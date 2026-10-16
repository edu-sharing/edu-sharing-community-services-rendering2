import asyncio
import json
import logging
import os
from contextlib import asynccontextmanager
from contextvars import ContextVar

import nbformat
import uvicorn
from fastapi import FastAPI, HTTPException, UploadFile, Response
from starlette.concurrency import run_in_threadpool
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import HTMLResponse, JSONResponse
from nbconvert import HTMLExporter
from prometheus_fastapi_instrumentator import Instrumentator

instrumentator = Instrumentator()

# Request-scoped trace ids, set by TraceContextMiddleware and rendered into every log line by
# trace_id_filter below (contextvars, not a plain global, so concurrent requests don't clobber
# each other's ids).
_trace_id: ContextVar[str] = ContextVar("trace_id", default="-")
_client_trace_id: ContextVar[str] = ContextVar("client_trace_id", default="-")


def _extract_trace_id(headers) -> str:
    """
    Mirrors the rendering service's/Lumi's b3 extraction (see WebClientConfig.kt / traceContext.ts):
    the single `b3` header (`traceId-spanId-...`), then the multi-header `x-b3-traceid`, then the W3C
    `traceparent` (`00-traceId-spanId-flags`).
    """
    b3 = headers.get("b3")
    if b3:
        trace_id = b3.split("-")[0]
        if trace_id:
            return trace_id
    x_b3_trace_id = headers.get("x-b3-traceid")
    if x_b3_trace_id:
        return x_b3_trace_id
    traceparent = headers.get("traceparent")
    if traceparent:
        parts = traceparent.split("-")
        if len(parts) >= 2 and parts[1]:
            return parts[1]
    return "-"


class TraceContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        trace_token = _trace_id.set(_extract_trace_id(request.headers))
        # X-Client-Trace-Id is a client-supplied correlation id, distinct from the b3 trace id above;
        # the rendering service forwards it unchanged (see its application.properties baggage config).
        client_trace_token = _client_trace_id.set(request.headers.get("x-client-trace-id", "-"))
        try:
            return await call_next(request)
        finally:
            _trace_id.reset(trace_token)
            _client_trace_id.reset(client_trace_token)


class TraceIdLogFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.trace_id = _trace_id.get()
        record.client_trace_id = _client_trace_id.get()
        return True


logger = logging.getLogger("jupyter-converter")
logger.setLevel(logging.INFO)
_handler = logging.StreamHandler()
_handler.setFormatter(
    logging.Formatter(
        "%(asctime)s %(levelname)s [traceId=%(trace_id)s clientTraceId=%(client_trace_id)s] %(name)s: %(message)s"
    )
)
_handler.addFilter(TraceIdLogFilter())
logger.addHandler(_handler)
logger.propagate = False



def _env_int(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, default))
    except ValueError:
        logger.warning("Ignoring non-numeric %s", name)
        return default


# Upload size limit (bytes), enforced via Content-Length and while the body streams in -> 413.
MAX_UPLOAD_BYTES = _env_int("JUPYTER_MAX_UPLOAD_BYTES", 50 * 1024 * 1024)
# Wall-clock budget per conversion (seconds) -> 504. Keep it below the caller's client timeout.
CONVERSION_TIMEOUT = _env_int("JUPYTER_CONVERSION_TIMEOUT", 300)
# Max conversions running at once; a request that cannot get a slot within SLOT_WAIT seconds gets 503.
MAX_CONCURRENT_CONVERSIONS = max(1, _env_int("JUPYTER_MAX_CONCURRENT_CONVERSIONS", 4))
SLOT_WAIT = _env_int("JUPYTER_SLOT_WAIT_TIMEOUT", 30)


class _BodyTooLarge(Exception):
    pass


class BodySizeLimitMiddleware:
    """
    Pure ASGI middleware: rejects oversized uploads with 413 before the multipart parser spools them to disk
    (Content-Length check), and also while streaming for requests without/with a lying Content-Length.
    """

    def __init__(self, app, max_bytes: int):
        self.app = app
        self.max_bytes = max_bytes

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)
        headers = dict(scope["headers"])
        declared = headers.get(b"content-length")
        if declared is not None and declared.isdigit() and int(declared) > self.max_bytes:
            return await self._reject(scope, receive, send)

        received = 0
        started = False

        async def limited_receive():
            nonlocal received
            message = await receive()
            if message["type"] == "http.request":
                received += len(message.get("body", b""))
                if received > self.max_bytes:
                    raise _BodyTooLarge()
            return message

        async def tracking_send(message):
            nonlocal started
            if message["type"] == "http.response.start":
                started = True
            await send(message)

        try:
            await self.app(scope, limited_receive, tracking_send)
        except _BodyTooLarge:
            if started:
                raise
            await self._reject(scope, receive, send)

    async def _reject(self, scope, receive, send):
        response = JSONResponse(
            status_code=413,
            content={"detail": f"Upload exceeds the limit of {self.max_bytes} bytes"},
        )
        await response(scope, receive, send)


