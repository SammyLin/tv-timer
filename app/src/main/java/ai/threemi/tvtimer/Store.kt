package ai.threemi.tvtimer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.time.LocalDate
import kotlin.random.Random

/** 爸媽在手機控制頁可以改的設定 */
data class Config(
  val kids: List<String>,
  val pin: String,
  /** 「自訂」按鈕預設幾分鐘 */
  val customMinutes: Int,
  /** 快速按鈕(分鐘) */
  val presets: List<Int>,
  /** 每人每天最多看幾分鐘,0 = 不限 */
  val dailyLimit: Int,
  /** 剩幾分鐘開始在角落提醒 */
  val warnMinutes: Int,
)

/** 一次看電視。running=false 表示暫停(電視關掉或按暫停),下次可以「繼續上次」 */
data class Session(
  val kids: List<String>,
  val unlimited: Boolean,
  val remainingMs: Long,
  val running: Boolean,
)

data class State(
  val config: Config,
  val session: Session?,
  /** 時間剛用完,鎖定畫面顯示「時間到囉」 */
  val timeUp: Boolean = false,
  /** 小孩按了「不限制」,等爸媽手機同意;值是這次請求的代碼 */
  val pendingApproval: String? = null,
  /** 今天每個人已經看了幾毫秒 */
  val watchedToday: Map<String, Long> = emptyMap(),
)

object Store {
  private lateinit var prefs: SharedPreferences
  private val _state = MutableStateFlow(State(defaultConfig(), null))
  val state: StateFlow<State> = _state

  fun init(context: Context) {
    if (::prefs.isInitialized) return
    prefs = context.applicationContext.getSharedPreferences("tvtimer", Context.MODE_PRIVATE)
    if (!prefs.contains("pin")) prefs.edit().putString("pin", "%04d".format(Random.nextInt(10000))).apply()
    _state.value = State(loadConfig(), loadSession(), watchedToday = loadWatched(loadConfig().kids))
  }

  val isSetupShown get() = prefs.getBoolean("setup_shown", false)
  fun markSetupShown() = prefs.edit().putBoolean("setup_shown", true).apply()

  // ---- 設定 ----

  private fun defaultConfig() = Config(listOf("哥哥", "妹妹"), "0000", 45, listOf(30, 60), 120, 5)

