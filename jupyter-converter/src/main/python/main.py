import logging
from contextlib import asynccontextmanager
from contextvars import ContextVar

import uvicorn
from fastapi import FastAPI, HTTPException, UploadFile, Response
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import HTMLResponse
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


@asynccontextmanager
async def lifespan(app: FastAPI):
    instrumentator.expose(app)
    yield


app = FastAPI(lifespan=lifespan)
app.add_middleware(TraceContextMiddleware)
instrumentator.instrument(app)


@app.get("/ping")
async def ping():
    return Response(status_code=200)


@app.post("/convert")
async def convert(file: UploadFile):
    if not file:
        raise HTTPException(status_code=400, detail="No file submitted")
    logger.info("Converting notebook %s", file.filename)
    try:
        html_exporter = HTMLExporter()
        (body, resources) = html_exporter.from_file(file.file)
    except Exception as exc:
        logger.error("Could not convert notebook %s: %s", file.filename, exc)
        raise HTTPException(
            status_code=400,
            detail=f"Could not convert notebook: {exc}",
        )
    logger.info("Converted notebook %s", file.filename)
    return HTMLResponse(content=body)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0")
