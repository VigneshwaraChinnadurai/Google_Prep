import os
from fastapi import Header, HTTPException, status


def require_api_key(x_api_key: str | None = Header(default=None)) -> None:
    expected = os.environ.get("API_KEY")
    if not expected:
        raise HTTPException(500, "Server missing API_KEY")
    if x_api_key != expected:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid API key")
