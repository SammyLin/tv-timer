# 產生跟電視上 Chime.kt 一樣的「時間到」木琴旋律(10 秒),給影片用
# 用法:python3 chime.py assets/chime.wav
import sys, wave
import numpy as np

RATE, SECONDS, BEAT = 44100, 10.0, 0.16
# 相對 C5 的半音數,None 是休止;跟 Chime.kt 的 melody 一樣
MELODY = [(4, 1), (7, 1), (12, 1), (None, 1), (9, 1), (7, 1), (4, 2),
          (2, 1), (4, 1), (7, 1), (None, 1), (9, 1), (12, 1), (14, 3), (None, 2)]

n = int(RATE * SECONDS)
out = np.zeros(n)
t0 = 0.0
while True:
    stop = False
    for note, beats in MELODY:
        if t0 >= SECONDS - 1.2:
            stop = True
            break
        if note is not None:
            f = 523.25 * 2 ** (note / 12)
            s = int(t0 * RATE)
            L = min(int(1.5 * RATE), n - s)
            t = np.arange(L) / RATE
            out[s:s + L] += np.minimum(1, t / 0.006) * (
                np.exp(-t * 5) * np.sin(2 * np.pi * f * t)
                + 0.18 * np.exp(-t * 14) * np.sin(2 * np.pi * f * 3.93 * t)
                + 0.05 * np.exp(-t * 30) * np.sin(2 * np.pi * f * 9.2 * t))
        t0 += beats * BEAT
    if stop:
        break
fade = np.ones(n); fade[-RATE:] = np.linspace(1, 0, RATE)
pcm = (out / np.abs(out).max() * fade * 0.7 * 32767).astype("<i2")
with wave.open(sys.argv[1], "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE); w.writeframes(pcm.tobytes())
