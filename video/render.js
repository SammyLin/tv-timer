// 把 intro.html / tutorial.html 一格一格截圖,再用 ffmpeg 接成 1080×1920 的 MP4
// 用法:node render.js intro [fps]      → out/intro.mp4
//      node render.js intro --stills 1,5,9  → out/intro-1.png …(先看版面)
const path = require("path"), fs = require("fs"), { execFileSync } = require("child_process")
const { chromium } = require(process.env.PLAYWRIGHT_CORE || "playwright-core")

const name = process.argv[2]
const stillsArg = process.argv.indexOf("--stills")
const fps = Number(process.argv[3]) || 30
const dir = __dirname
const out = path.join(dir, "out"); fs.mkdirSync(out, { recursive: true })

;(async () => {
  const b = await chromium.launch({ executablePath: process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" })
  const p = await b.newPage({ viewport: { width: 540, height: 960 }, deviceScaleFactor: 2 })
  await p.goto("file://" + path.join(dir, `${name}.html`))
  await p.evaluate(() => document.fonts.ready)
  await p.waitForTimeout(500)
  if (stillsArg > 0) {
    for (const t of process.argv[stillsArg + 1].split(",").map(Number)) {
      await p.evaluate((t) => window.seek(t), t)
      await p.screenshot({ path: path.join(out, `${name}-${t}.png`) })
    }
  } else {
    const dur = await p.evaluate(() => window.DURATION)
    const frames = path.join(dir, "frames", name)
    fs.rmSync(frames, { recursive: true, force: true }); fs.mkdirSync(frames, { recursive: true })
    const n = Math.ceil(dur * fps)
    for (let i = 0; i < n; i++) {
      await p.evaluate((t) => window.seek(t), i / fps)
      await p.screenshot({ path: path.join(frames, `${String(i).padStart(5, "0")}.jpg`), type: "jpeg", quality: 92 })
      if (i % fps === 0) process.stdout.write(`\r${name}: ${Math.round(i / fps)}/${Math.round(dur)} 秒`)
    }
    console.log()
    execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-framerate", String(fps), "-i", path.join(frames, "%05d.jpg"),
      "-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo", "-shortest",
      "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18", "-preset", "slow", "-c:a", "aac", "-movflags", "+faststart",
      path.join(out, `${name}.mp4`)])
    fs.rmSync(frames, { recursive: true, force: true })
    console.log("→", path.join(out, `${name}.mp4`))
  }
  await b.close()
})()
