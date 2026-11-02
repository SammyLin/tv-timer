package ai.threemi.tvtimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 一直在背景跑:
 *  - 沒在計時的時候,用蓋在所有 App 上面的畫面(Overlay)把電視鎖住,只能選人、選時間
 *  - 計時中每秒扣時間,剩 warnMinutes 分鐘在角落提醒,時間到再鎖起來
 *  - 電視關掉(待機)就暫停,下次開機可以「繼續上次」
 *  - 時間到播 10 秒木琴音效,鎖起來時讓後面的影片暫停
 *  - 開一個小網頁給爸媽手機用(同意不限制、加時間、改設定)
 */
class GuardService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private lateinit var overlays: Overlays
  private lateinit var chime: Chime
  private var server: ParentServer? = null

  private val screenReceiver = object : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
      when (i.action) {
        Intent.ACTION_SCREEN_OFF -> Store.pause()
        Intent.ACTION_SCREEN_ON -> overlays.render(Store.state.value)
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    Store.init(this)
    startInForeground()
    overlays = Overlays(this)
    chime = Chime(this)
    registerReceiver(screenReceiver, IntentFilter().apply {
      addAction(Intent.ACTION_SCREEN_OFF)
      addAction(Intent.ACTION_SCREEN_ON)
    })
    server = ParentServer(this, BuildConfig.PARENT_PORT) { chime.playSample() }.also { it.start() }

    // 更新等確認時要收起鎖定畫面,確認完(或放棄)再蓋回去
    scope.launch { Updater.status.collectLatest { overlays.render(Store.state.value) } }
    scope.launch {
      var wasWatching: Boolean? = null
      Store.state.collectLatest {
        overlays.render(it)
        // 鎖起來時拿著音訊焦點(後面的影片會暫停);開始看了再還回去。
        // 只在切換時動,不然看電視時每秒的計時更新會把試聽音效停掉
        val watching = it.session?.running == true && !it.timeUp && it.pendingApproval == null
        if (watching != wasWatching) {
          if (watching) chime.release() else chime.hush()
          wasWatching = watching
        }
      }
    }
    scope.launch {
      var last = SystemClock.elapsedRealtime()
      while (true) {
        delay(1000)
        val now = SystemClock.elapsedRealtime()
        Store.rolloverIfNeeded()
        if (Store.tick(now - last)) chime.playTimeUp()
        last = now
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

  override fun onDestroy() {
    scope.cancel()
    unregisterReceiver(screenReceiver)
    server?.stop()
    overlays.removeAll()
    chime.release()
    History.flush()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun startInForeground() {
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel("guard", "看電視計時", NotificationManager.IMPORTANCE_MIN))
    val n = Notification.Builder(this, "guard")
      .setSmallIcon(R.drawable.icon)
      .setContentTitle("看電視計時")
      .setOngoing(true)
      .build()
    if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    else startForeground(1, n)
  }

  companion object {
    fun start(context: Context) {
      if (!Settings.canDrawOverlays(context)) return
      context.startForegroundService(Intent(context, GuardService::class.java))
    }
  }
}

class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) = GuardService.start(context)
}

/**
 * 給電腦上的安裝程式用(adb):忘記爸媽密碼時重設。
 *   adb shell am broadcast -a ai.threemi.tvtimer.RESET_PIN -n ai.threemi.tvtimer/.AdminReceiver
 * Manifest 要求 DUMP 權限,只有 adb shell / 系統送得進來,電視上其他 App 碰不到。
 * 新密碼放在 result data,am broadcast 會印出來。
 */
class AdminReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != "ai.threemi.tvtimer.RESET_PIN") return
    Store.init(context)
    val pin = Store.resetPin()
    setResult(1, pin, null)
    GuardService.start(context)
  }
}
