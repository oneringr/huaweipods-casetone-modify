package oneringr.huaweipods.modify

import oneringr.huaweipods.modify.bluetooth.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class MbbCodecTest {
    @Test fun crcKnownVector() { assertEquals(0x31c3, MbbCodec.crc("123456789".toByteArray())) }
    @Test fun framesSurviveArbitraryStreamBoundaries() {
        for (length in listOf(2, 1018, 1019, 2034, 2035, 7680)) {
            val payload = ByteArray(length) { (it * 31).toByte() }
            val results = mutableListOf<ByteArray>()
            val decoder = MbbCodec.Decoder { results.add(it) }
            val all = MbbCodec.frames(payload).fold(byteArrayOf()) { a, b -> a + b }
            all.toList().chunked(37).forEach { decoder.feed(it.toByteArray()) }
            assertEquals(1, results.size); assertArrayEquals(payload, results.single())
        }
    }
    @Test fun corruptFrameDoesNotBecomeValidPayload() {
        val packet = MbbCodec.frame(hex("2bb4010110")); packet[5] = (packet[5].toInt() xor 1).toByte()
        val packets = mutableListOf<ByteArray>()
        val decoder = MbbCodec.Decoder { packets.add(it) }
        decoder.feed(packet); decoder.feed(MbbCodec.frame(hex("2bb4010110")))
        assertEquals(1, packets.size); assertArrayEquals(hex("2bb4010110"), packets.single())
    }
    @Test fun extendedTlvUsesSevenBitLength() {
        val payload = ByteArray(969) { it.toByte() }
        val tlv = MbbCodec.field(2, payload)
        assertEquals(0x87, tlv.u(1)); assertEquals(0x49, tlv.u(2))
        assertArrayEquals(payload, MbbCodec.tlv(tlv, 0)[2])
    }
    @Test fun realDeviceStatusAndPackage() {
        val status = ToneStatus.parse(hex("2bb4010110020c01010f000000000000002b01"))
        assertTrue(status.selected); assertEquals(43, status.toneId); assertEquals(15, status.volume)
        val pcm = ByteArray(27240) { (it * 17).toByte() }
        val packet = MbbCodec.tonePackage(pcm)
        assertEquals(pcm.size + 88, packet.size)
        val body = packet.copyOfRange(36, packet.size - 4)
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(body), packet.copyOfRange(4, 36))
        assertArrayEquals(pcm, body.copyOfRange(48, body.size))
    }
    @Test fun truncatedTlvRejected() {
        assertThrows(IllegalArgumentException::class.java) { MbbCodec.tlv(hex("2bb4020c0101")) }
    }
}
