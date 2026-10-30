package ai.threemi.tvtimer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 介面規則(taste-skill 的 true-black-flat):
 *  - 暖黑底、卡片只靠亮度分層,沒有漸層、陰影、外框、emoji
 *  - 唯一的顏色是「燈光琥珀」,只用在時間數字上(剩幾分、今天還能看多少),不上按鈕
 *  - 主要按鈕用反白(白底黑字的膠囊);選到的選項也是反白
 *  - 遙控器焦點:放大一點、表面變亮、白色外圈,三公尺外看得到
 *  - 每個小孩有固定的字首圓圈和自己的淡色
 * 字級是網頁規格放大約 1.5 倍,給沙發距離看的電視用。
 */
object T {
  val Ground = Color(0xFF0E0D0C)
  val Surface = Color(0xFF181716)
  val Raised = Color(0xFF2A2826)
  val Lit = Color(0xFF3A3735)
  val Text = Color(0xFFFFFFFF)
  val Muted = Color(0xFFB4B4B4)
  val Faint = Color(0xFF949494)
  val Amber = Color(0xFFF2B33D)
  val Ink = Color(0xFF0E0D0C)
  val Card = RoundedCornerShape(20.dp)
  val Pill = RoundedCornerShape(999.dp)

  /** 每個小孩一個色相,彼此差 60 度以上;避開紫色 */
  private val hues = floatArrayOf(200f, 340f, 150f, 30f, 100f, 250f)
  fun kidBg(i: Int) = Color.hsl(hues[i % hues.size], 0.35f, 0.24f)
  fun kidInk(i: Int) = Color.hsl(hues[i % hues.size], 0.75f, 0.82f)
}

/** 電視四周留安全邊(有些電視會裁掉邊緣) */
private val Edge = 56.dp

@Composable
fun LockScreen() {
  val s by Store.state.collectAsState()
  Box(Modifier.fillMaxSize().background(T.Ground).padding(horizontal = Edge, vertical = 28.dp)) {
    when {
      s.timeUp -> TimeUp(s)
      s.pendingApproval != null -> Approval(s.pendingApproval!!)
      else -> Picker(s)
    }
  }
}

// ---------------- 選誰、選多久 ----------------

private const val CUSTOM = -1

@Composable
private fun Picker(s: State) {
  val cfg = s.config
  val paused = s.session?.takeIf { !it.running && !it.unlimited && it.remainingMs > 0 }
  var kids by remember { mutableStateOf(listOf<String>()) }
  var unlimited by remember { mutableStateOf(false) }
  var minutes by rememberSaveable { mutableIntStateOf(cfg.presets.firstOrNull() ?: 30) }
  var custom by rememberSaveable { mutableIntStateOf(cfg.customMinutes) }
  var editingCustom by remember { mutableStateOf(false) }
  val first = remember { FocusRequester() }
  LaunchedEffect(editingCustom) { if (!editingCustom) runCatching { first.requestFocus() } }

  fun leftToday(kid: String): Long? =
    if (cfg.dailyLimit <= 0) null else (cfg.dailyLimit * 60_000L - (s.watchedToday[kid] ?: 0)).coerceAtLeast(0)

  if (editingCustom) {
    CustomMinutes(custom) { custom = it; minutes = CUSTOM; editingCustom = false }
    return
  }

  val ready = unlimited || kids.isNotEmpty()
  fun start() = when {
    unlimited -> { Store.requestApproval(); Unit }
    kids.isEmpty() -> Unit
    else -> {
      val want = if (minutes == CUSTOM) custom else minutes
      // 有每日上限時,最多看到其中一個人用完為止
      val cap = kids.mapNotNull { leftToday(it) }.minOrNull()?.let { mins(it) }
      Store.start(kids, if (cap != null) minOf(want, cap) else want)
    }
  }

  Column(Modifier.fillMaxSize()) {
    TopBar()
    Spacer(Modifier.weight(0.5f))
    Text("誰要看電視?", style = H1)
    // 有「繼續上次」時畫面比較擠,說明那行就收起來
    if (paused == null) {
      Spacer(Modifier.height(6.dp))
      Text(
        "最多選兩個人" + if (cfg.dailyLimit > 0) ",每人每天 ${cfg.dailyLimit} 分" else "",
        style = Body.copy(color = T.Muted),
      )
    }
    Spacer(Modifier.height(18.dp))

    if (paused != null) {
      Focusable(
        Modifier.fillMaxWidth().height(76.dp).focusRequester(first),
        shape = T.Card,
        onClick = { Store.resume() },
      ) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("繼續上次", style = Small.copy(color = T.Muted))
            Text(paused.kids.joinToString("、"), style = Name)
          }
          Minutes(mins(paused.remainingMs), 40.sp, label = "還剩")
        }
      }
      Spacer(Modifier.height(14.dp))
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      cfg.kids.forEachIndexed { i, kid ->
        val left = leftToday(kid)
        val done = left == 0L
        KidCard(
          Modifier.weight(1f).let { if (i == 0 && paused == null) it.focusRequester(first) else it },
          index = i,
          name = kid,
          selected = kid in kids,
          disabled = done,
          pausedLeft = paused?.takeIf { kid in it.kids }?.let { mins(it.remainingMs) },
          watched = watchedMins(s.watchedToday[kid] ?: 0),
          left = left?.let { mins(it) },
          limit = cfg.dailyLimit,
        ) {
          if (!done) {
            unlimited = false
            kids = if (kid in kids) kids - kid else (kids + kid).takeLast(2)
          }
        }
      }
      Focusable(
        Modifier.weight(1f).height(116.dp),
        shape = T.Card,
        selected = unlimited,
        onClick = { unlimited = !unlimited; if (unlimited) kids = emptyList() },
      ) { inverted ->
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center) {
          Text("不限制", style = Name.copy(color = if (inverted) T.Ink else T.Text))
          Text("要爸媽手機同意", style = Small.copy(color = if (inverted) T.Raised else T.Muted))
        }
      }
    }

    Spacer(Modifier.weight(1f).heightIn(min = 18.dp))
    Text("看多久", style = Body.copy(color = T.Muted))
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      cfg.presets.forEach { p ->
        Choice("$p 分", selected = !unlimited && minutes == p, disabled = unlimited) { minutes = p }
      }
      Choice("自訂 $custom 分", selected = !unlimited && minutes == CUSTOM, disabled = unlimited) { editingCustom = true }
      Spacer(Modifier.weight(1f))
      Primary(if (unlimited) "請爸媽同意" else "開始", enabled = ready, width = 210.dp) { start() }
    }
    Spacer(Modifier.weight(0.5f))
  }
}

