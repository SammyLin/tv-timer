package ai.threemi.tvtimer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * 時間到的音效:10 秒輕快的木琴旋律,程式即時合成(不用放音檔)。
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

    /** 音量(0~1),之前 0.8 太大聲 */
    private const val VOLUME = 0.32f

    /** 旋律:相對 C5 的半音數,null 是休止;每格 0.16 秒,蹦蹦跳跳的五聲音階,一句約 2.9 秒 */
    private val melody: List<Pair<Int?, Int>> = listOf(
      4 to 1, 7 to 1, 12 to 1, null to 1, 9 to 1, 7 to 1, 4 to 2,
      2 to 1, 4 to 1, 7 to 1, null to 1, 9 to 1, 12 to 1, 14 to 3, null to 2,
    )
    private const val BEAT = 0.16

    /** 木琴音色的旋律重複到 10 秒,最後一秒淡出 */
    private val pcm: ShortArray by lazy {
      val n = (RATE * SECONDS).toInt()
      val out = FloatArray(n)
      var t0 = 0.0
      outer@ while (true) {
        for ((note, beats) in melody) {
          if (t0 >= SECONDS - 1.2) break@outer
          if (note != null) {
            val f = 523.25 * 2.0.pow(note / 12.0)
            val start = (t0 * RATE).toInt()
            val len = minOf((1.5 * RATE).toInt(), n - start)
            for (k in 0 until len) {
              val t = k.toDouble() / RATE
              // 木琴:基音 + 很快消失的 3.93 倍、9.2 倍泛音
              val v = minOf(1.0, t / 0.006) * (
                exp(-t * 5) * sin(2 * PI * f * t) +
                  0.18 * exp(-t * 14) * sin(2 * PI * f * 3.93 * t) +
                  0.05 * exp(-t * 30) * sin(2 * PI * f * 9.2 * t))
              out[start + k] += v.toFloat()
            }
          }
          t0 += beats * BEAT
        }
      }
      val peak = out.maxOf { abs(it) }
      val fadeFrom = n - RATE
      ShortArray(n) { k ->
        val fade = if (k > fadeFrom) (n - k).toFloat() / RATE else 1f
        (out[k] / peak * fade * VOLUME * Short.MAX_VALUE).toInt().toShort()
      }
    }
  }
}
