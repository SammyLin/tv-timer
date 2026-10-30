package ai.threemi.tvtimer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/**
 * 爸媽手機用的網頁:http://電視IP:8765/
 *  - 畫面是 web/(React + shadcn/ui)打包到 assets/web 的檔案,這裡直接送出去
 *  - GET  /api/state  目前狀態(不用密碼,只有看)
 *  - POST /api/do     {pin, action, ...} 同意不限制、加時間、停止、改設定;回 {ok, message, state}
 * 只在家裡網路上,密碼連錯 5 次鎖 10 分鐘(避免小孩用手機亂猜)。
 */
class ParentServer(private val context: Context, private val port: Int, private val testChime: () -> Unit) {
  private var socket: ServerSocket? = null
  private val main = Handler(Looper.getMainLooper())
  private var wrong = 0
  private var lockedUntil = 0L

  fun start() {
    thread(name = "parent-server", isDaemon = true) {
      runCatching {
        val ss = ServerSocket(port).also { socket = it }
        while (!ss.isClosed) {
          val c = ss.accept()
          thread(isDaemon = true) { runCatching { c.use { handle(it) } } }
        }
      }
    }
  }

  fun stop() = runCatching { socket?.close() }

  // ---------------- HTTP ----------------

  private fun handle(c: Socket) {
    c.soTimeout = 10_000
    val input = c.getInputStream()
    val head = readHead(input) ?: return
    val lines = head.split("\r\n")
    val (method, target) = lines.first().split(" ").let { it.getOrNull(0).orEmpty() to it.getOrNull(1).orEmpty() }
    val length = lines.drop(1).firstOrNull { it.lowercase().startsWith("content-length:") }
      ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
    // 內文照位元組讀(中文名字是多位元組,不能用字元數算)
    val body = if (length in 1..20_000) readBytes(input, length).toString(Charsets.UTF_8) else ""
    val path = target.substringBefore("?")

    val out = c.getOutputStream()
    fun send(status: String, type: String, bytes: ByteArray, cache: String = "no-store") {
      out.write("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: $cache\r\nConnection: close\r\n\r\n".toByteArray())
      out.write(bytes)
      out.flush()
    }
    fun json(o: JSONObject) = send("200 OK", "application/json; charset=utf-8", o.toString().toByteArray())

    when {
      path == "/api/state" -> json(stateJson())
      path == "/api/do" && method == "POST" -> {
        val req = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
        val (ok, msg) = act(req)
        json(JSONObject().put("ok", ok).put("message", msg).put("state", stateJson()))
      }
      else -> {
        // 打包好的網頁;找不到的路徑都給 index.html
        val file = path.removePrefix("/").ifEmpty { "index.html" }
        val found = asset(file)
        when {
          // assets/ 底下的檔名帶雜湊,可以一直快取;其他(sw.js、manifest、圖示)每次都重抓
          found != null && file.startsWith("assets/") -> send("200 OK", mime(file), found, "public, max-age=31536000, immutable")
          found != null -> send("200 OK", mime(file), found)
          else -> asset("index.html")?.let { send("200 OK", "text/html; charset=utf-8", it) }
            ?: send("404 Not Found", "text/plain", "web not built".toByteArray())
        }
      }
    }
  }

  private fun readHead(input: InputStream): String? {
    val buf = ByteArrayOutputStream()
    var last4 = 0
    while (buf.size() < 16_384) {
      val b = input.read()
      if (b < 0) return null
      buf.write(b)
      last4 = (last4 shl 8) or b
      if (last4 == 0x0D0A0D0A) return buf.toString(Charsets.ISO_8859_1.name()).trimEnd()
    }
    return null
  }

  private fun readBytes(input: InputStream, n: Int): ByteArray {
    val buf = ByteArray(n)
    var got = 0
    while (got < n) { val k = input.read(buf, got, n - got); if (k < 0) break; got += k }
    return buf.copyOf(got)
  }

  private fun asset(name: String): ByteArray? =
    if (name.contains("..")) null else runCatching { context.assets.open("web/$name").use { it.readBytes() } }.getOrNull()

  private fun mime(name: String) = when (name.substringAfterLast('.')) {
    "html" -> "text/html; charset=utf-8"
    "js" -> "text/javascript; charset=utf-8"
    "webmanifest" -> "application/manifest+json"
    "css" -> "text/css; charset=utf-8"
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "woff2" -> "font/woff2"
    "json" -> "application/json"
    else -> "application/octet-stream"
  }

  // ---------------- 狀態 ----------------

