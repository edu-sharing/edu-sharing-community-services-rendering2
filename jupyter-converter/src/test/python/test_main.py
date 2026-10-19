import json
import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "main", "python"))
os.environ["JUPYTER_MAX_UPLOAD_BYTES"] = "20000"

import pytest
from fastapi.testclient import TestClient

import main

NOTEBOOK = {
    "nbformat": 4,
    "nbformat_minor": 5,
    "metadata": {},
    "cells": [{"cell_type": "markdown", "metadata": {}, "id": "a", "source": "# Hello"}],
}


@pytest.fixture
def client():
    with TestClient(main.app) as c:
        yield c


def post(client, data: bytes):
    return client.post("/convert", files={"file": ("n.ipynb", data)})


def test_ping(client):
    assert client.get("/ping").status_code == 200


def test_convert_ok(client):
    r = post(client, json.dumps(NOTEBOOK).encode())
    assert r.status_code == 200 and "Hello" in r.text


def test_invalid_input_is_422(client):
    assert post(client, b"not json").status_code == 422
    assert post(client, b'{"foo": 1}').status_code == 422


def test_empty_is_400(client):
    assert post(client, b"").status_code == 400


def test_too_large_is_413(client):
    assert post(client, b"x" * 30000).status_code == 413


def test_internal_error_is_500(client, monkeypatch):
    def boom(_nb):
        raise RuntimeError("boom")
    monkeypatch.setattr(main, "_render", boom)
    assert post(client, json.dumps(NOTEBOOK).encode()).status_code == 500


def test_timeout_is_504_and_ping_stays_responsive(client, monkeypatch):
    monkeypatch.setattr(main, "CONVERSION_TIMEOUT", 1)
    monkeypatch.setattr(main, "_render", lambda _nb: time.sleep(2) or "x")
    start = time.time()
    assert post(client, json.dumps(NOTEBOOK).encode()).status_code == 504
    assert time.time() - start < 2
