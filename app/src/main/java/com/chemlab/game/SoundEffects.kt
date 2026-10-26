package com.chemlab.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 程序化音效：用 AudioTrack 实时合成短音效，无需任何音频资源文件，
 * 契合"纯代码、零第三方资源"的低功耗手表设计。
 *
 * 覆盖四种实验室音效：
 *  - SOLID  加入固体（金属撞击玻璃的清脆"叮"声）
 *  - LIQUID 加入液体（倒水的流动声）
 *  - GAS    加入气体（喷气的嘶嘶声）
 *  - FLAME  加热 / 点燃（火焰燃烧的噼啪声）
 */
object SoundEffects {

    enum class Kind { SOLID, LIQUID, GAS, FLAME }

    private const val RATE = 22050

    fun play(kind: Kind, enabled: Boolean) {
        if (!enabled) return
        val pcm = when (kind) {
            Kind.SOLID -> solid()
            Kind.LIQUID -> liquid()
            Kind.GAS -> gas()
            Kind.FLAME -> flame()
        }
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (_: Exception) {
            return
        }
        thread(start = true, name = "chemlab-sfx") {
            try {
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(pcm.size * 1000L / RATE + 80)
                track.release()
            } catch (_: Exception) {
                // 音频资源不可用时静默忽略，不影响主流程
            }
        }
    }

    private fun s(v: Double): Short = (v.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()

    /** 攻击/释放信封：x 在 (0,1) 内线性，越界截断 */
    private fun env01(x: Double): Double = when {
        x <= 0.0 -> 0.0
        x >= 1.0 -> 1.0
        else -> x
    }

    /** 金属撞玻璃：明亮高频泛音的快速衰减 + 初始冲击噪声 */
    private fun solid(): ShortArray {
        val dur = 0.30
        val n = (RATE * dur).toInt()
        val out = ShortArray(n)
        val freqs = doubleArrayOf(2093.0, 3136.0, 4186.0) // C7 / G7 / C8 明亮泛音
        for (i in 0 until n) {
            val t = i / RATE.toDouble()
            var v = 0.0
            for ((k, f) in freqs.withIndex()) {
                val amp = 0.22 * exp(-7.0 * t * (1.0 + k * 0.18))
                v += amp * sin(2.0 * PI * f * t)
            }
            v += if (t < 0.012) 0.5 * (1.0 - t / 0.012) * (Math.random() * 2.0 - 1.0) else 0.0
            out[i] = s(v)
        }
        return out
    }

    /** 倒水：低通滤波噪声，模拟水流 */
    private fun liquid(): ShortArray {
        val dur = 0.42
        val n = (RATE * dur).toInt()
        val out = ShortArray(n)
        var lp = 0.0
        for (i in 0 until n) {
            val t = i / RATE.toDouble()
            val env = env01(t / 0.07) * env01((dur - t) / 0.10)
            val white = Math.random() * 2.0 - 1.0
            lp += 0.14 * (white - lp)
            out[i] = s(lp * env * 1.1)
        }
        return out
    }

    /** 喷气：高通噪声，模拟气体喷出的嘶嘶声 */
    private fun gas(): ShortArray {
        val dur = 0.32
        val n = (RATE * dur).toInt()
        val out = ShortArray(n)
        var lp = 0.0
        for (i in 0 until n) {
            val t = i / RATE.toDouble()
            val env = env01(t / 0.04) * env01((dur - t) / 0.09)
            val white = Math.random() * 2.0 - 1.0
            lp += 0.04 * (white - lp) // 极低截止，残留中高频"嘶嘶"成分
            val hp = white - lp
            out[i] = s(hp * env * 0.85)
        }
        return out
    }

    /** 火焰燃烧：低频隆隆 + 中频噪声 + 随机噼啪声 */
    private fun flame(): ShortArray {
        val dur = 0.55
        val n = (RATE * dur).toInt()
        val out = ShortArray(n)
        var lp = 0.0
        for (i in 0 until n) {
            val t = i / RATE.toDouble()
            val env = env01(t / 0.06) * env01((dur - t) / 0.12)
            val rumble = 0.35 * sin(2.0 * PI * 55.0 * t) + 0.25 * sin(2.0 * PI * 110.0 * t)
            val white = Math.random() * 2.0 - 1.0
            lp += 0.06 * (white - lp)
            val crackle = if (Math.random() < 0.09) (Math.random() * 2.0 - 1.0) * 0.7 else 0.0
            out[i] = s((lp * 0.5 + rumble * 0.22 + crackle) * env)
        }
        return out
    }
}