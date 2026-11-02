# 用 ElevenLabs 生成背景音樂(長度跟著 tutorial-timing.js)
# 用法:source ~/.zshrc.local; python3 music.py
import json, os, re, urllib.request
from pathlib import Path

HERE = Path(__file__).parent
timing = json.loads(re.search(r"window\.TIMING = (\{.*\})", (HERE / "tutorial-timing.js").read_text(), re.S).group(1))
ms = int((timing["duration"] + 1) * 1000)
prompt = ("Upbeat, modern electronic background track for a short tech tutorial video. Bright synth plucks, soft punchy drums, "
          "light bass, positive and playful, steady 110 BPM, no vocals, clean mix that stays out of the way of a voiceover, "
          "gentle intro and a clear ending.")
req = urllib.request.Request("https://api.elevenlabs.io/v1/music", method="POST",
    data=json.dumps({"prompt": prompt, "music_length_ms": ms}).encode(),
    headers={"xi-api-key": os.environ["ELEVENLABS_API_KEY"], "Content-Type": "application/json", "Accept": "audio/mpeg"})
out = HERE / "assets" / "music.mp3"
out.write_bytes(urllib.request.urlopen(req, timeout=300).read())
print(f"{out}  {out.stat().st_size // 1024} KB, {ms / 1000:.1f} 秒")
