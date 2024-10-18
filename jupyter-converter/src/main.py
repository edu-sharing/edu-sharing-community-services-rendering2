import uvicorn
from fastapi import FastAPI, UploadFile, HTTPException, Response
from starlette.responses import HTMLResponse

from src.service.conversion_service import convert_ipynb_to_html

app = FastAPI()

@app.post("/convert")
async def convert(file: UploadFile):
    if file:
        (body, resources) = convert_ipynb_to_html(file=file)
        return HTMLResponse(content=body)
    else:
        raise HTTPException(status_code=400, detail="No file submitted")

if __name__ == "__main__":
    uvicorn.run(app, port=8907)