@Composable
private fun KidCard(
  modifier: Modifier,
  index: Int,
  name: String,
  selected: Boolean,
  disabled: Boolean,
  pausedLeft: Int?,
  watched: Int,
  left: Int?,
  limit: Int,
  onClick: () -> Unit,
) {
  Focusable(modifier.height(116.dp), shape = T.Card, selected = selected, disabled = disabled, onClick = onClick) { inverted ->
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(T.kidBg(index)), contentAlignment = Alignment.Center) {
          Text(initial(name), style = TextStyle(color = T.kidInk(index), fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
        }
        Spacer(Modifier.width(14.dp))
        Text(name, style = Name.copy(color = if (inverted) T.Ink else T.Text), maxLines = 1)
      }
      val sub = if (inverted) T.Raised else T.Muted
      when {
        pausedLeft != null -> Row(verticalAlignment = Alignment.Bottom) {
          Text("上次還剩 ", style = Small.copy(color = sub))
          Text("$pausedLeft 分", style = Small.copy(color = if (inverted) T.Ink else T.Amber, fontWeight = FontWeight.SemiBold))
        }
        left != null -> Column {
          Row(verticalAlignment = Alignment.Bottom) {
            Text(if (left == 0) "今天看完了" else "今天還能看 ", style = Small.copy(color = sub))
            if (left > 0) Text("$left 分", style = Small.copy(color = if (inverted) T.Ink else T.Amber, fontWeight = FontWeight.SemiBold))
          }
          Spacer(Modifier.height(8.dp))
          Allowance(left.toFloat() / limit, inverted)
        }
        else -> Text("今天看了 $watched 分", style = Small.copy(color = sub))
      }
    }
  }
}

/** 今天剩下的額度:琥珀色是還能看的部分 */
@Composable
private fun Allowance(fraction: Float, inverted: Boolean) {
  Box(Modifier.fillMaxWidth().height(6.dp).clip(T.Pill).background(if (inverted) Color(0xFFDDDDDD) else T.Raised)) {
    Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(T.Pill).background(if (inverted) T.Ink else T.Amber))
  }
}

@Composable
private fun TopBar() {
  var now by remember { mutableStateOf(LocalTime.now()) }
  LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(15_000) } }
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Mark()
    Spacer(Modifier.width(12.dp))
    Text("看電視計時", style = Small.copy(color = T.Muted))
    Spacer(Modifier.weight(1f))
    Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = Small.copy(color = T.Muted))
  }
}

