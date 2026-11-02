# 介紹與教學短影片

直式 1080×1920(YouTube Shorts / Reels / TikTok)。畫面是網頁動畫,`render.js` 一格一格截圖再用 ffmpeg 接成 MP4。

| 檔案 | 內容 | 長度 |
|---|---|---|
| `intro.html` | 介紹:選人選時間、倒數與時間到(有時間到音效)、爸媽手機、不限制要同意、每個小孩分開設、電視配色、觀看分析 | 48 秒 |
| `tutorial.html` | 安裝教學(米粒學堂風格:白卡片、薄荷綠、jf 粉圓、3D 按鈕、吉祥物「小電」;旁白、背景音樂、跟著旁白亮的字幕):開發人員選項、USB 偵錯、電腦貼一行、按允許、網址和密碼、之後怎麼辦 | 約 64 秒 |

教學影片的旁白和音樂:

```sh
source ~/.zshrc.local
python3 voice.py   # 旁白:tutorial-script.json → assets/vo/*.mp3 + tutorial-timing.js(每一幕的長度照旁白)
                   #   OpenRouter 的 fish-audio/drama-3(OPENROUTER_API_KEY,可用 FISH_VOICE 換聲音);沒金鑰就用 macOS 美佳
python3 music.py   # 背景音樂:ElevenLabs Music(ELEVENLABS_API_KEY),長度跟著時間軸
node render.js tutorial
```

```sh
npm i playwright-core            # 用本機的 Chrome,不用另外下載瀏覽器
node render.js intro             # → out/intro.mp4
node render.js tutorial --stills 5,20,40   # 先看某幾秒的畫面 → out/tutorial-5.png …
```

需要 ffmpeg 和 Google Chrome(路徑可用 `CHROME=` 指定)。時間到的音效 `assets/chime.wav` 由 `python3 chime.py assets/chime.wav` 產生(跟電視上 Chime.kt 同一段旋律);頁面裡 `window.AUDIO` 指定什麼時候播。沒有背景音樂,上傳後可以在平台上加。

插圖(吉祥物「小電」和客廳背景)用 OpenRouter 的圖像模型產生,做法跟米粒學堂一樣(米米當風格參考、綠幕再去背):

```sh
cd art
python3 gen.py tvbot-wave 1:1 1K prompts/tvbot-wave.txt <風格參考圖...>   # → raw/(不進 git)
python3 key.py raw/tvbot-wave-1.png ../assets/art/tvbot-wave.png 30      # 去綠幕
```

字型 `assets/fonts/jf-openhuninn.ttf` 是 [jf open 粉圓](https://github.com/justfont/open-huninn-font)(SIL Open Font License)。
