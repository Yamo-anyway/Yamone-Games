package com.yamone.games.arcadecore

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Small, offline, generated musical phrases. No network, sampled songs, or external sound packs. */
enum class MusicScene(val label: String, val bpm: Int) {
    HOME("친구들의 놀이터", 96), SUDOKU("포근한 생각", 72),
    ICE("반짝이는 빙하", 112), FISH("바닷속 산책", 104), SNOW("눈꽃 달리기", 124)
}
enum class GameSound { TAP, START, COLLECT, JUMP, ERROR, FINISH, RECORD }
enum class MusicStyle(val label: String) { AUTO("게임별 자동"), CALM("잔잔하게"), CHEERFUL("경쾌하게") }
enum class HapticStrength(val label: String) { LIGHT("가볍게"), NORMAL("보통") }

object SoundSynthesis {
    const val SAMPLE_RATE = 22050
    private fun frequency(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    fun music(scene: MusicScene): ByteArray {
        val beat = 60.0 / scene.bpm
        val beats = 32
        val data = DoubleArray((beats * beat * SAMPLE_RATE).roundToInt())
        val melody = when (scene) {
            MusicScene.HOME -> intArrayOf(72, 76, 79, 76, 74, 79, 81, 79, 76, 72, 74, 76, 79, 76, 74, 72)
            MusicScene.SUDOKU -> intArrayOf(72, 0, 76, 79, 0, 76, 74, 0, 69, 72, 0, 76, 74, 0, 72, 0)
            MusicScene.ICE -> intArrayOf(79, 84, 83, 79, 76, 79, 81, 84, 83, 79, 76, 74, 76, 79, 74, 72)
            MusicScene.FISH -> intArrayOf(76, 79, 81, 79, 74, 76, 79, 0, 72, 76, 79, 81, 79, 76, 74, 0)
            MusicScene.SNOW -> intArrayOf(84, 79, 81, 84, 83, 79, 76, 79, 81, 76, 79, 81, 79, 76, 74, 72)
        }
        val roots = intArrayOf(48, 45, 53, 55)
        repeat(beats) { step ->
            val midi = melody[step % melody.size]
            if (midi > 0) addNote(data, step * beat, beat * .82, midi, .17, bell = true)
            if (step % 2 == 0) {
                val root = roots[(step / 8) % roots.size]
                addNote(data, step * beat, beat * 1.75, root, .09)
                addNote(data, step * beat, beat * 1.55, root + 7, .045)
                addNote(data, step * beat, beat * 1.45, root + 12, .04)
            }
            if (scene != MusicScene.SUDOKU && step % 2 == 1) {
                addNote(data, step * beat, .055, 91, .015, bell = true)
            }
        }
        // Last note ends before the loop boundary; smooth fades prevent a click at wraparound.
        return wav(data)
    }

    fun effect(sound: GameSound): ByteArray {
        val notes = when (sound) {
            GameSound.TAP -> intArrayOf(84)
            GameSound.START -> intArrayOf(72, 76, 79)
            GameSound.COLLECT -> intArrayOf(84, 88)
            GameSound.JUMP -> intArrayOf(76, 84)
            GameSound.ERROR -> intArrayOf(55, 52)
            GameSound.FINISH -> intArrayOf(76, 74, 72)
            GameSound.RECORD -> intArrayOf(72, 76, 79, 84, 88)
        }
        val step = if (sound == GameSound.TAP) .07 else .09
        val data = DoubleArray(((notes.size * step + .14) * SAMPLE_RATE).toInt())
        notes.forEachIndexed { i, midi -> addNote(data, i * step, step + .10, midi, .29, true) }
        return wav(data)
    }

    private fun addNote(out: DoubleArray, start: Double, duration: Double, midi: Int, gain: Double, bell: Boolean = false) {
        val offset = (start * SAMPLE_RATE).toInt()
        val count = (duration * SAMPLE_RATE).toInt()
        val f = frequency(midi)
        for (i in 0 until count) {
            val at = offset + i
            if (at !in out.indices) break
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = min(1.0, t / .012) * exp(-t * if (bell) 6.0 else 2.8) * min(1.0, (duration - t) / .06).coerceAtLeast(0.0)
            val wave = sin(2 * PI * f * t) + (if (bell) .24 else .10) * sin(2 * PI * 2 * f * t)
            out[at] += wave * gain * envelope
        }
    }

    private fun wav(samples: DoubleArray): ByteArray {
        val bytes = samples.size * 2
        val out = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII)); out.putInt(36 + bytes)
        out.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); out.putInt(16)
        out.putShort(1); out.putShort(1); out.putInt(SAMPLE_RATE); out.putInt(SAMPLE_RATE * 2)
        out.putShort(2); out.putShort(16); out.put("data".toByteArray(Charsets.US_ASCII)); out.putInt(bytes)
        val edge = (SAMPLE_RATE * .008).toInt()
        samples.forEachIndexed { i, sample ->
            val fade = min(1.0, min(i.toDouble() / edge, (samples.lastIndex - i).toDouble() / edge)).coerceAtLeast(0.0)
            out.putShort((sample.coerceIn(-.95, .95) * fade * 32767).roundToInt().toShort())
        }
        return out.array()
    }
}
