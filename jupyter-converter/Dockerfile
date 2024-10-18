FROM python:3.13-alpine
LABEL authors="metaventis"

RUN pip install poetry==1.8.3

COPY . .

RUN poetry install

ENTRYPOINT ["poetry", "run", "python", "-m", "main"]