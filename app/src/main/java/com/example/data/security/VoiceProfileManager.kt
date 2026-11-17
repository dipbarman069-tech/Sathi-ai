package com.example.data.security

import android.content.Context
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device speaker voiceprint extractor using 40-band log-spectrum features (100-4000 Hz)
 * over 16 kHz voiced frames. Stored encrypted via LocalCryptoManager.
 */
object VoiceProfileManager {
  const val SAMPLE_RATE = 16000
  const val FRAME_SIZE = 512
  private const val BANDS = 40
  const val VOICED_RMS_THRESHOLD = 650.0
  const val MATCH_THRESHOLD = 0.86

  fun computeRms(buffer: ShortArray, count: Int): Double {
    if (count <= 0) return 0.0
    var sum = 0.0
    for (i in 0 until count) {
      val sample = buffer[i].toDouble()
      sum += sample * sample
    }
    return sqrt(sum / count)
  }

  private fun fft(re: DoubleArray, im: DoubleArray) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
      var bit = n shr 1
      while (j and bit != 0) {
        j = j xor bit
        bit = bit shr 1
      }
      j = j xor bit
      if (i < j) {
        val tr = re[i]
        re[i] = re[j]
        re[j] = tr
        val ti = im[i]
        im[i] = im[j]
        im[j] = ti
      }
    }
    var len = 2
    while (len <= n) {
      val angle = -2.0 * PI / len
      for (i in 0 until n step len) {
        for (k in 0 until len / 2) {
          val wr = cos(angle * k)
          val wi = sin(angle * k)
          val ur = re[i + k]
          val ui = im[i + k]
          val vr = re[i + k + len / 2] * wr - im[i + k + len / 2] * wi
          val vi = re[i + k + len / 2] * wi + im[i + k + len / 2] * wr
          re[i + k] = ur + vr
          im[i + k] = ui + vi
          re[i + k + len / 2] = ur - vr
          im[i + k + len / 2] = ui - vi
        }
      }
      len = len shl 1
    }
  }

  fun extractFrameFeatures(buffer: ShortArray): DoubleArray {
    val re = DoubleArray(FRAME_SIZE) { i -> buffer[i] * (0.5 - 0.5 * cos(2.0 * PI * i / (FRAME_SIZE - 1))) }
    val im = DoubleArray(FRAME_SIZE)
    fft(re, im)
    val hzPerBin = SAMPLE_RATE.toDouble() / FRAME_SIZE
    return DoubleArray(BANDS) { b ->
      val lo = (100.0 * 40.0.pow(b.toDouble() / BANDS) / hzPerBin).toInt().coerceAtLeast(1)
      val hi = (100.0 * 40.0.pow((b + 1.0) / BANDS) / hzPerBin).toInt().coerceAtLeast(lo + 1)
      var energy = 0.0
      for (k in lo until hi.coerceAtMost(FRAME_SIZE / 2)) {
        energy += re[k] * re[k] + im[k] * im[k]
      }
      10.0 * log10(energy / (hi - lo) + 1e-3)
    }
  }

  class Accumulator {
    private val sums = DoubleArray(BANDS)
    var frameCount = 0
      private set

    fun add(features: DoubleArray) {
      for (i in 0 until BANDS) {
        sums[i] += features[i]
      }
      frameCount++
    }

    fun average(): DoubleArray = DoubleArray(BANDS) { sums[it] / frameCount.coerceAtLeast(1) }
  }

  fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
    if (a.size != b.size || a.isEmpty()) return 0.0
    val ma = a.average()
    val mb = b.average()
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (i in a.indices) {
      val da = a[i] - ma
      val db = b[i] - mb
      dot += da * db
      normA += da * da
      normB += db * db
    }
    return dot / (sqrt(normA * normB) + 1e-9)
  }

  private const val PREF_FILE = "saathi_secure_voice"
  private const val KEY_PROFILE = "voiceprint_enc"

  fun saveProfile(context: Context, vector: DoubleArray) {
    val serialized = vector.joinToString(",")
    val encrypted = LocalCryptoManager.encrypt(serialized)
    context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().putString(KEY_PROFILE, encrypted).apply()
  }

  fun loadProfile(context: Context): DoubleArray? {
    val raw = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getString(KEY_PROFILE, null) ?: return null
    val decrypted = LocalCryptoManager.decrypt(raw)
    return decrypted.split(",").mapNotNull { it.toDoubleOrNull() }.takeIf { it.size == BANDS }?.toDoubleArray()
  }

  fun clearProfile(context: Context) {
    context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().remove(KEY_PROFILE).apply()
  }
}