/** App 的記號:一個時鐘面,指針停在快一小時的地方 */
@Composable
private fun Mark() {
  Box(Modifier.size(30.dp).clip(CircleShape).background(T.Raised), contentAlignment = Alignment.Center) {
    Box(Modifier.size(width = 3.dp, height = 11.dp).offset(y = (-4).dp).background(T.Text))
    Box(Modifier.size(width = 9.dp, height = 3.dp).offset(x = 4.dp).background(T.Text))
  }
}

// ---------------- 時間到 ----------------

@Composable
private fun TimeUp(s: State) {
  val first = remember { FocusRequester() }
  LaunchedEffect(Unit) { first.requestFocus() }
  val names = s.config.kids.filter { (s.watchedToday[it] ?: 0) >= 60_000 }
  Column(Modifier.fillMaxSize()) {
    TopBar()
    Spacer(Modifier.weight(1f))
    Text("時間到了", style = H1.copy(fontSize = 64.sp))
    Spacer(Modifier.height(14.dp))
    if (names.isNotEmpty()) {
      Row(verticalAlignment = Alignment.Bottom) {
        names.forEachIndexed { i, k ->
          Text((if (i > 0) ",$k" else k) + "今天看了 ", style = Lead.copy(color = T.Muted))
          Text("${watchedMins(s.watchedToday[k] ?: 0)} 分", style = Lead.copy(color = T.Amber, fontWeight = FontWeight.SemiBold))
        }
      }
    }
    Text("站起來走走,讓眼睛看看窗外。", style = Lead.copy(color = T.Muted))
    Spacer(Modifier.height(36.dp))
    Primary("知道了", enabled = true, width = 240.dp, modifier = Modifier.focusRequester(first)) { Store.dismissTimeUp() }
    Spacer(Modifier.weight(1f))
  }
}

// ---------------- 等爸媽同意 ----------------

@Composable
private fun Approval(code: String) {
  val first = remember { FocusRequester() }
  LaunchedEffect(Unit) { first.requestFocus() }
  val url = remember(code) { "http://${Net.localIp() ?: "電視IP"}:${BuildConfig.PARENT_PORT}/?code=$code" }
  val qr = remember(url) { qrBitmap(url, 480) }
  Column(Modifier.fillMaxSize()) {
    TopBar()
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("不限制,要爸媽同意", style = H1)
        Spacer(Modifier.height(16.dp))
        Text("用手機相機掃右邊的 QR code,", style = Lead)
        Text("輸入爸媽密碼就會開始。", style = Lead)
        Spacer(Modifier.height(10.dp))
        Text("手機要連家裡的 Wi-Fi", style = Body.copy(color = T.Faint))
        Spacer(Modifier.height(36.dp))
        Secondary("取消", Modifier.focusRequester(first)) { Store.cancelApproval() }
      }
      Image(qr.asImageBitmap(), "爸媽同意用的 QR code", Modifier.size(340.dp).clip(T.Card).background(Color.White).padding(18.dp))
    }
  }
}

// ---------------- 自訂分鐘 ----------------

@Composable
private fun CustomMinutes(start: Int, onDone: (Int) -> Unit) {
  var m by remember { mutableIntStateOf(start) }
  val ok = remember { FocusRequester() }
  LaunchedEffect(Unit) { ok.requestFocus() }
  Column(Modifier.fillMaxSize()) {
    TopBar()
    Spacer(Modifier.height(20.dp))
    Text("看幾分鐘?", style = H1)
    Spacer(Modifier.weight(1f))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Secondary("少 5 分") { m = (m - 5).coerceAtLeast(5) }
      Box(Modifier.width(320.dp), contentAlignment = Alignment.Center) { Minutes(m, 96.sp) }
      Secondary("多 5 分") { m = (m + 5).coerceAtMost(240) }
    }
    Spacer(Modifier.weight(1f))
    Primary("好", enabled = true, width = 240.dp, modifier = Modifier.focusRequester(ok)) { onDone(m) }
  }
}

// ---------------- 計時中右上角 ----------------

