#!/usr/bin/env python3
"""Start the AI Testing Workbench on loopback only (mock mode)."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
BACKEND = REPO / "backend"

for path in (str(ROOT), str(BACKEND)):
    if path not in sys.path:
        sys.path.insert(0, path)

import uvicorn


def main() -> None:
    uvicorn.run(
        "workbench.app:app",
        host="127.0.0.1",
        port=8765,
        reload=False,
        factory=False,
    )


if __name__ == "__main__":
    main()
