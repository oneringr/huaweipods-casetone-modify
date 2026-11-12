package oneringr.huaweipods.modify

import android.content.ContextWrapper
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import oneringr.huaweipods.modify.audio.AudioEngine
import oneringr.huaweipods.modify.data.SoundLibrary
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AudioEngineDeviceTest {
    @Test fun videoExtractionTrimGainBothExportsAndLibraryPersistence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "fixture-${UUID.randomUUID()}.mp4")
        instrumentation.context.assets.open("audio-fixture.mp4").use { input -> fixture.outputStream().use { input.copyTo(it) } }
        val engine = AudioEngine(context)
        val decodeStarted = SystemClock.elapsedRealtime()
        val source = engine.decode(fixture, "带音轨视频.mp4")
        val decodeMs = SystemClock.elapsedRealtime() - decodeStarted
        assertTrue(source.durationMs >= 64000)
        assertEquals(44100, source.rate); assertEquals(2, source.channels); assertEquals(900, source.waveform.size)
        val wav = engine.export(source, 2000, 4500, 0.5f, "wav") {}
        val bytes = wav.readBytes()
        assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(44100, ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        assertEquals(44100 * 2 * 2 * 2500 / 1000 + 44, bytes.size)
        val gainWav = engine.export(source, 2000, 4500, 1f, "wav") {}
        val full = gainWav.readBytes()
        val original = source.file.inputStream().use { input ->
            input.skip(2000L * source.rate / 1000 * source.channels * 2)
            ByteArray(full.size - 44).also { data -> java.io.DataInputStream(input).readFully(data) }
        }
        assertArrayEquals(original, full.copyOfRange(44, full.size))
        for (i in 44 until minOf(bytes.size, 4000) step 2) assertTrue(kotlin.math.abs(AudioEngine.shortAt(full, i) / 2 - AudioEngine.shortAt(bytes, i)) <= 1)
        val m4a = engine.export(source, 2000, 4500, 0.5f, "m4a") {}
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(m4a.absolutePath)
            assertEquals(1, extractor.trackCount)
            val fmt = extractor.getTrackFormat(0)
            assertEquals("audio/mp4a-latm", fmt.getString(MediaFormat.KEY_MIME))
            val duration = fmt.getLong(MediaFormat.KEY_DURATION)
            assertTrue("AAC duration $duration", duration in 2400000L..2700000L)
        } finally { extractor.release() }
        val exportStarted = SystemClock.elapsedRealtime()
        val longWav = engine.export(source, 0, source.durationMs, 1f, "wav") {}
        val exportMs = SystemClock.elapsedRealtime() - exportStarted
        assertTrue(longWav.length() > 10000000)
        val tone = engine.tonePcm(source, 2000, 4500, 0.5f)
        assertEquals(80000, tone.size)
        val six = engine.tonePcm(source, 2000, 8000, 1f)
        assertEquals(192000, six.size)
        assertThrows(IllegalArgumentException::class.java) { engine.tonePcm(source, 0, 6001, 1f) }
        val importStarted = SystemClock.elapsedRealtime()
        val imported = engine.import(Uri.fromFile(fixture)) {}
        val importMs = SystemClock.elapsedRealtime() - importStarted
        assertEquals(source.frames, imported.frames)
        assertEquals(source.waveform, imported.waveform)
        assertArrayEquals(tone, engine.tonePcm(imported, 2000, 4500, 0.5f))
        val testDir = File(context.cacheDir, "isolated-library-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir(): File = testDir }
        val library = SoundLibrary(isolated)
        val first = library.add("测试片段", tone, emptyList())
        assertEquals(1, SoundLibrary(isolated).load().size)
        val second = library.add("第二段", six, listOf(first))
        library.save(listOf(second.copy(order = 0), first.copy(order = 1)))
        assertEquals(second.id, library.load().first().id)
        library.delete(second, library.load()); assertEquals(1, library.load().size)
        library.delete(first, library.load()); assertEquals(0, library.load().size)
        val results = File(context.filesDir, "audio-test-results.txt")
        results.writeText("decodeMs=$decodeMs\nimportMs=$importMs\nunityWavExportMs=$exportMs\nsourceMs=${source.durationMs}\nsourcePcmBytes=${source.file.length()}\nwaveformBins=${source.waveform.size}\ntrimWavBytes=${wav.length()}\ntrimM4aBytes=${m4a.length()}\ntoneBytes=${tone.size}\nsixSecondsBytes=${six.size}\nlongWavBytes=${longWav.length()}\nPASS\n")
        imported.file.delete()
        fixture.delete(); source.file.delete(); wav.delete(); gainWav.delete(); m4a.delete(); longWav.delete()
    }
}
