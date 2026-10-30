package ai.threemi.tvtimer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 從電視主畫面點進來看到的頁面:
 *  - 還沒開「顯示在其他應用程式上層」權限 → 教怎麼開
 *  - 正在看 → 剩幾分、暫停/結束(小孩自己想停的時候)
 *  - 第一次裝好 → 顯示爸媽手機網址和初始密碼
 */
class MainActivity : ComponentActivity() {
  private var canOverlay by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Store.init(this)
    setContent { Screen(canOverlay) }
  }

  override fun onResume() {
    super.onResume()
    canOverlay = Settings.canDrawOverlays(this)
    GuardService.start(this)
  }

  @Composable
  private fun Screen(canOverlay: Boolean) {
    val s by Store.state.collectAsState()
    val session = s.session
    val first = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().background(T.Ground).padding(horizontal = 56.dp, vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text("看電視計時", color = T.Muted, fontSize = 18.sp)
      if (!canOverlay) {
        Text("還差一個權限", color = T.Text, fontSize = 44.sp, fontWeight = FontWeight.SemiBold)
        Text("要開「顯示在其他應用程式上層」,鎖定畫面才蓋得住其他 App。", color = T.Muted, fontSize = 22.sp)
        Text("電腦執行:adb shell appops set ai.threemi.tvtimer SYSTEM_ALERT_WINDOW allow", color = T.Faint, fontSize = 18.sp)
        Pill("打開設定", primary = true, modifier = Modifier.focusRequester(first)) {
          runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        }
      } else {
        if (session != null && session.running) {
          Text(session.kids.joinToString("、").ifEmpty { "全家" } + " 正在看", color = T.Text, fontSize = 44.sp, fontWeight = FontWeight.SemiBold)
          Text(
            if (session.unlimited) "不限制" else "還剩 ${(session.remainingMs + 59_999) / 60_000} 分",
            color = if (session.unlimited) T.Muted else T.Amber, fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Pill("暫停,下次繼續", primary = true, modifier = Modifier.focusRequester(first)) { Store.pause() }
            Pill("結束") { Store.stop() }
          }
        } else {
          Text("鎖定中", color = T.Text, fontSize = 44.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(18.dp))
        Text("爸媽手機(連家裡 Wi-Fi)打開", color = T.Muted, fontSize = 20.sp)
        Text("http://${Net.localIp() ?: "電視IP"}:${BuildConfig.PARENT_PORT}/", color = T.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        if (!Store.isSetupShown) {
          Text("初始爸媽密碼 ${s.config.pin}。在手機頁面改掉之後,這裡就不再顯示。", color = T.Muted, fontSize = 20.sp)
        }
        if (session == null || !session.running) Pill("關閉", primary = true, modifier = Modifier.focusRequester(first)) { finish() }
      }
    }
    LaunchedEffect(canOverlay, session?.running) { runCatching { first.requestFocus() } }
  }

  @Composable
  private fun Pill(label: String, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Text(
      label,
      color = if (primary) T.Ink else T.Text,
      fontSize = 24.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = modifier
        .onFocusChanged { focused = it.isFocused }
        .border(if (focused) 3.dp else 0.dp, if (focused) T.Text else Color.Transparent, shape)
        .padding(if (focused) 6.dp else 0.dp)
        .background(if (primary) T.Text else if (focused) T.Lit else T.Surface, shape)
        .clickable(onClick = onClick)
        .padding(horizontal = 32.dp, vertical = 14.dp),
    )
  }
}
