package oneringr.huaweipods.modify.bluetooth

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

fun hex(value: String): ByteArray = value.filterNot(Char::isWhitespace).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
fun ByteArray.u(index: Int) = this[index].toInt() and 255
fun be16(n: Int) = byteArrayOf((n ushr 8).toByte(), n.toByte())
fun be32(n: Int) = ByteBuffer.allocate(4).putInt(n).array()
fun ByteArray.intBE(): Int { require(size == 4); return ByteBuffer.wrap(this).int }

object MbbCodec {
    fun crc(data: ByteArray): Int {
        var crc = 0
        data.forEach { value ->
            crc = crc xor ((value.toInt() and 255) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xffff
        }
        return crc
    }

    fun frame(payload: ByteArray, control: Int = 0, sequence: Int = 0): ByteArray {
        val body = byteArrayOf(control.toByte()) + (if (control == 0) byteArrayOf() else byteArrayOf(sequence.toByte())) + payload
        val packet = byteArrayOf(0x5a) + be16(body.size) + body
        return packet + be16(crc(packet))
    }

    fun frames(payload: ByteArray): List<ByteArray> {
        if (payload.size <= 1018) return listOf(frame(payload))
        val chunks = payload.toList().chunked(1017)
        return chunks.mapIndexed { i, bytes -> frame(bytes.toByteArray(), when(i) { 0 -> 1; chunks.lastIndex -> 3; else -> 2 }, i) }
    }

    fun tlv(data: ByteArray, start: Int = 2): Map<Int, ByteArray> {
        val result = linkedMapOf<Int, ByteArray>()
        var at = start
        while (at < data.size) {
            require(at + 2 <= data.size) { "设备返回的字段不完整" }
            val tag = data.u(at++)
            var length = data.u(at++)
            if (length and 128 != 0) {
                require(at < data.size)
                length = ((length and 127) shl 7) + (data.u(at++) and 127)
            }
            require(at + length <= data.size) { "设备返回的字段长度不正确" }
            result[tag] = data.copyOfRange(at, at + length)
            at += length
        }
        return result
    }

    fun field(tag: Int, bytes: ByteArray): ByteArray {
        require(bytes.size <= 16383)
        val length = if (bytes.size < 128) byteArrayOf(bytes.size.toByte()) else byteArrayOf(((bytes.size ushr 7) or 128).toByte(), (bytes.size and 127).toByte())
        return byteArrayOf(tag.toByte()) + length + bytes
    }

    /** 匹配官方 RbtPackageUtil。音频包 SHA 覆盖 48 字节音频头和 PCM。 */
    fun tonePackage(pcm: ByteArray, name: String = "Whistle", mcu: ByteArray = ByteArray(4)): ByteArray {
        require(pcm.isNotEmpty() && pcm.size % 2 == 0 && pcm.size <= 192000)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        require(nameBytes.size <= 32 && mcu.size == 4)
        val nameField = nameBytes.copyOf(32)
        val desc = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).putShort(15470).put(hex("00100001")).putInt(pcm.size / 16).array()
        val header = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(crc(desc + nameField).toShort()).put(desc).putInt(16000).put(nameField).array()
        val body = header + pcm
        return be32(body.size) + MessageDigest.getInstance("SHA-256").digest(body) + body + mcu
    }

    class Decoder(private val onPacket: (ByteArray) -> Unit) {
        private var pending = byteArrayOf()
        private var fragments: ByteArrayOutputStream? = null
        private var nextSequence = 0
        fun feed(bytes: ByteArray) {
            pending += bytes
            while (pending.size >= 3) {
                if (pending.u(0) != 0x5a) { pending = pending.copyOfRange(1, pending.size); continue }
                val size = ((pending.u(1) shl 8) or pending.u(2)) + 5
                if (size < 6 || size > 65540) { pending = pending.copyOfRange(1, pending.size); continue }
                if (pending.size < size) return
                val packet = pending.copyOfRange(0, size)
                pending = pending.copyOfRange(size, pending.size)
                if (crc(packet) != 0) { fragments = null; continue }
                when (packet.u(3)) {
                    0 -> { fragments = null; onPacket(packet.copyOfRange(4, size - 2)) }
                    1 -> if (size >= 7 && packet.u(4) == 0) {
                        fragments = ByteArrayOutputStream().apply { write(packet, 5, size - 7) }; nextSequence = 1
                    }
                    2, 3 -> {
                        val buffer = fragments ?: continue
                        if (size < 7 || packet.u(4) != nextSequence) { fragments = null; continue }
                        nextSequence++
                        buffer.write(packet, 5, size - 7)
                        if (packet.u(3) == 3) { fragments = null; onPacket(buffer.toByteArray()) }
                    }
                }
            }
        }
    }
}