  private fun loadConfig(): Config {
    val d = defaultConfig()
    val kids = prefs.getString("kids", null)?.let { s -> JSONArray(s).let { a -> List(a.length()) { a.getString(it) } } } ?: d.kids
    val presets = prefs.getString("presets", null)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.isNotEmpty() } ?: d.presets
    return Config(
      kids = kids,
      pin = prefs.getString("pin", d.pin)!!,
      customMinutes = prefs.getInt("custom", d.customMinutes),
      presets = presets,
      dailyLimit = prefs.getInt("daily", d.dailyLimit),
      warnMinutes = prefs.getInt("warn", d.warnMinutes),
    )
  }

  fun saveConfig(c: Config) {
    prefs.edit()
      .putString("kids", JSONArray(c.kids).toString())
      .putString("pin", c.pin)
      .putInt("custom", c.customMinutes)
      .putString("presets", c.presets.joinToString(","))
      .putInt("daily", c.dailyLimit)
      .putInt("warn", c.warnMinutes)
      .apply()
    _state.value = _state.value.copy(config = c, watchedToday = loadWatched(c.kids))
  }

  // ---- 這次看電視 ----

  private fun loadSession(): Session? {
    if (!prefs.contains("s_remaining")) return null
    val kids = JSONArray(prefs.getString("s_kids", "[]")).let { a -> List(a.length()) { a.getString(it) } }
    // 電視重開機後當暫停,要小孩再按「繼續上次」;
    // 但只是 App 更新或被系統重啟(15 秒內還在計時)就直接接著算
    val justRunning = prefs.getBoolean("s_running", false) &&
      System.currentTimeMillis() - prefs.getLong("s_saved_at", 0) in 0..15_000
    val unlimited = prefs.getBoolean("s_unlimited", false)
    // 不限制只在 App 更新/重啟時接著用;電視重開機就回到鎖定
    if (unlimited) return if (justRunning) Session(kids, true, 0, true) else null
    return Session(kids, false, prefs.getLong("s_remaining", 0), running = justRunning)
      .takeIf { it.remainingMs > 0 }
  }

  private fun saveSession(s: Session?) {
    val e = prefs.edit()
    if (s == null) e.remove("s_kids").remove("s_unlimited").remove("s_remaining").remove("s_running")
    else e.putString("s_kids", JSONArray(s.kids).toString()).putBoolean("s_unlimited", s.unlimited).putLong("s_remaining", s.remainingMs)
      .putBoolean("s_running", s.running).putLong("s_saved_at", System.currentTimeMillis())
    e.apply()
  }

  private fun update(f: (State) -> State) {
    val old = _state.value
    val new = f(old)
    _state.value = new
    if (old.session != new.session) saveSession(new.session)
  }

  fun start(kids: List<String>, minutes: Int) = update {
    it.copy(session = Session(kids, false, minutes * 60_000L, true), timeUp = false, pendingApproval = null)
  }

  fun startUnlimited(kids: List<String>) = update {
    it.copy(session = Session(kids, true, 0, true), timeUp = false, pendingApproval = null)
  }

  fun resume() = update { s -> s.copy(session = s.session?.copy(running = true), timeUp = false) }

  /** 暫停:沒限時的直接結束,有限時的留著剩下的時間 */
  fun pause() = update { s ->
    val cur = s.session ?: return@update s
    s.copy(session = if (cur.unlimited) null else cur.copy(running = false))
  }

  fun stop() = update { it.copy(session = null, timeUp = false, pendingApproval = null) }

  fun addMinutes(min: Int) = update { s ->
    val cur = s.session
    val next = if (cur == null || cur.unlimited) cur else cur.copy(remainingMs = cur.remainingMs + min * 60_000L, running = true)
    s.copy(session = next ?: Session(emptyList(), false, min * 60_000L, true), timeUp = false)
  }

  fun dismissTimeUp() = update { it.copy(timeUp = false) }

  /** 每秒呼叫一次;回傳這次是不是剛好時間到 */
  fun tick(elapsedMs: Long): Boolean {
    var ended = false
    update { s ->
      val cur = s.session?.takeIf { it.running } ?: return@update s
      val watched = s.watchedToday.toMutableMap()
      cur.kids.forEach { k -> watched[k] = (watched[k] ?: 0) + elapsedMs }
      saveWatched(watched)
      if (cur.unlimited) return@update s.copy(watchedToday = watched)
      val left = cur.remainingMs - elapsedMs
      if (left <= 0) {
        ended = true
        s.copy(session = null, timeUp = true, watchedToday = watched)
      } else s.copy(session = cur.copy(remainingMs = left), watchedToday = watched)
    }
    // 不限制的剩餘時間不會變,要另外蓋時間戳,App 更新時才知道「剛剛還在看」
    _state.value.session?.takeIf { it.unlimited && it.running }?.let { saveSession(it) }
    return ended
  }

  // ---- 爸媽同意「不限制」 ----

  fun requestApproval(): String {
    val code = _state.value.pendingApproval ?: "%06d".format(Random.nextInt(1_000_000))
    update { it.copy(pendingApproval = code) }
    return code
  }

  fun cancelApproval() = update { it.copy(pendingApproval = null) }

  // ---- 今天看了多久 ----

  private fun today() = LocalDate.now().toString()

  private fun loadWatched(kids: List<String>): Map<String, Long> {
    if (prefs.getString("w_date", null) != today()) return emptyMap()
    return kids.associateWith { prefs.getLong("w_$it", 0) }
  }

  private fun saveWatched(w: Map<String, Long>) {
    val e = prefs.edit()
    if (prefs.getString("w_date", null) != today()) {
      prefs.all.keys.filter { it.startsWith("w_") }.forEach { e.remove(it) }
      e.putString("w_date", today())
    }
    w.forEach { (k, v) -> e.putLong("w_$k", v) }
    e.apply()
  }

  /** 換日時把「今天已看」歸零 */
  fun rolloverIfNeeded() {
    if (prefs.getString("w_date", null) != today() && _state.value.watchedToday.values.any { it > 0 }) {
      _state.value = _state.value.copy(watchedToday = emptyMap())
    }
  }
}
