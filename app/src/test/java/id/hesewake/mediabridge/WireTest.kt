package id.hesewake.mediabridge

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WireTest {
    @Test fun pairingCodeMatchesDocumentedVector() {
        assertEquals("667512", Wire.pairingCode(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 32).toByte() }, ByteArray(32) { (it + 64).toByte() }))
    }
    @Test fun pairingRejectsWrongNonceSize() {
        assertThrows(IllegalArgumentException::class.java) { Wire.pairingCode(ByteArray(31), ByteArray(32), ByteArray(32)) }
    }
    @Test fun fingerprintMatchesSha256Vector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Wire.fingerprint("abc".toByteArray()))
    }
    @Test fun invalidUtf8IsRejectedBeforeJsonParsing() {
        val bytes = byteArrayOf(0, 0, 0, 1, 0xff.toByte())
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { Wire.read(DataInputStream(ByteArrayInputStream(bytes))) }
    }
    @Test fun framesRejectInvalidSizeBeforeAllocation() {
        for (size in listOf(0, -1, 1_048_577)) {
            val bytes = java.nio.ByteBuffer.allocate(4).putInt(size).array()
            assertThrows(IllegalArgumentException::class.java) { Wire.read(DataInputStream(ByteArrayInputStream(bytes))) }
        }
    }
}
