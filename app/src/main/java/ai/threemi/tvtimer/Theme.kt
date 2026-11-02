package ai.threemi.tvtimer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject

/**
 * 電視畫面的配色,爸媽在手機「設定 → 電視配色」挑,電視馬上換。
 * 每組都是同一套角色:
 *  ground 最底、surface 卡片、raised 卡片裡的凹槽/未選的進度條、lit 遙控器焦點、
 *  text 主要字(也是「選到」的反白底色)、ink 反白上的字、muted/faint 次要字、accent 時間數字。
 */
data class Palette(
  val id: String,
  val name: String,
  val light: Boolean,
  val ground: Color,
  val surface: Color,
  val raised: Color,
  val lit: Color,
  val text: Color,
  val ink: Color,
  val muted: Color,
  val faint: Color,
  val accent: Color,
)

object Theme {
  val ALL = listOf(
    Palette("warm", "暖黑", false,
      Color(0xFF0E0D0C), Color(0xFF181716), Color(0xFF2A2826), Color(0xFF3A3735),
      Color(0xFFFFFFFF), Color(0xFF0E0D0C), Color(0xFFB4B4B4), Color(0xFF949494), Color(0xFFF2B33D)),
    Palette("night", "深夜藍", false,
      Color(0xFF0B1020), Color(0xFF141B2E), Color(0xFF222B42), Color(0xFF323C58),
      Color(0xFFF5F7FF), Color(0xFF0B1020), Color(0xFFAEB6CC), Color(0xFF8A93AB), Color(0xFF7DD3FC)),
    Palette("forest", "森林綠", false,
      Color(0xFF0C1410), Color(0xFF142019), Color(0xFF213027), Color(0xFF2E4135),
      Color(0xFFF1F7F2), Color(0xFF0C1410), Color(0xFFA9BDB0), Color(0xFF86998C), Color(0xFFA3E635)),
    Palette("light", "純白", true,
      Color(0xFFF4F3F0), Color(0xFFFFFFFF), Color(0xFFE4E1DC), Color(0xFFDAD6D0),
      Color(0xFF1A1917), Color(0xFFFFFFFF), Color(0xFF57534E), Color(0xFF78716C), Color(0xFFC2410C)),
    Palette("milktea", "奶茶", true,
      Color(0xFFF6EEDF), Color(0xFFFFFAF1), Color(0xFFEBDCC1), Color(0xFFE2CFAC),
      Color(0xFF3B2A14), Color(0xFFFFFAF1), Color(0xFF6B5840), Color(0xFF8A775C), Color(0xFFB45309)),
    Palette("macaron", "馬卡龍", true,
      Color(0xFFFFF1F3), Color(0xFFFFFFFF), Color(0xFFFFDDE5), Color(0xFFFFCCD9),
      Color(0xFF4A3456), Color(0xFFFFFFFF), Color(0xFF75628A), Color(0xFF94839F), Color(0xFFD9467A)),
  )
  const val DEFAULT = "warm"

  /** 目前的配色;Compose 讀到會跟著重畫 */
  var current by mutableStateOf(ALL.first())
    private set

  fun apply(id: String) { current = ALL.firstOrNull { it.id == id } ?: ALL.first() }

  fun isValid(id: String) = ALL.any { it.id == id }

  /** 給手機頁畫色塊預覽 */
  fun toJson(): JSONArray = JSONArray(ALL.map { p ->
    JSONObject()
      .put("id", p.id).put("name", p.name).put("light", p.light)
      .put("ground", hex(p.ground)).put("surface", hex(p.surface)).put("text", hex(p.text)).put("accent", hex(p.accent))
  })

  private fun hex(c: Color) = "#%06X".format(0xFFFFFF and c.toArgb())
}
