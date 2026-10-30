// 看電視計時:讓 Chrome 認得這是可以安裝的 App。
// 畫面檔案先用網路、沒網路才用快取;/api 一律走網路(狀態要即時)。
const CACHE = "tvtimer-v1"
self.addEventListener("install", (e) => { self.skipWaiting() })
self.addEventListener("activate", (e) => { e.waitUntil(self.clients.claim()) })
self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url)
  if (e.request.method !== "GET" || url.pathname.startsWith("/api/")) return
  e.respondWith(
    fetch(e.request)
      .then((r) => { const copy = r.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); return r })
      .catch(() => caches.match(e.request).then((r) => r || caches.match("/")))
  )
})
