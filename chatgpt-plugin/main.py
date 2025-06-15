import os
import yaml
from fastapi import FastAPI

app = FastAPI()

CHAT_FLAGS_PATH = os.path.join("plugins", "OpenCore", "chat_flags.yml")


def load_flags():
    if not os.path.exists(CHAT_FLAGS_PATH):
        return []
    with open(CHAT_FLAGS_PATH, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f) or {}
    result = []
    for code, cfg in data.items():
        if not isinstance(cfg, dict):
            continue
        if not cfg.get("active", True):
            continue
        result.append({
            "code": code,
            "description": cfg.get("description", ""),
            "min_change": cfg.get("min_change", 0),
            "max_change": cfg.get("max_change", 0),
        })
    return result


@app.get("/flags")
def get_flags():
    return {"flags": load_flags()}
