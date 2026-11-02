# 介紹與教學短影片

直式 1080×1920(YouTube Shorts / Reels / TikTok)。畫面是網頁動畫,`render.js` 一格一格截圖再用 ffmpeg 接成 MP4。

| 檔案 | 內容 | 長度 |
|---|---|---|
| `intro.html` | 介紹:選人選時間、倒數與時間到(有時間到音效)、爸媽手機、不限制要同意、每個小孩分開設、電視配色、觀看分析 | 48 秒 |
| `tutorial.html` | 安裝教學:開發人員選項、USB 偵錯、電腦貼一行、按允許、網址和密碼、之後怎麼辦 | 52 秒 |

```sh
npm i playwright-core            # 用本機的 Chrome,不用另外下載瀏覽器
node render.js intro             # → out/intro.mp4
node render.js tutorial --stills 5,20,40   # 先看某幾秒的畫面 → out/tutorial-5.png …
```

需要 ffmpeg 和 Google Chrome(路徑可用 `CHROME=` 指定)。時間到的音效 `assets/chime.wav` 由 `python3 chime.py assets/chime.wav` 產生(跟電視上 Chime.kt 同一段旋律);頁面裡 `window.AUDIO` 指定什麼時候播。沒有背景音樂,上傳後可以在平台上加。
