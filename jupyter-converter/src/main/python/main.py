from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI, HTTPException, UploadFile, Response
from starlette.responses import HTMLResponse
from nbconvert import HTMLExporter
from prometheus_fastapi_instrumentator import Instrumentator

instrumentator = Instrumentator()


@asynccontextmanager
async def lifespan(app: FastAPI):
    instrumentator.expose(app)
    yield


app = FastAPI(lifespan=lifespan)
instrumentator.instrument(app)


@app.get("/ping")
async def ping():
    return Response(status_code=200)


@app.post("/convert")
async def convert(file: UploadFile):
    if not file:
        raise HTTPException(status_code=400, detail="No file submitted")
    try:
        html_exporter = HTMLExporter()
        (body, resources) = html_exporter.from_file(file.file)
    except Exception as exc:
        raise HTTPException(
            status_code=400,
            detail=f"Could not convert notebook: {exc}",
        )
    return HTMLResponse(content=body)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0")
