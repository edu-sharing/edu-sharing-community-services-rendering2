import typing as t

from fastapi import UploadFile
from nbconvert import HTMLExporter


def convert_ipynb_to_html(file: UploadFile) -> tuple[str, dict[str, t.Any]]:
    html_exporter: HTMLExporter = HTMLExporter()
    return html_exporter.from_file(file.file)
