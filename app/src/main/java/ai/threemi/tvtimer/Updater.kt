package ai.threemi.tvtimer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 爸媽在手機上按「檢查更新 / 更新」:電視去 GitHub Releases 看最新版,下載 tv-timer.apk 自己裝。
 *  - 只裝簽章跟現在這版一樣、版本比較新的 APK
 *  - 安裝程式(install/mac.sh、windows.ps1)用 `install -i` 把這個 App 登記成自己的安裝者,
 *    並開 REQUEST_INSTALL_PACKAGES,Android 12 以上就能不用在電視上按確認直接更新;
 *    不然電視會跳確認視窗,這時先把鎖定畫面收起來,不然會蓋住它
 *  - 裝好後系統會重開 App(BootReceiver 收 MY_PACKAGE_REPLACED),正在看的計時會接著算
 */
object Updater {
  /** phase:idle / checking / latest / available / downloading / installing / confirm / failed */
  data class Status(
    val phase: String = "idle",
    val latest: String? = null,
    val notes: String? = null,
    val progress: Int = 0,
    val message: String? = null,
  ) {
    fun toJson(): JSONObject = JSONObject()
      .put("phase", phase)
      .put("latest", latest ?: JSONObject.NULL)
      .put("notes", notes ?: JSONObject.NULL)
      .put("progress", progress)
      .put("message", message ?: JSONObject.NULL)
  }

  private val _status = MutableStateFlow(Status())
  val status: StateFlow<Status> = _status
  private var apkUrl: String? = null
  private val main = Handler(Looper.getMainLooper())

  private val busy get() = _status.value.phase in setOf("checking", "downloading", "installing", "confirm")

  /**
   * 問 GitHub 最新版(會等結果,給手機頁馬上顯示)。
   * quiet = 電視自己定時檢查:只是為了讓手機頁出現「有新版」提示,不會自己更新;
   * 連不上就安靜放棄,不蓋掉畫面上的狀態
   */
  fun check(quiet: Boolean = false): Status {
    if (busy) return _status.value
    val before = _status.value
    if (!quiet) _status.value = Status("checking")
    val result = runCatching {
      val json = JSONObject(get(BuildConfig.UPDATE_URL))
      val tag = json.getString("tag_name").removePrefix("v")
      val asset = json.optJSONArray("assets")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        ?.firstOrNull { it.optString("name") == "tv-timer.apk" }
      apkUrl = asset?.optString("browser_download_url")
      // 版本說明只拿「這版有什麼」那段,拿掉 Markdown 的粗體記號
      val body = json.optString("body")
      val notes = (Regex("##\\s*這版有什麼\\s*\\n([\\s\\S]*?)(\\n## |$)").find(body)?.groupValues?.get(1) ?: body)
        .replace("**", "").trim().take(2000)
      when {
        newer(tag, BuildConfig.VERSION_NAME) && apkUrl != null -> Status("available", tag, notes)
        else -> Status("latest", tag)
      }
    }.getOrElse { if (quiet) before else Status("failed", message = "連不到 GitHub,電視有連上網路嗎?") }
    // 背景檢查時如果手機剛好按了更新,不要蓋掉進度
    if (!(quiet && busy)) _status.value = result
    return _status.value
  }

