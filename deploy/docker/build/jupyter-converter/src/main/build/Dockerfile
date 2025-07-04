FROM dockerio.mirror.docker.edu-sharing.com/python:3.13-bullseye AS builder

RUN pip install poetry==2.1.2


ENV POETRY_NO_INTERACTION=1 \
    POETRY_VIRTUALENVS_IN_PROJECT=1 \
    POETRY_VIRTUALENVS_CREATE=1 \
    POETRY_CACHE_DIR=/tmp/poetry_cache

WORKDIR /app

COPY artifacts/pyproject.toml artifacts/poetry.lock ./
RUN touch README.md

RUN poetry install --only main --no-root
RUN rm -rf $POETRY_CACHE_DIR


FROM dockerio.mirror.docker.edu-sharing.com/python:3.13-slim-bullseye

ARG git_branch=dev
ARG git_commit_id=dev
ARG project_artifactId=edu_sharing-community-services-rendering2-deploy-docker-build-jupyter-converter
ARG project_groupId=org.edu_sharing
ARG project_version=dev
ARG virtualEnvPath=/app/.venv

ENV VIRTUAL_ENV=${virtualEnvPath} \
    PATH="/app/.venv/bin:$PATH"

WORKDIR app

RUN set -eux \
    && adduser --uid=1000 --home=/application --disabled-password --gecos "" --shell=/bin/bash worker \
    && chown -RL worker:worker . \
    && chown -RL worker:worker /tmp

USER worker
COPY --from=builder ${virtualEnvPath} ${virtualEnvPath}

COPY artifacts/main.py ./main.py

ENTRYPOINT ["python", "-m", "main"]

LABEL git.branch=${git_branch} \
git.commit.id=${git_commit_id} \
mvn.project.artifactId=${project_artifactId} \
mvn.project.groupId=${project_groupId} \
mvn.project.version=${project_version}
