import uvicorn
from fastapi import FastAPI, UploadFile, HTTPException, Response
from starlette.responses import FileResponse

from src.main.python.service.conversion_service import convert_ipynb_to_html

app = FastAPI()

@app.post("/convert")
async def convert(file: UploadFile):
    if file:
        (body, resources) = convert_ipynb_to_html(file=file)
        file_name: str = "notebook.html"
        headers: dict = {
            "Content-Disposition": f"attachment; filename={file_name}"
        }
        with open(file_name, 'w') as output_file:
            output_file.write(body)
            return FileResponse(file_name)
    else:
        raise HTTPException(status_code=400, detail="No file submitted")

if __name__ == "__main__":
    uvicorn.run(app, port=8907)
