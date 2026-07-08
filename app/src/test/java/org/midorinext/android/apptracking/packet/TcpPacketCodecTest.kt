package org.midorinext.android.apptracking.packet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TcpPacketCodecTest {

    @Test
    fun createsResetAckResponseForClientPacket() {
        val requestPayload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val request = buildIpv4TcpPacket(
            sourceIp = byteArrayOf(10, 22, 0, 2),
            destinationIp = byteArrayOf(172.toByte(), 217.toByte(), 168.toByte(), 174.toByte()),
            sourcePort = 40412,
            destinationPort = 443,
            sequenceNumber = 1000,
            acknowledgementNumber = 2000,
            flags = 0x18, // PSH + ACK
            payload = requestPayload
        )

        val reset = TcpPacketCodec.createTcpResetResponse(request, request.size)

        assertNotNull(reset)
        reset ?: return

        assertEquals(172, reset[12].toInt() and 0xFF)
        assertEquals(217, reset[13].toInt() and 0xFF)
        assertEquals(168, reset[14].toInt() and 0xFF)
        assertEquals(174, reset[15].toInt() and 0xFF)

        assertEquals(10, reset[16].toInt() and 0xFF)
        assertEquals(22, reset[17].toInt() and 0xFF)
        assertEquals(0, reset[18].toInt() and 0xFF)
        assertEquals(2, reset[19].toInt() and 0xFF)

        val tcpStart = 20
        val responseSourcePort = readUnsignedShort(reset, tcpStart)
        val responseDestinationPort = readUnsignedShort(reset, tcpStart + 2)
        assertEquals(443, responseSourcePort)
        assertEquals(40412, responseDestinationPort)

        val flags = reset[tcpStart + 13].toInt() and 0xFF
        assertEquals(0x14, flags) // RST + ACK

        val responseSequence = readUnsignedInt(reset, tcpStart + 4)
        val responseAcknowledgement = readUnsignedInt(reset, tcpStart + 8)
        assertEquals(2000L, responseSequence)
        assertEquals(1004L, responseAcknowledgement)
    }

    private fun buildIpv4TcpPacket(
        sourceIp: ByteArray,
        destinationIp: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        sequenceNumber: Long,
        acknowledgementNumber: Long,
        flags: Int,
        payload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val tcpHeaderLen = 20
        val totalLength = ipHeaderLen + tcpHeaderLen + payload.size
        val packet = ByteArray(totalLength)

        packet[0] = 0x45
        packet[2] = ((totalLength ushr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        packet[8] = 64
        packet[9] = 6

        System.arraycopy(sourceIp, 0, packet, 12, 4)
        System.arraycopy(destinationIp, 0, packet, 16, 4)

        writeShort(packet, 20, sourcePort)
        writeShort(packet, 22, destinationPort)
        writeInt(packet, 24, sequenceNumber)
        writeInt(packet, 28, acknowledgementNumber)
        packet[32] = 0x50
        packet[33] = (flags and 0x3F).toByte()

        System.arraycopy(payload, 0, packet, 40, payload.size)
        return packet
    }

    private fun writeShort(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    private fun writeInt(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = ((value ushr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 3] = (value and 0xFF).toByte()
    }

    private fun readUnsignedShort(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun readUnsignedInt(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }
}