_slots = None  # created in lifespan (needs the running event loop)


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _slots
    _slots = asyncio.Semaphore(MAX_CONCURRENT_CONVERSIONS)
    instrumentator.expose(app)
    yield


app = FastAPI(lifespan=lifespan)
app.add_middleware(TraceContextMiddleware)
app.add_middleware(BodySizeLimitMiddleware, max_bytes=MAX_UPLOAD_BYTES)
instrumentator.instrument(app)


@app.get("/ping")
async def ping():
    return Response(status_code=200)


def _parse_notebook(raw: bytes):
    """Input stage: anything failing here is the caller's fault (-> 4xx)."""
    try:
        return nbformat.reads(raw.decode("utf-8"), as_version=4)
    except (UnicodeDecodeError, json.JSONDecodeError, nbformat.reader.NotJSONError, nbformat.ValidationError) as exc:
        raise HTTPException(status_code=422, detail=f"Not a valid notebook: {exc}")
    except (KeyError, ValueError, TypeError, AttributeError) as exc:
        raise HTTPException(status_code=422, detail=f"Malformed notebook: {exc}")


def _render(notebook) -> str:
    """Conversion stage: failures here are internal (-> 5xx). Runs in a worker thread."""
    body, _resources = HTMLExporter().from_notebook_node(notebook)
    return body


@app.post("/convert")
async def convert(file: UploadFile):
    if not file:
        raise HTTPException(status_code=400, detail="No file submitted")
    try:
        # Bounded by BodySizeLimitMiddleware; the spooled temp file is closed in the finally below.
        raw = await file.read()
    finally:
        await file.close()
    if not raw:
        raise HTTPException(status_code=400, detail="Empty file submitted")
    logger.info("Converting notebook %s (%d bytes)", file.filename, len(raw))
    notebook = _parse_notebook(raw)

    try:
        await asyncio.wait_for(_slots.acquire(), timeout=SLOT_WAIT)
    except asyncio.TimeoutError:
        raise HTTPException(status_code=503, detail="Converter busy, retry later", headers={"Retry-After": "5"})
    # The slot is released when the worker thread really finishes (a timed-out thread cannot be killed),
    # so hung conversions can never pile up beyond MAX_CONCURRENT_CONVERSIONS.
    future = asyncio.ensure_future(run_in_threadpool(_render, notebook))
    future.add_done_callback(lambda _f: _slots.release())
    try:
        body = await asyncio.wait_for(asyncio.shield(future), timeout=CONVERSION_TIMEOUT)
    except asyncio.TimeoutError:
        future.add_done_callback(lambda f: f.cancelled() or f.exception())  # mark the late result as retrieved
        logger.error("Conversion of %s timed out after %ss", file.filename, CONVERSION_TIMEOUT)
        raise HTTPException(status_code=504, detail=f"Conversion timed out after {CONVERSION_TIMEOUT}s")
    except MemoryError:
        logger.error("Out of memory converting %s", file.filename)
        raise HTTPException(status_code=503, detail="Converter out of memory, retry later")
    except Exception as exc:
        logger.exception("Could not convert notebook %s", file.filename)
        raise HTTPException(status_code=500, detail=f"Could not convert notebook: {exc}")
    logger.info("Converted notebook %s", file.filename)
    return HTMLResponse(content=body)


if __name__ == "__main__":
    workers = max(1, _env_int("JUPYTER_UVICORN_WORKERS", 1))
    limit_concurrency = _env_int("JUPYTER_UVICORN_LIMIT_CONCURRENCY", 0) or None
    # With several workers the import string is required; note that /metrics is then per worker
    # (prometheus multiprocess mode is not set up) - scale via pods instead where possible.
    uvicorn.run(
        "main:app" if workers > 1 else app,
        host=os.environ.get("JUPYTER_HOST", "0.0.0.0"),
        port=_env_int("JUPYTER_PORT", 8000),
        workers=workers,
        limit_concurrency=limit_concurrency,
        timeout_keep_alive=_env_int("JUPYTER_UVICORN_TIMEOUT_KEEP_ALIVE", 5),
    )
