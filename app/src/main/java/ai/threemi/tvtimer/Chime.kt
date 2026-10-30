package ai.threemi.tvtimer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 時間到的音效:10 秒的音樂盒旋律,程式即時合成(不用放音檔)。
 * 播之前先搶「音訊焦點」,YouTube 之類的 App 會因此暫停,不會在鎖定畫面後面繼續出聲。
 */
class Chime(context: Context) {
  private val audio = context.getSystemService(AudioManager::class.java)
  private val attrs = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()
  private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
    .setAudioAttributes(attrs)
    .setOnAudioFocusChangeListener { }
    .build()
  private var track: AudioTrack? = null
  private var holding = false

  /** 讓正在播的影片停下來(鎖起來時用),一直拿著直到 release() */
  fun hush() {
    if (!holding) holding = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
  }

  /** 開始看電視了,把音訊焦點還回去 */
  fun release() {
    stopTrack()
    if (holding) { audio.abandonAudioFocusRequest(focus); holding = false }
  }

  /** 爸媽在手機上試聽:播完把焦點還回去(正在看的影片之後可以繼續) */
  fun playSample() {
    val wasHolding = holding
    playTimeUp()
    if (!wasHolding) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (holding) release() }, 10_500)
  }

  fun playTimeUp() {
    hush()
    stopTrack()
    val pcm = pcm
    track = AudioTrack.Builder()
      .setAudioAttributes(attrs)
      .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
      .setTransferMode(AudioTrack.MODE_STATIC)
      .setBufferSizeInBytes(pcm.size * 2)
      .build()
      .apply { write(pcm, 0, pcm.size); play() }
  }

  private fun stopTrack() {
    track?.runCatching { stop(); release() }
    track = null
  }

  companion object {
    private const val RATE = 44_100
    private const val SECONDS = 10.0

    /** 「C-E-G-C」上行再下行,像音樂盒;每個音 0.42 秒,鈴聲般衰減,最後一秒淡出 */
    private val pcm: ShortArray by lazy {
      val notes = doubleArrayOf(1046.5, 1318.5, 1568.0, 2093.0, 1568.0, 1318.5) // C6 E6 G6 C7 G6 E6
      val step = 0.42
      val n = (RATE * SECONDS).toInt()
      val out = FloatArray(n)
      var t0 = 0.0
      var i = 0
      while (t0 < SECONDS - 0.3) {
        val f = notes[i++ % notes.size]
        val start = (t0 * RATE).toInt()
        val len = minOf((1.6 * RATE).toInt(), n - start)
        for (k in 0 until len) {
          val t = k.toDouble() / RATE
          val env = exp(-t * 3.2) * minOf(1.0, t / 0.004)
          // 基音加一點八度泛音,聽起來比較像鈴
          out[start + k] += (env * (0.6 * sin(2 * PI * f * t) + 0.25 * sin(2 * PI * f * 2 * t) + 0.1 * sin(2 * PI * f * 3.01 * t))).toFloat()
        }
        t0 += step
      }
      val fadeFrom = n - RATE
      ShortArray(n) { k ->
        val fade = if (k > fadeFrom) (n - k).toFloat() / RATE else 1f
        (out[k].coerceIn(-1.4f, 1.4f) / 1.4f * fade * 0.8f * Short.MAX_VALUE).toInt().toShort()
      }
    }
  }
}