@Composable
fun CountdownChip() {
  val s by Store.state.collectAsState()
  val ms = s.session?.remainingMs ?: 0
  val sec = (ms / 1000).toInt()
  val urgent = ms <= s.config.warnMinutes * 60_000L
  Row(
    // 整塊 50% 透明、字很小,不擋畫面
    Modifier.alpha(0.5f).clip(RoundedCornerShape(10.dp)).background(T.Surface).padding(horizontal = if (urgent) 12.dp else 9.dp, vertical = if (urgent) 7.dp else 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (urgent) {
      Text("還剩", style = Body.copy(color = T.Muted, fontSize = 10.sp))
      Spacer(Modifier.width(6.dp))
      Text("%d:%02d".format(sec / 60, sec % 60), style = Figure.copy(fontSize = 17.sp))
    } else {
      // 平常小小的、灰色,不搶畫面
      Text("剩 ${mins(ms)} 分", style = Small.copy(color = T.Muted, fontSize = 9.sp, fontFeatureSettings = "tnum"))
    }
  }
}

// ---------------- 元件 ----------------

private val H1 = TextStyle(color = T.Text, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
private val Lead = TextStyle(color = T.Text, fontSize = 26.sp, lineHeight = 38.sp)
private val Name = TextStyle(color = T.Text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
private val Body = TextStyle(color = T.Text, fontSize = 20.sp)
private val Small = TextStyle(color = T.Text, fontSize = 18.sp)
private val Figure = TextStyle(color = T.Amber, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

@Composable
private fun Minutes(n: Int, size: TextUnit, label: String? = null) {
  Row {
    if (label != null) Text("$label ", style = Body.copy(color = T.Muted), modifier = Modifier.alignByBaseline())
    Text("$n", style = Figure.copy(fontSize = size), modifier = Modifier.alignByBaseline())
    Text(" 分", style = Body.copy(color = T.Muted), modifier = Modifier.alignByBaseline())
  }
}

/**
 * 可以用遙控器選的東西。selected = 反白(白底);焦點 = 放大 + 變亮 + 白圈。
 * content 拿到 inverted,讓裡面的字跟著換顏色。
 */
@Composable
private fun Focusable(
  modifier: Modifier,
  shape: Shape,
  selected: Boolean = false,
  disabled: Boolean = false,
  fill: Color = T.Surface,
  onClick: () -> Unit,
  content: @Composable (inverted: Boolean) -> Unit,
) {
  var focused by remember { mutableStateOf(false) }
  val bg = when {
    selected -> T.Text
    focused -> T.Lit
    disabled -> T.Ground
    else -> fill
  }
  Box(
    modifier
      .scale(if (focused) 1.05f else 1f)
      .onFocusChanged { focused = it.isFocused }
      .border(if (focused) 3.dp else 0.dp, if (focused) T.Text else Color.Transparent, shape)
      .padding(if (focused) 6.dp else 0.dp)
      .clip(shape)
      .background(bg)
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
  ) { content(selected) }
}

@Composable
private fun RowScope.Choice(label: String, selected: Boolean, disabled: Boolean, onClick: () -> Unit) {
  Focusable(Modifier.height(72.dp), shape = T.Pill, selected = selected, disabled = disabled, onClick = onClick) { inverted ->
    // 最多三個快速按鈕 + 自訂 + 開始要排得下一行(960dp 寬扣掉兩邊 56dp)
    Box(Modifier.fillMaxHeight().padding(horizontal = 26.dp), contentAlignment = Alignment.Center) {
      Text(label, style = Name.copy(fontSize = 26.sp, color = if (inverted) T.Ink else if (disabled) T.Faint else T.Text))
    }
  }
}

@Composable
private fun Primary(label: String, enabled: Boolean, width: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Focusable(modifier.width(width).height(76.dp), shape = T.Pill, selected = enabled, fill = T.Raised, onClick = onClick) { inverted ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(label, style = Name.copy(fontSize = 30.sp, color = if (inverted) T.Ink else T.Faint))
    }
  }
}

@Composable
private fun Secondary(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Focusable(modifier.height(72.dp), shape = T.Pill, onClick = onClick) { _ ->
    Box(Modifier.fillMaxHeight().padding(horizontal = 36.dp), contentAlignment = Alignment.Center) {
      Text(label, style = Name.copy(fontSize = 26.sp))
    }
  }
}

/** 字首圓圈用的字:「小明」的「小」大家都一樣,取最後一個字 */
private fun initial(name: String) = if (name.length >= 2 && name[0] in "小阿大老") name.last().toString() else name.take(1)

/** 還剩幾分:無條件進位(剩 10 秒也算 1 分) */
private fun mins(ms: Long) = ((ms + 59_999) / 60_000).toInt()

/** 已經看了幾分:無條件捨去,跟爸媽手機頁一致 */
private fun watchedMins(ms: Long) = (ms / 60_000).toInt()

private fun qrBitmap(text: String, px: Int): Bitmap {
  val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px)
  val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
  for (x in 0 until px) for (y in 0 until px) bmp.setPixel(x, y, if (m[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
  return bmp
}
