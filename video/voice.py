# 產生教學影片的旁白,並依每句長度排出時間軸(tutorial-timing.js)
#   有 OPENROUTER_API_KEY → 用 OpenRouter 上的 Fish Audio(fish-audio/drama-3,可用 FISH_VOICE 指定聲音)
#   有 FISH_AUDIO_API_KEY + FISH_VOICE_ID → 直接用 Fish Audio
#   都沒有 → 先用 macOS 的「美佳」當暫時旁白
# 用法:source ~/.zshrc.local; python3 voice.py
import json, os, subprocess, urllib.request
from pathlib import Path

HERE = Path(__file__).parent
OUT = HERE / "assets" / "vo"
OUT.mkdir(parents=True, exist_ok=True)
LEAD, GAP, TAIL = 0.5, 0.7, 1.6   # 開頭空白、每句之間、最後一句後面

def fish(text: str, path: Path):
    body = json.dumps({"text": text, "reference_id": os.environ["FISH_VOICE_ID"], "format": "mp3", "normalize": True}).encode()
    req = urllib.request.Request("https://api.fish.audio/v1/tts", data=body, method="POST", headers={
        "Authorization": f"Bearer {os.environ['FISH_AUDIO_API_KEY']}", "Content-Type": "application/json",
        "model": os.environ.get("FISH_MODEL", "s1")})
    path.write_bytes(urllib.request.urlopen(req, timeout=120).read())

def openrouter(text: str, path: Path):
    body = {"model": os.environ.get("TTS_MODEL", "fish-audio/drama-3"), "input": text, "response_format": "mp3"}
    if os.environ.get("FISH_VOICE"): body["voice"] = os.environ["FISH_VOICE"]
    req = urllib.request.Request("https://openrouter.ai/api/v1/audio/speech", data=json.dumps(body).encode(), method="POST",
        headers={"Authorization": f"Bearer {os.environ['OPENROUTER_API_KEY']}", "Content-Type": "application/json"})
    path.write_bytes(urllib.request.urlopen(req, timeout=120).read())

def say(text: str, path: Path):
    aiff = path.with_suffix(".aiff")
    subprocess.run(["say", "-v", "Meijia", "-r", "200", "-o", str(aiff), text.replace("「", "").replace("」", "")], check=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(aiff), str(path)], check=True)
    aiff.unlink()

def duration(path: Path) -> float:
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)], capture_output=True, text=True)
    return float(r.stdout.strip())

if os.environ.get("FISH_AUDIO_API_KEY") and os.environ.get("FISH_VOICE_ID"):
    engine, tts = "fish", fish
elif os.environ.get("OPENROUTER_API_KEY"):
    engine, tts = "openrouter-fish", openrouter
else:
    engine, tts = "say", say
print("旁白:", engine)
lines = json.loads((HERE / "tutorial-script.json").read_text())
t, scenes = 0.0, []
for i, line in enumerate(lines):
    path = OUT / f"{i:02d}-{line['id']}.mp3"
    # 「」之類的引號會讓 TTS 唸法怪怪的,唸的時候拿掉(字幕照原文)
    tts(line["text"].replace("「", "").replace("」", ""), path)
    d = duration(path)
    start = t
    vo = start + (LEAD if i == 0 else 0.25)
    end = vo + d + (TAIL if i == len(lines) - 1 else GAP)
    scenes.append({"id": line["id"], "text": line["text"], "start": round(start, 2), "end": round(end, 2),
                   "vo": round(vo, 2), "voDur": round(d, 2), "audio": f"assets/vo/{path.name}"})
    t = end
    print(f"  {line['id']:6s} {d:5.2f}s  {start:6.2f} → {end:6.2f}")
(HERE / "tutorial-timing.js").write_text("// voice.py 產生的時間軸,不要手改\nwindow.TIMING = " +
    json.dumps({"voice": engine, "duration": round(t, 2), "scenes": scenes}, ensure_ascii=False, indent=1) + "\n")
print(f"總長 {t:.1f} 秒 → tutorial-timing.js")
