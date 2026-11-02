#!/usr/bin/env python3
"""插圖:gen.py NAME ASPECT SIZE PROMPT_FILE [REF ...] → raw/NAME-<n>.png
用 OpenRouter 的圖像模型(預設 google/gemini-nano-banana-2.1,跟米粒學堂同一套做法),參考圖抓風格。花費記在 raw/costs.jsonl"""
import sys, os, json, base64, urllib.request, glob, time
from pathlib import Path
name, aspect, size, pfile, *refs = sys.argv[1:]
MODEL = os.environ.get("MODEL", "google/gemini-nano-banana-2.1")
here = Path(__file__).parent
prompt = (here / "prompts" / "_tvbot_base.txt").read_text() + "\n\n" + Path(pfile).read_text() if name.startswith("tvbot") else Path(pfile).read_text()
content = [{"type": "text", "text": prompt}]
for r in refs:
    content.append({"type": "image_url", "image_url": {"url": "data:image/png;base64," + base64.b64encode(open(r, "rb").read()).decode()}})
body = {"model": MODEL, "messages": [{"role": "user", "content": content}], "modalities": ["image", "text"],
        "usage": {"include": True}, "image_config": {"aspect_ratio": aspect, "image_size": size}}
req = urllib.request.Request("https://openrouter.ai/api/v1/chat/completions", data=json.dumps(body).encode(),
      headers={"Authorization": "Bearer " + os.environ["OPENROUTER_API_KEY"], "Content-Type": "application/json"})
t = time.time()
resp = json.load(urllib.request.urlopen(req, timeout=600))
msg = resp["choices"][0]["message"]
n = len(glob.glob(str(here / "raw" / f"{name}-*.png"))) + 1
out = None
for im in (msg.get("images") or [])[:1]:
    out = here / "raw" / f"{name}-{n}.png"
    out.write_bytes(base64.b64decode(im["image_url"]["url"].split(",", 1)[1]))
with open(here / "raw" / "costs.jsonl", "a") as f:
    f.write(json.dumps({"name": name, "file": str(out), "model": MODEL, "cost": resp.get("usage", {}).get("cost"), "secs": round(time.time() - t)}) + "\n")
print(name, out, resp.get("usage", {}).get("cost"), (msg.get("content") or "")[:120])
