// 短影片的動畫引擎:畫面完全由時間 t 決定(seek(t)),render.js 一格一格截圖再用 ffmpeg 接起來。
//
//  <section data-scene="開始秒,結束秒">        整段淡入淡出
//    <div data-in="延遲秒,長度秒,種類">          進場:up / down / left / right / pop / fade / zoom / blur
//    <div data-out="延遲秒,長度秒">             (相對場景開始)淡出
//    <span data-type="延遲秒,每秒幾字">文字</span> 打字效果
//    <div data-fn="函式名">                     自訂:window[函式名](el, 場景內秒數)
//  </section>

(() => {
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x))
const easeOut = (x) => 1 - Math.pow(1 - x, 3)
const easeBack = (x) => { const c = 1.7; return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2) }
window.ease = { clamp, easeOut, easeBack }

const FADE = 0.35

window.seek = (t) => {
  for (const sc of document.querySelectorAll("[data-scene]")) {
    const [s, e] = sc.dataset.scene.split(",").map(Number)
    const lt = t - s
    const o = t < s || t > e ? 0 : clamp(Math.min(lt / FADE, (e - t) / FADE))
    // 第一段不淡入
    sc.style.opacity = s === 0 ? (t > e ? 0 : clamp((e - t) / FADE)) : o
    sc.style.visibility = sc.style.opacity > 0 ? "visible" : "hidden"
    if (sc.style.visibility === "hidden") continue

    for (const el of sc.querySelectorAll("[data-in]")) {
      const [d, du = 0.6, type = "up"] = el.dataset.in.split(",")
      const raw = clamp((lt - Number(d)) / Number(du))
      const p = easeOut(raw)
      let tf = ""
      if (type === "up") tf = `translateY(${(1 - p) * 60}px)`
      if (type === "down") tf = `translateY(${-(1 - p) * 60}px)`
      if (type === "left") tf = `translateX(${(1 - p) * 120}px)`
      if (type === "right") tf = `translateX(${-(1 - p) * 120}px)`
      if (type === "pop") tf = `scale(${0.4 + 0.6 * easeBack(raw)})`
      if (type === "zoom") tf = `scale(${1.25 - 0.25 * p})`
      if (type === "blur") tf = `translateY(${(1 - p) * 24}px) scale(${1.06 - 0.06 * p})`
      el.style.filter = type === "blur" && raw < 1 ? `blur(${(1 - p) * 14}px)` : ""
      el.style.transform = tf
      el.style.opacity = raw
    }
    for (const el of sc.querySelectorAll("[data-out]")) {
      const [d, du = 0.4] = el.dataset.out.split(",").map(Number)
      el.style.opacity = Math.min(Number(el.style.opacity || 1), 1 - clamp((lt - d) / du))
    }
    for (const el of sc.querySelectorAll("[data-type]")) {
      const [d, cps] = el.dataset.type.split(",").map(Number)
      el.dataset.full ??= el.textContent
      const n = Math.floor((lt - d) * cps)
      const full = el.dataset.full
      el.textContent = full.slice(0, clamp(n, 0, full.length))
      el.classList.toggle("typing", n >= 0 && n < full.length + 6)
    }
    for (const el of sc.querySelectorAll("[data-fn]")) window[el.dataset.fn]?.(el, lt)
  }
  const bar = document.querySelector("#progress")
  if (bar) bar.style.transform = `scaleX(${clamp(t / window.DURATION)})`
}
})()
