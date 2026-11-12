package oneringr.huaweipods.modify

import oneringr.huaweipods.modify.audio.AudioEngine
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmGainTest {
    private fun pcm(vararg samples: Int) = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
        samples.forEach { putShort(it.toShort()) }
    }.array()

    @Test fun presetGainAttenuatesAmplifiesAndClipsWithoutChangingTheSource() {
        val original = pcm(-32768, -20000, -1000, 0, 1000, 20000, 32766)
        val backup = original.copyOf()
        assertArrayEquals(pcm(-16384, -10000, -500, 0, 500, 10000, 16383), AudioEngine.applyPcmGain(original, .5f))
        assertArrayEquals(pcm(-32768, -32768, -2000, 0, 2000, 32767, 32767), AudioEngine.applyPcmGain(original, 2f))
        assertArrayEquals(ByteArray(original.size), AudioEngine.applyPcmGain(original, 0f))
        assertArrayEquals(backup, AudioEngine.applyPcmGain(original, 1f))
        assertArrayEquals(backup, original)
    }
}
