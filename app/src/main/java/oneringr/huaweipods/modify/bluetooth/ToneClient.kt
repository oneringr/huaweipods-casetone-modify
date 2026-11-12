package oneringr.huaweipods.modify.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import java.io.Closeable
import java.io.IOException
import java.util.UUID
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class ToneStatus(val master: Boolean, val enabled: Boolean, val volume: Int, val toneId: Int, val selected: Boolean) {
    companion object {
        fun parse(payload: ByteArray): ToneStatus {
            val values = MbbCodec.tlv(payload)
            require(values[1]?.contentEquals(byteArrayOf(16)) == true) { "耳机没有返回开盒音效状态" }
            val v = values[2] ?: error("耳机没有返回开盒音效设置")
            require(v.size == 12)
            return ToneStatus(v.u(0) == 1, v.u(1) == 1, v.u(2), (v.u(9) shl 8) or v.u(10), v.u(11) == 1)
        }
    }
}

/** 仅使用公开 BluetoothSocket API。连接、查询和下发均不依赖智慧音频。 */
@SuppressLint("MissingPermission")
class ToneClient(private val device: BluetoothDevice, private val trace: (String) -> Unit = {}) : Closeable {
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var failure: Throwable? = null
    @Volatile private var closed = false
    private val received = LinkedBlockingQueue<ByteArray>()
    private var reader: Thread? = null

    fun connect() {
        var connected: BluetoothSocket? = null
        var lastError: IOException? = null
        // 实机在上一条串口刚关闭时会拒绝首次连接。仅为这个已观察到的情况重试一次。
        repeat(2) { attempt ->
            if (connected != null) return@repeat
            checkOpen()
            if (attempt > 0) Thread.sleep(800)
            val candidate = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"))
            socket = candidate
            if (closed || Thread.currentThread().isInterrupted) { candidate.close(); throw InterruptedException() }
            val timer = Timer("bt-connect-timeout", true)
            timer.schedule(object : TimerTask() { override fun run() { runCatching { candidate.close() } } }, 10000)
            try { candidate.connect(); connected = candidate }
            catch (e: IOException) { checkOpen(); lastError = e; runCatching { candidate.close() }; trace("CONNECT_ATTEMPT_FAILED ${attempt + 1}") }
            finally { timer.cancel() }
        }
        val s = connected ?: throw IOException("无法连接耳机。请将双耳入盒并重新开盖，关闭其他占用连接的耳机工具后重试。", lastError)
        val decoder = MbbCodec.Decoder { p ->
            trace("RX " + p.hex())
            received.offer(p)
        }
        reader = thread(name = "tone-bluetooth-reader", isDaemon = true) {
            try {
                val buffer = ByteArray(4096)
                val input = s.inputStream
                while (!Thread.currentThread().isInterrupted) {
                    val count = input.read(buffer)
                    if (count < 0) throw IOException("耳机已断开连接")
                    decoder.feed(buffer.copyOf(count))
                }
            } catch (e: Throwable) { failure = e }
        }
        trace("CONNECTED")
    }

    private fun send(payload: ByteArray, bulk: Boolean = false) {
        if (!bulk) trace("TX " + payload.hex())
        val output = socket?.outputStream ?: error("尚未连接耳机")
        MbbCodec.frames(payload).forEach { output.write(it) }
        output.flush()
    }