  /** 下載並安裝;在背景跑,進度放在 status */
  fun install(context: Context): String {
    val ctx = context.applicationContext
    if (busy) return "正在處理中"
    val url = apkUrl
    if (_status.value.phase != "available" || url == null) return "請先檢查更新"
    if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
      _status.value = Status("failed", message = "這台電視還沒開放用手機更新。請用電腦跑一次安裝程式(選 1),之後就能用手機更新。")
      return _status.value.message!!
    }
    val latest = _status.value.latest
    _status.value = Status("downloading", latest)
    thread(name = "updater") {
      runCatching {
        val file = File(ctx.cacheDir, "update.apk")
        download(url, file) { pct -> _status.value = Status("downloading", latest, progress = pct) }
        verify(ctx, file)
        _status.value = Status("installing", latest)
        commit(ctx, file)
      }.onFailure { _status.value = Status("failed", latest, message = it.message ?: "更新失敗") }
    }
    return "開始下載 $latest"
  }

  /** 安裝結果(PackageInstaller 回呼) */
  class Receiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val latest = _status.value.latest
      when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
          // 要在電視上按「安裝」:先收起鎖定畫面(GuardService 看 phase),3 分鐘沒按就放棄
          _status.value = Status("confirm", latest)
          @Suppress("DEPRECATION")
          val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
          confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
          main.postDelayed({
            if (_status.value.phase == "confirm") _status.value = Status("failed", latest, message = "電視上沒有按「安裝」,這次沒有更新")
          }, 3 * 60_000)
        }
        PackageInstaller.STATUS_SUCCESS -> _status.value = Status("idle")   // 通常來不及收到,App 就被重開了
        else -> _status.value = Status("failed", latest,
          message = "安裝失敗:" + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "未知原因"))
      }
    }
  }

  // ---------------- 內部 ----------------

  private fun get(url: String): String {
    val c = (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = 10_000; readTimeout = 15_000
      setRequestProperty("Accept", "application/vnd.github+json")
      setRequestProperty("User-Agent", "tv-timer/${BuildConfig.VERSION_NAME}")
    }
    try {
      if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
      return c.inputStream.bufferedReader().readText()
    } finally { c.disconnect() }
  }

  private fun download(url: String, file: File, onProgress: (Int) -> Unit) {
    val c = (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = 10_000; readTimeout = 30_000
      setRequestProperty("User-Agent", "tv-timer/${BuildConfig.VERSION_NAME}")
    }
    try {
      if (c.responseCode !in 200..299) error("下載失敗(HTTP ${c.responseCode})")
      val total = c.contentLengthLong
      var done = 0L
      var last = -1
      c.inputStream.use { inp ->
        file.outputStream().use { out ->
          val buf = ByteArray(64 * 1024)
          while (true) {
            val n = inp.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            done += n
            val pct = if (total > 0) (done * 100 / total).toInt() else 0
            if (pct != last) { last = pct; onProgress(pct) }
          }
        }
      }
    } finally { c.disconnect() }
  }

  /** 下載的 APK 要是同一個 App、同一把金鑰、版本比較新 */
  @Suppress("DEPRECATION")
  private fun verify(ctx: Context, file: File) {
    val pm = ctx.packageManager
    val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    val apk = pm.getPackageArchiveInfo(file.path, flags) ?: error("下載的檔案壞掉了,請再試一次")
    val mine = pm.getPackageInfo(ctx.packageName, flags)
    if (apk.packageName != ctx.packageName) error("下載的不是這個 App")
    val apkCode = if (Build.VERSION.SDK_INT >= 28) apk.longVersionCode else apk.versionCode.toLong()
    val myCode = if (Build.VERSION.SDK_INT >= 28) mine.longVersionCode else mine.versionCode.toLong()
    if (apkCode <= myCode) error("下載的版本沒有比較新")
    val same = if (Build.VERSION.SDK_INT >= 28) {
      apk.signingInfo?.apkContentsSigners?.contentEquals(mine.signingInfo?.apkContentsSigners) == true
    } else apk.signatures?.contentEquals(mine.signatures) == true
    if (!same) error("新版的簽章跟電視上這版不同,沒辦法直接更新。請用電腦安裝程式(選 1)重裝。")
  }

  private fun commit(ctx: Context, file: File) {
    val installer = ctx.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
      setAppPackageName(ctx.packageName)
      if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
    }
    val id = installer.createSession(params)
    installer.openSession(id).use { s ->
      s.openWrite("tv-timer.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; s.fsync(out) }
      val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, Receiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
      s.commit(pi.intentSender)
    }
  }

  /** "1.10" > "1.9" */
  private fun newer(a: String, b: String): Boolean {
    val x = a.split(".").map { it.toIntOrNull() ?: 0 }
    val y = b.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(x.size, y.size)) {
      val d = (x.getOrElse(i) { 0 }) - (y.getOrElse(i) { 0 })
      if (d != 0) return d > 0
    }
    return false
  }
}