  private fun stateJson(): JSONObject {
    val s = Store.state.value
    val c = s.config
    val session = s.session
    val status = when {
      s.pendingApproval != null -> "pending"
      s.timeUp -> "timeup"
      session == null -> "locked"
      session.unlimited -> "unlimited"
      session.running -> "watching"
      else -> "paused"
    }
    return JSONObject()
      .put("status", status)
      .put("who", JSONArray(session?.kids ?: emptyList<String>()))
      .put("remainingMinutes", session?.takeIf { !it.unlimited }?.let { (it.remainingMs + 59_999) / 60_000 } ?: JSONObject.NULL)
      .put("today", JSONArray(c.kids.map { JSONObject().put("name", it).put("minutes", (s.watchedToday[it] ?: 0) / 60_000) }))
      .put("config", JSONObject()
        .put("kids", JSONArray(c.kids))
        .put("presets", JSONArray(c.presets))
        .put("customMinutes", c.customMinutes)
        .put("dailyLimit", c.dailyLimit)
        .put("warnMinutes", c.warnMinutes))
      .put("updatedAt", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
  }

  // ---------------- 動作 ----------------

  /** 執行動作,回傳(密碼對不對, 要顯示的訊息) */
  private fun act(f: JSONObject): Pair<Boolean, String> {
    val now = SystemClock.elapsedRealtime()
    if (now < lockedUntil) return false to "密碼錯太多次,請 ${(lockedUntil - now) / 60_000 + 1} 分鐘後再試"
    // 手機輸入法可能多帶空白或全形數字,先整理成半形數字再比
    val typed = f.optString("pin").map { if (it in '０'..'９') '0' + (it - '０') else it }.filter { it.isDigit() }.joinToString("")
    if (typed != Store.state.value.config.pin) {
      if (++wrong >= 5) { wrong = 0; lockedUntil = now + 10 * 60_000; return false to "密碼錯 5 次,請 10 分鐘後再試" }
      return false to "密碼不對,還可以試 ${5 - wrong} 次"
    }
    wrong = 0
    return true to onMain {
      val s = Store.state.value
      when (f.optString("action")) {
        "check" -> "密碼正確"
        "approve" -> if (s.pendingApproval != null) {
          Store.startUnlimited(emptyList()); "已同意,電視可以不限時間看"
        } else "目前沒有要同意的請求"
        "chime" -> { testChime(); "電視正在播時間到的音效(10 秒)" }
        "unlimited" -> { Store.startUnlimited(emptyList()); "電視現在不限時間" }
        "deny" -> { Store.cancelApproval(); "沒有同意,電視回到選人畫面" }
        "add" -> {
          val min = f.optInt("minutes", 15).coerceIn(5, 240)
          Store.addMinutes(min); "加了 $min 分鐘"
        }
        "stop" -> { Store.stop(); "已停止,電視回到選人畫面" }
        "save" -> {
          val kids = f.optJSONArray("kids")?.let { a -> List(a.length()) { a.optString(it).trim() } }?.filter { it.isNotEmpty() }
          val presets = f.optJSONArray("presets")?.let { a -> List(a.length()) { a.optInt(it) } }?.filter { it > 0 }?.map { it.coerceIn(5, 240) }
          val newPin = f.optString("newPin").trim()
          if (newPin.isNotEmpty() && !newPin.matches(Regex("\\d{4,8}"))) return@onMain "新密碼要 4~8 位數字"
          Store.saveConfig(s.config.copy(
            kids = kids?.ifEmpty { null }?.take(6) ?: s.config.kids,
            pin = newPin.ifEmpty { s.config.pin },
            customMinutes = f.optInt("customMinutes", s.config.customMinutes).coerceIn(5, 240),
            presets = presets?.ifEmpty { null }?.take(3) ?: s.config.presets,
            dailyLimit = f.optInt("dailyLimit", s.config.dailyLimit).coerceIn(0, 1440),
            warnMinutes = f.optInt("warnMinutes", s.config.warnMinutes).coerceIn(0, 30),
          ))
          if (newPin.isNotEmpty()) Store.markSetupShown()
          if (newPin.isNotEmpty()) "設定已儲存,密碼已換成新的" else "設定已儲存"
        }
        else -> "不認得的動作"
      }
    }
  }

  private fun <T> onMain(block: () -> T): T {
    var result: Result<T>? = null
    val latch = CountDownLatch(1)
    main.post { result = runCatching(block); latch.countDown() }
    latch.await()
    return result!!.getOrThrow()
  }
}