    private fun await(timeout: Long = 6000, predicate: (ByteArray) -> Boolean): ByteArray {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            failure?.let { throw IOException("蓝牙连接已中断", it) }
            val packet = received.poll(200, TimeUnit.MILLISECONDS) ?: continue
            if (predicate(packet)) return packet
        }
        throw IOException("等待耳机回执超时，请保持双耳入盒、盒盖打开后重试")
    }

    private fun command(payload: ByteArray, service: Int = payload.u(0), cmd: Int = payload.u(1)): ByteArray {
        send(payload)
        return await { it.size >= 2 && it.u(0) == service && it.u(1) == cmd }
    }

    fun query(): ToneStatus = ToneStatus.parse(command(hex("2bb4010110")))

    private fun checkBox() {
        val inBox = MbbCodec.tlv(command(hex("2b250300")))
        require(inBox[3]?.firstOrNull()?.toInt() == 1 && inBox[4]?.firstOrNull()?.toInt() == 1) { "请将两只耳机放入充电盒" }
        val lid = MbbCodec.tlv(command(hex("2bb102000300")))
        require(lid[2]?.firstOrNull()?.toInt() == 1) { "请打开充电盒盖" }
    }

    private fun protocol(): Int {
        val caps = MbbCodec.tlv(command(hex("2bb30101010e0100150100")))
        fun supported(tag: Int) = caps[tag]?.let { it.size >= 2 && it.u(1) == 1 } == true
        return when { supported(21) -> 2; supported(14) -> 1; else -> error("这款耳机没有报告可用的开盒音效传输能力") }
    }

    private fun setTone(id: Int, selected: Boolean, volume: Int): ToneStatus {
        val setting = byteArrayOf(1, 1, volume.coerceIn(0, 15).toByte()) + ByteArray(6) + be16(id) + byteArrayOf(if (selected) 1 else 0)
        val result = command(hex("2bb4010110") + MbbCodec.field(2, setting))
        val status = ToneStatus.parse(result)
        require(status.enabled && status.master && status.toneId == id && status.selected == selected) { "耳机未确认音效设置，请重试" }
        return status
    }

    fun restoreDefault(): ToneStatus {
        checkBox()
        return setTone(0, false, query().volume)
    }

    fun upload(pcm: ByteArray, progress: (Float, String) -> Unit): ToneStatus {
        require(pcm.isNotEmpty() && pcm.size % 2 == 0 && pcm.size <= 192000) { "开盒音效必须在 6 秒以内" }
        checkBox()
        val before = query()
        val protocol = protocol()
        trace("PROTOCOL $protocol PCM_BYTES ${pcm.size}")
        progress(0f, "准备传输")
        var data = MbbCodec.tonePackage(pcm)
        if (protocol == 1) send(hex("2bb6010101"))
        else send(hex("0901010101020200000301000601040904") + be32(data.size) + hex("0a0100"))
        var chunkSize = 0
        var completed = false
        val start = SystemClock.elapsedRealtime()
        var lastActivity = start
        while (!completed) {
            require(SystemClock.elapsedRealtime() - start < 180000) { "传输时间过长，请重新连接后重试" }
            val p = await(15000) { it.size >= 2 && ((protocol == 1 && it.u(0) == 43 && it.u(1) == 182) || (protocol == 2 && it.u(0) == 9)) }
            require(SystemClock.elapsedRealtime() - lastActivity < 16000)
            lastActivity = SystemClock.elapsedRealtime()
            val fields = MbbCodec.tlv(p)
            val type = if (protocol == 1) fields[1]?.firstOrNull()?.toInt() ?: 0 else p.u(1)
            when (type) {
                1 -> {
                    val result = if (protocol == 1) fields[2]?.intBE() else fields[127]?.intBE()
                    require(result == 100000) { errorText(result ?: -1) }
                    val mcu = if (protocol == 1) fields[3]?.let { byteArrayOf(0, 0) + it } else fields[11]?.let { byteArrayOf(0, 0) + it }
                    if (protocol == 1) {
                        require(mcu?.size == 4) { "耳机没有返回正确的音效格式" }
                        data = MbbCodec.tonePackage(pcm, mcu = mcu)
                    }
                    setTone(0, false, before.volume)
                    if (protocol == 1) send(hex("2bb60101020204") + be32(data.size) + hex("030100"))
                    else send(hex("0902"))
                }
                2 -> if (protocol == 2) {
                    val bytes = fields[3] ?: error("耳机没有返回分包大小")
                    require(bytes.size == 2)
                    chunkSize = ((bytes.u(0) shl 8) or bytes.u(1)) - 9
                    require(chunkSize in 1..16300)
                    send(hex("0909010101"))
                }
                3 -> {
                    val offsetBytes = fields[if (protocol == 1) 2 else 1] ?: error("缺少传输偏移")
                    val offset = offsetBytes.intBE()
                    if (protocol == 1) {
                        val bytes = fields[5] ?: error("缺少分包大小")
                        require(bytes.size == 2)
                        chunkSize = (bytes.u(0) shl 8) or bytes.u(1)
                    }
                    require(chunkSize in 1..16300 && offset in 0..data.size) { "耳机请求了无效数据范围" }
                    val bitmap = fields[if (protocol == 1) 4 else 3]?.firstOrNull()?.toInt()?.and(255) ?: error("缺少分包回执")
                    repeat(8) { i ->
                        val at = offset.toLong() + i.toLong() * chunkSize
                        if (bitmap and (1 shl i) == 0 && at < data.size) {
                            val chunk = data.copyOfRange(at.toInt(), minOf(at.toInt() + chunkSize, data.size))
                            if (protocol == 1) {
                                val packet = hex("ff") + offsetBytes + be16(chunk.size) + byteArrayOf(i.toByte()) + chunk
                                send(hex("2bb6010104") + MbbCodec.field(2, packet), true)
                            } else send(hex("0904ff") + offsetBytes + byteArrayOf(i.toByte()) + chunk, true)
                        }
                    }
                    progress((offset.toFloat() / data.size).coerceIn(0f, 0.99f), "正在传输")
                }
                5 -> {
                    if (protocol == 2) {
                        val total = fields[1]?.intBE() ?: 0
                        val received = fields[2]?.intBE() ?: 0
                        if (total > 0) {
                            progress((received.toFloat() / total).coerceIn(0f, 0.99f), "正在写入充电盒")
                            if (received >= total) completed = true
                        }
                    } else {
                        val update = fields[2]
                        if (update != null && update.size >= 2) progress(update.u(1) / 100f, if (update.u(0) == 2) "正在写入充电盒" else "正在传输")
                    }
                }
                6 -> if (protocol == 1) {
                    val result = fields[2]?.intBE() ?: -1
                    require(result == 1) { errorText(result) }
                    completed = true
                }
                7 -> error(errorText((if (protocol == 1) fields[2] else fields[127])?.intBE() ?: -1))
            }
        }
        progress(0.99f, "确认音效")
        Thread.sleep(1000)
        val status = setTone(43, true, before.volume)
        progress(1f, "已应用")
        return status
    }

    private fun errorText(code: Int) = when(code) {
        100010, 109012 -> "请将两只耳机放入充电盒"
        100011, 109002, 109024 -> "充电盒电量不足，请充电后重试"
        100012, 109006 -> "耳机正在更新，请等待更新完成"
        100013 -> "耳机正在传输其他数据，请稍后重试"
        109022 -> "耳机正在录音，请结束录音后重试"
        else -> "耳机拒绝了音效传输（$code）"
    }

    override fun close() {
        closed = true
        runCatching { socket?.close() }
        reader?.interrupt()
        socket = null
    }
    private fun checkOpen() { if (closed || Thread.currentThread().isInterrupted) throw InterruptedException() }
}
