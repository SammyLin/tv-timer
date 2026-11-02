package ai.threemi.tvtimer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * 每天的觀看紀錄,給爸媽手機看「觀看分析」:
 *  - 每個人看了幾秒(沒選人的「全家」記在 FAMILY)
 *  - 其中不限制的秒數
 *  - 每個整點各有幾秒在看(看幾點在看電視)
 * 存在自己的 SharedPreferences 檔(history.xml),一天一筆;超過保留天數的自動刪掉。
 * 每秒都在加,但只有每 10 秒、換日、或停下來(停止/暫停/時間到)時才寫進檔案。
 */
object History {
  const val FAMILY = ""
  const val DEFAULT_RETENTION = 30
  val RETENTION_CHOICES = listOf(7, 14, 30, 60, 90, 180)

  class Day(
    val kids: MutableMap<String, Long> = mutableMapOf(),
    var unlimitedMs: Long = 0,
    val hours: LongArray = LongArray(24),
  )

  private lateinit var prefs: SharedPreferences
  private val days = sortedMapOf<String, Day>()
  private var dirty = false
  private var unsavedMs = 0L

  fun init(context: Context) {
    if (::prefs.isInitialized) return
    prefs = context.applicationContext.getSharedPreferences("history", Context.MODE_PRIVATE)
    prefs.all.forEach { (k, v) -> if (k.startsWith("d_") && v is String) runCatching { days[k.removePrefix("d_")] = parse(v) } }
    prune()
  }

  val retentionDays get() = prefs.getInt("retention", DEFAULT_RETENTION)

  fun setRetention(daysToKeep: Int) {
    prefs.edit().putInt("retention", daysToKeep).apply()
    prune()
  }

  /** 正在看的時候每秒呼叫:kids 是這次在看的人(空的就是全家) */
  @Synchronized
  fun add(kids: List<String>, unlimited: Boolean, elapsedMs: Long) {
    if (elapsedMs <= 0) return
    val date = LocalDate.now().toString()
    val newDay = date !in days
    val d = days.getOrPut(date) { Day() }
    (kids.ifEmpty { listOf(FAMILY) }).forEach { d.kids[it] = (d.kids[it] ?: 0) + elapsedMs }
    if (unlimited) d.unlimitedMs += elapsedMs
    d.hours[LocalTime.now().hour] += elapsedMs
    dirty = true
    unsavedMs += elapsedMs
    if (newDay) prune()
    if (newDay || unsavedMs >= 10_000) flush()
  }

  @Synchronized
  fun flush() {
    if (!dirty || !::prefs.isInitialized) return
    val e = prefs.edit()
    days.forEach { (date, d) -> e.putString("d_$date", toJson(d).toString()) }
    e.apply()
    dirty = false
    unsavedMs = 0
  }

  /** date = null 刪全部,不然只刪那一天 */
  @Synchronized
  fun clear(date: String?) {
    val e = prefs.edit()
    if (date == null) {
      days.keys.forEach { e.remove("d_$it") }
      days.clear()
    } else {
      days.remove(date)
      e.remove("d_$date")
    }
    e.apply()
  }

  /** 刪掉保留天數以前的(今天算第 1 天) */
  @Synchronized
  private fun prune() {
    val oldest = LocalDate.now().minusDays((retentionDays - 1).toLong()).toString()
    val old = days.keys.filter { it < oldest }
    if (old.isEmpty()) return
    val e = prefs.edit()
    old.forEach { days.remove(it); e.remove("d_$it") }
    e.apply()
  }

  /** GET /api/history 的內容;時間都是秒 */
  @Synchronized
  fun toApiJson(kids: List<String>): JSONObject {
    prune()
    val list = JSONArray()
    days.forEach { (date, d) ->
      list.put(JSONObject()
        .put("date", date)
        .put("kids", JSONObject().apply { d.kids.forEach { (k, v) -> put(k, v / 1000) } })
        .put("unlimited", d.unlimitedMs / 1000)
        .put("hours", JSONArray(d.hours.map { it / 1000 })))
    }
    return JSONObject()
      .put("retentionDays", retentionDays)
      .put("retentionChoices", JSONArray(RETENTION_CHOICES))
      .put("today", LocalDate.now().toString())
      .put("kids", JSONArray(kids))
      .put("days", list)
  }

  private fun toJson(d: Day) = JSONObject()
    .put("k", JSONObject().apply { d.kids.forEach { (k, v) -> put(k, v) } })
    .put("u", d.unlimitedMs)
    .put("h", JSONArray(d.hours.toList()))

  private fun parse(s: String): Day {
    val o = JSONObject(s)
    val day = Day(unlimitedMs = o.optLong("u"))
    o.optJSONObject("k")?.let { k -> k.keys().forEach { day.kids[it] = k.optLong(it) } }
    o.optJSONArray("h")?.let { h -> for (i in 0 until minOf(24, h.length())) day.hours[i] = h.optLong(i) }
    return day
  }
}
