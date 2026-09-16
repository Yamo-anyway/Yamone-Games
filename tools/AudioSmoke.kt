import com.yamone.games.arcadecore.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import kotlin.math.abs
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "/tmp/yamone-audio").apply { mkdirs() }
    val tracks = MusicScene.entries.map { it.name to SoundSynthesis.music(it) } +
        GameSound.entries.map { it.name to SoundSynthesis.effect(it) }
    require(tracks.map { it.second.contentHashCode() }.distinct().size == tracks.size)
    tracks.forEach { (name, bytes) ->
        require(String(bytes,0,4) == "RIFF" && String(bytes,8,4) == "WAVE")
        val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(b.getInt(24)==SoundSynthesis.SAMPLE_RATE && b.getShort(22).toInt()==1)
        require(b.getInt(40)==bytes.size-44)
        var peak=0; var nonZero=0
        for (i in 44 until bytes.size step 2) { val v=abs(b.getShort(i).toInt());peak=maxOf(peak,v);if(v>0)nonZero++ }
        require(peak in 100..32000 && nonZero>100)
        require(SoundSynthesis.SAMPLE_RATE==22050)
        File(out,"$name.wav").writeBytes(bytes)
        println("PASS $name: ${(bytes.size-44)/44100.0}s; peak=$peak; PCM16 mono")
    }
    println("PASS: 5 scene tracks + 7 effects; valid headers, non-silence, bounded peaks, distinct content.")
}
