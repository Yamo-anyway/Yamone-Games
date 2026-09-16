package com.yamone.games.arcadecore

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class SoundSynthesisTest {
    @Test fun musicHasValidWaveHeaders() = MusicScene.entries.forEach {
        val bytes = SoundSynthesis.music(it)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals(22050, buffer.getInt(24))
        assertEquals(1, buffer.getShort(22).toInt())
        assertEquals(16, buffer.getShort(34).toInt())
        assertEquals(bytes.size - 44, buffer.getInt(40))
    }
    @Test fun allFiveScenesAreDifferent() {
        assertEquals(5, MusicScene.entries.map { SoundSynthesis.music(it).contentHashCode() }.distinct().size)
    }
    @Test fun sevenEffectsAreDifferentAndShort() {
        val clips = GameSound.entries.map(SoundSynthesis::effect)
        assertEquals(7, clips.map(ByteArray::contentHashCode).distinct().size)
        assertTrue(clips.all { it.size in 1000..44144 })
    }
    @Test fun tracksAreAudibleAndDoNotClip() {
        (MusicScene.entries.map(SoundSynthesis::music) + GameSound.entries.map(SoundSynthesis::effect)).forEach { bytes ->
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val peak = (44 until bytes.size step 2).maxOf { abs(buffer.getShort(it).toInt()) }
            assertTrue(peak in 100..32000)
        }
    }
    @Test fun generationIsDeterministic() {
        assertArrayEquals(SoundSynthesis.music(MusicScene.HOME), SoundSynthesis.music(MusicScene.HOME))
        assertArrayEquals(SoundSynthesis.effect(GameSound.RECORD), SoundSynthesis.effect(GameSound.RECORD))
    }
}
