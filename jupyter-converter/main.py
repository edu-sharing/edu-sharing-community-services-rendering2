import uvicorn
from fastapi import FastAPI, UploadFile, HTTPException
from starlette.responses import HTMLResponse
from nbconvert import HTMLExporter

app = FastAPI()

@app.post("/convert")
async def convert(file: UploadFile):
    if file:
        html_exporter: HTMLExporter = HTMLExporter()
        (body, resources) = html_exporter.from_file(file.file)
        return HTMLResponse(content=body)
    else:
        raise HTTPException(status_code=400, detail="No file submitted")

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0")
