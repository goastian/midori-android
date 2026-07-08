package org.midorinext.android.apptracking.packet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrafficHostInspectorTest {

    private val inspector = TrafficHostInspector()

    @Test
    fun parsesHttpHostFromTcpPayload() {
        val packet = buildIpv4TcpPacket(
            destinationPort = 80,
            payload = (
                "GET /collect HTTP/1.1\r\n" +
                    "Host: app-measurement.com\r\n" +
                    "User-Agent: test\r\n\r\n"
                ).toByteArray()
        )

        val signal = inspector.parseHostSignal(packet, packet.size)

        assertEquals("app-measurement.com", signal?.host)
        assertEquals(80, signal?.destinationPort)
    }

    @Test
    fun parsesTlsSniFromClientHello() {
        val packet = buildIpv4TcpPacket(
            destinationPort = 443,
            payload = buildTlsClientHelloPayload()
        )

        val signal = inspector.parseHostSignal(packet, packet.size)

        assertEquals("connect.facebook.net", signal?.host)
        assertEquals(443, signal?.destinationPort)
    }

    @Test
    fun returnsNullWhenNoHostSignal() {
        val packet = buildIpv4TcpPacket(
            destinationPort = 443,
            payload = byteArrayOf(0x01, 0x02, 0x03)
        )

        val signal = inspector.parseHostSignal(packet, packet.size)

        assertNull(signal)
    }

    private fun buildIpv4TcpPacket(destinationPort: Int, payload: ByteArray): ByteArray {
        val ipHeaderLen = 20
        val tcpHeaderLen = 20
        val totalLength = ipHeaderLen + tcpHeaderLen + payload.size
        val packet = ByteArray(totalLength)

        packet[0] = 0x45
        packet[2] = ((totalLength ushr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        packet[8] = 64
        packet[9] = 6

        packet[12] = 10
        packet[13] = 22
        packet[14] = 0
        packet[15] = 2
        packet[16] = 1
        packet[17] = 1
        packet[18] = 1
        packet[19] = 1

        packet[20] = 0xC0.toByte()
        packet[21] = 0xDE.toByte()
        packet[22] = ((destinationPort ushr 8) and 0xFF).toByte()
        packet[23] = (destinationPort and 0xFF).toByte()
        packet[32] = 0x50

        System.arraycopy(payload, 0, packet, 40, payload.size)
        return packet
    }

    private fun buildTlsClientHelloPayload(): ByteArray {
        val hostBytes = "connect.facebook.net".toByteArray(Charsets.US_ASCII)

        val sniEntry = byteArrayOf(
            0x00,
            ((hostBytes.size ushr 8) and 0xFF).toByte(),
            (hostBytes.size and 0xFF).toByte()
        ) + hostBytes

        val serverNameList = byteArrayOf(
            ((sniEntry.size ushr 8) and 0xFF).toByte(),
            (sniEntry.size and 0xFF).toByte()
        ) + sniEntry

        val sniExtension = byteArrayOf(
            0x00, 0x00,
            ((serverNameList.size ushr 8) and 0xFF).toByte(),
            (serverNameList.size and 0xFF).toByte()
        ) + serverNameList

        val extensions = sniExtension
        val clientHelloBody = buildList {
            add(0x03)
            add(0x03)
            repeat(32) { add(0x00) }
            add(0x00)
            add(0x00)
            add(0x02)
            add(0x13)
            add(0x01)
            add(0x01)
            add(0x00)
            add(((extensions.size ushr 8) and 0xFF))
            add((extensions.size and 0xFF))
            addAll(extensions.map { it.toInt() and 0xFF })
        }.map { it.toByte() }.toByteArray()

        val handshake = byteArrayOf(
            0x01,
            ((clientHelloBody.size ushr 16) and 0xFF).toByte(),
            ((clientHelloBody.size ushr 8) and 0xFF).toByte(),
            (clientHelloBody.size and 0xFF).toByte()
        ) + clientHelloBody

        return byteArrayOf(
            0x16,
            0x03,
            0x01,
            ((handshake.size ushr 8) and 0xFF).toByte(),
            (handshake.size and 0xFF).toByte()
        ) + handshake
    }
}


