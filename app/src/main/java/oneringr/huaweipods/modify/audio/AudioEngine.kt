package oneringr.huaweipods.modify.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class AudioSource(val name: String, val file: File, val rate: Int, val channels: Int, val frames: Long, val waveform: List<Float>) {
    val durationMs get() = frames * 1000L / rate
}

/** 长音频以磁盘 PCM 为中间文件；解码、增益和导出均按块处理。 */
class AudioEngine(private val context: Context) {
    private val work = File(context.cacheDir, "audio").apply { mkdirs() }

    fun import(uri: Uri, progress: (Float) -> Unit): AudioSource {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "导入音频"
        val input = File(work, "${UUID.randomUUID()}.source")
        try {
            context.contentResolver.openInputStream(uri)?.use { source -> input.outputStream().use(source::copyTo) }
                ?: error("无法读取所选文件，请重新选择")
            return decode(input, name, progress)
        } finally { input.delete() }
    }

    fun decode(input: File, name: String, progress: (Float) -> Unit = {}): AudioSource {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        val output = File(work, "${UUID.randomUUID()}.pcm")
        try {
            extractor.setDataSource(input.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("此文件没有音轨")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            decoder = codec
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var lastOutput = System.currentTimeMillis()
            FileOutputStream(output).use { stream ->
                while (!outputEnded) {
                    checkCancelled()
                    if (!inputEnded) {
                        val index = codec.dequeueInputBuffer(10000)
                        if (index >= 0) {
                            val buffer = codec.getInputBuffer(index)!!
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val index = codec.dequeueOutputBuffer(info, 10000)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val actual = codec.outputFormat
                            rate = actual.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            encoding = if (actual.containsKey(MediaFormat.KEY_PCM_ENCODING)) actual.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                            require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT) { "解码器输出格式暂不支持" }
                        }
                        index >= 0 -> {
                            val buffer = codec.getOutputBuffer(index)!!
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            buffer.order(ByteOrder.LITTLE_ENDIAN)
                            if (info.size > 0) {
                                val data = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                    val bytes = ByteBuffer.allocate(info.size / 2).order(ByteOrder.LITTLE_ENDIAN)
                                    while (buffer.remaining() >= 4) bytes.putShort((buffer.float.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
                                    bytes.array()
                                } else ByteArray(info.size).also(buffer::get)
                                stream.write(data)
                                if (duration > 0) progress((info.presentationTimeUs.toFloat() / duration).coerceIn(0f, 0.98f))
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(index, false)
                            lastOutput = System.currentTimeMillis()
                        }
                    }
                    require(System.currentTimeMillis() - lastOutput < 30000) { "音频解码超时，请尝试其他文件" }
                }
            }
            require(rate > 0 && channels in 1..8 && output.length() > 0) { "未读取到有效音频" }
            val frames = output.length() / (channels * 2)
            val wave = waveform(output, channels, frames)
            progress(1f)
            return AudioSource(name, output, rate, channels, frames, wave)
        } catch (e: Throwable) { output.delete(); throw e }
        finally { runCatching { decoder?.stop() }; decoder?.release(); extractor.release() }
    }

    private fun waveform(file: File, channels: Int, frames: Long): List<Float> {
        val peaks = FloatArray(900)
        val data = ByteArray(65536 / (channels * 2) * (channels * 2))
        var frame = 0L
        file.inputStream().buffered().use { input ->
            while (true) {
                checkCancelled()
                val count = input.read(data)
                if (count < 0) break
                for (i in 0 until count / (channels * 2)) {
                    val bucket = (frame++ * peaks.size / frames).toInt().coerceIn(0, peaks.lastIndex)
                    var peak = 0f
                    repeat(channels) { ch -> peak = max(peak, abs(shortAt(data, (i * channels + ch) * 2).toInt()) / 32768f) }
                    peaks[bucket] = max(peaks[bucket], peak)
                }
            }
        }
        return peaks.toList()
    }

    fun tonePcm(source: AudioSource, startMs: Long, endMs: Long, gain: Float): ByteArray {
        require(endMs > startMs && endMs - startMs <= 6000) { "开盒音效需裁切为 6 秒以内" }
        val start = startMs * source.rate / 1000
        val end = min(source.frames, endMs * source.rate / 1000)
        val samples = (end - start) * source.channels
        require(samples > 0 && samples * 2 < 64 * 1024 * 1024) { "音频片段格式过大，请降低采样率后重试" }
        val raw = ByteArray((samples * 2).toInt())
        RandomAccessFile(source.file, "r").use { it.seek(start * source.channels * 2); it.readFully(raw) }
        val count = min(96000, ((end - start) * 16000 / source.rate).toInt())
        require(count > 0)
        val result = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        fun mono(frame: Int): Float {
            val offset = frame.coerceIn(0, (end - start).toInt() - 1) * source.channels * 2
            var sample = 0f
            repeat(source.channels) { sample += shortAt(raw, offset + it * 2) }
            return sample / source.channels
        }
        repeat(count) { i ->
            val position = i.toDouble() * source.rate / 16000
            val first = floor(position).toInt()
            val fraction = (position - first).toFloat()
            val sample = mono(first) * (1 - fraction) + mono(first + 1) * fraction
            result.putShort((sample * gain).roundToInt().coerceIn(-32768, 32767).toShort())
        }
        return result.array()
    }

    fun export(source: AudioSource, startMs: Long, endMs: Long, gain: Float, kind: String, progress: (Float) -> Unit): File {
        require(startMs >= 0 && endMs > startMs && endMs <= source.durationMs + 1)
        val output = File(work, "${UUID.randomUUID()}.$kind")
        try {
            if (kind == "wav") writeWav(source, startMs, endMs, gain, output, progress)
            else writeAac(source, startMs, endMs, gain, output, progress)
            progress(1f)
            return output
        } catch (e: Throwable) { output.delete(); throw e }
    }

    private fun writeWav(source: AudioSource, startMs: Long, endMs: Long, gain: Float, output: File, progress: (Float) -> Unit) {
        val size = ((endMs * source.rate / 1000).coerceAtMost(source.frames) - startMs * source.rate / 1000) * source.channels * 2
        require(size <= 0xffffffffL - 36) { "WAV 文件超过 4 GB，请缩短选区" }
        FileOutputStream(output).use { out ->
            out.write(wavHeader(size.toInt(), source.rate, source.channels))
            segment(source, startMs, endMs, gain, { bytes, count, done, total ->
                out.write(bytes, 0, count); progress(done.toFloat() / total)
            })
        }
    }

    private fun writeAac(source: AudioSource, startMs: Long, endMs: Long, gain: Float, output: File, progress: (Float) -> Unit) {
        require(source.channels <= 2) { "M4A 导出支持单声道和双声道；此文件请保存为 WAV" }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, source.rate, source.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, if (source.channels == 1) 128000 else 192000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        var track = -1
        var framesFed = 0L
        var ended = false
        val info = MediaCodec.BufferInfo()
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); encoder.start()
            fun drain(wait: Boolean) {
                while (true) {
                    checkCancelled()
                    val index = encoder.dequeueOutputBuffer(info, if (wait) 10000 else 0)
                    when {
                        index == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!started); track = muxer.addTrack(encoder.outputFormat); muxer.start(); started = true
                        }
                        index >= 0 -> {
                            val bytes = encoder.getOutputBuffer(index)!!
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                                check(started); bytes.position(info.offset); bytes.limit(info.offset + info.size)
                                muxer.writeSampleData(track, bytes, info)
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) ended = true
                            encoder.releaseOutputBuffer(index, false)
                        }
                    }
                }
            }
            segment(source, startMs, endMs, gain, { bytes, count, done, total ->
                var offset = 0
                while (offset < count) {
                    checkCancelled()
                    val index = encoder.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val buffer = encoder.getInputBuffer(index)!!
                        val n = min(buffer.remaining(), count - offset) / (source.channels * 2) * (source.channels * 2)
                        require(n > 0)
                        buffer.put(bytes, offset, n)
                        encoder.queueInputBuffer(index, 0, n, framesFed * 1000000L / source.rate, 0)
                        framesFed += n / (source.channels * 2); offset += n
                    }
                    drain(false)
                }
                progress(done.toFloat() / total)
            })
            var eosQueued = false
            val deadline = System.currentTimeMillis() + 30000
            while (!ended) {
                checkCancelled()
                require(System.currentTimeMillis() < deadline) { "音频编码超时" }
                if (!eosQueued) {
                    val index = encoder.dequeueInputBuffer(10000)
                    if (index >= 0) { encoder.queueInputBuffer(index, 0, 0, framesFed * 1000000L / source.rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM); eosQueued = true }
                }
                drain(true)
            }
        } finally {
            runCatching { encoder.stop() }; encoder.release()
            if (started) runCatching { muxer.stop() }
            muxer.release()
        }
    }

    private fun segment(source: AudioSource, startMs: Long, endMs: Long, gain: Float, write: (ByteArray, Int, Long, Long) -> Unit) {
        val first = startMs * source.rate / 1000
        val last = min(source.frames, endMs * source.rate / 1000)
        val total = (last - first) * source.channels * 2
        require(total > 0)
        val buffer = ByteArray(65536 / (source.channels * 2) * (source.channels * 2))
        RandomAccessFile(source.file, "r").use { input ->
            input.seek(first * source.channels * 2)
            var done = 0L
            while (done < total) {
                checkCancelled()
                val count = min(buffer.size.toLong(), total - done).toInt()
                input.readFully(buffer, 0, count)
                for (i in 0 until count step 2) {
                    val value = (shortAt(buffer, i) * gain).roundToInt().coerceIn(-32768, 32767)
                    buffer[i] = value.toByte(); buffer[i + 1] = (value shr 8).toByte()
                }
                done += count; write(buffer, count, done, total)
            }
        }
    }

    companion object {
        fun applyPcmGain(pcm: ByteArray, gain: Float): ByteArray {
            require(pcm.size % 2 == 0 && gain.isFinite() && gain in 0f..2f)
            if (gain == 1f) return pcm
            return ByteArray(pcm.size).also { output ->
                for (i in pcm.indices step 2) {
                    val sample = (shortAt(pcm, i) * gain).roundToInt().coerceIn(-32768, 32767)
                    output[i] = sample.toByte(); output[i + 1] = (sample shr 8).toByte()
                }
            }
        }
        fun shortAt(bytes: ByteArray, i: Int): Short = (((bytes[i + 1].toInt()) shl 8) or (bytes[i].toInt() and 255)).toShort()
        fun wavHeader(size: Int, rate: Int, channels: Int): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(size + 36).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort())
            .putInt(rate).putInt(rate * channels * 2).putShort((channels * 2).toShort()).putShort(16).put("data".toByteArray()).putInt(size).array()
        fun writeToneWav(pcm: ByteArray, file: File) { file.outputStream().use { it.write(wavHeader(pcm.size, 16000, 1)); it.write(pcm) } }
        private fun checkCancelled() { if (Thread.currentThread().isInterrupted) throw InterruptedException("处理已取消") }
    }
}
