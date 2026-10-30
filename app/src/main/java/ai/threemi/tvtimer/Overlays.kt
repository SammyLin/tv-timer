package ai.threemi.tvtimer

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** 鎖定畫面(全螢幕、吃遙控器按鍵)跟角落的倒數提醒(不擋操作) */
class Overlays(private val context: Context) {
  private val wm = context.getSystemService(WindowManager::class.java)
  private val owner = OverlayOwner()

  private val lock = compose { LockScreen() }
  private val chip = compose { CountdownChip() }
  private var lockShown = false
  private var chipShown = false

  fun render(s: State) {
    val watching = s.session?.running == true && !s.timeUp && s.pendingApproval == null
    // 計時中一直顯示剩幾分;快到時間(warnMinutes)才變大、變琥珀色、出現秒數
    val timed = watching && s.session!!.unlimited.not()
    setLock(!watching)
    setChip(timed)
  }

  fun removeAll() {
    setLock(false)
    setChip(false)
  }

  private fun setLock(show: Boolean) {
    if (show == lockShown) return
    lockShown = show
    if (show) {
      wm.addView(lock, params(full = true))
      lock.requestFocus()
    } else wm.removeView(lock)
  }

  private fun setChip(show: Boolean) {
    if (show == chipShown) return
    chipShown = show
    if (show) wm.addView(chip, params(full = false)) else wm.removeView(chip)
  }

  private fun params(full: Boolean): WindowManager.LayoutParams {
    val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    return if (full) WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
      PixelFormat.OPAQUE,
    ) else WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
      // 不加 FLAG_NOT_TOUCHABLE:Android 12 起會把「點不到」的浮層強制變成 80% 透明
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.END
      x = 24
      y = 24
    }
  }

  private fun compose(content: @Composable () -> Unit): View = ComposeView(context).apply {
    setViewTreeLifecycleOwner(owner)
    setViewTreeSavedStateRegistryOwner(owner)
    setViewTreeViewModelStoreOwner(owner)
    setContent(content)
  }
}

/** Compose 要掛在 Activity 以外的視窗,得自己給它 Lifecycle 等等 */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
  private val registry = LifecycleRegistry(this)
  private val saved = SavedStateRegistryController.create(this)
  override val lifecycle: Lifecycle get() = registry
  override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
  override val viewModelStore = ViewModelStore()

  init {
    saved.performRestore(null)
    registry.currentState = Lifecycle.State.RESUMED
  }
}
