package org.midorinext.android.apptracking.packet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DnsPacketParserTest {

    private val parser = DnsPacketParser()

    @Test
    fun parsesIpv4UdpDnsQueryDomain() {
        val packet = buildDnsIpv4UdpPacket("google-analytics.com")
        val parsed = parser.parseDnsQuery(packet, packet.size)

        assertNotNull(parsed)
        assertEquals("google-analytics.com", parsed?.domain)
        assertEquals(53, parsed?.udpInfo?.destinationPort)
    }

    @Test
    fun returnsNullForNonUdpPacket() {
        val packet = buildDnsIpv4UdpPacket("example.org").also {
            it[9] = 6 // TCP protocol instead of UDP
        }

        val parsed = parser.parseDnsQuery(packet, packet.size)
        assertNull(parsed)
    }

    private fun buildDnsIpv4UdpPacket(domain: String): ByteArray {
        val questionBytes = domain.split('.')
            .flatMap { label ->
                listOf(label.length.toByte()) + label.toByteArray().toList()
            } + listOf(0.toByte())

        val dnsPayload = ByteArray(12 + questionBytes.size + 4)
        dnsPayload[0] = 0x12
        dnsPayload[1] = 0x34
        dnsPayload[2] = 0x01
        dnsPayload[3] = 0x00
        dnsPayload[4] = 0x00
        dnsPayload[5] = 0x01

        var offset = 12
        questionBytes.forEach { byte ->
            dnsPayload[offset++] = byte
        }
        dnsPayload[offset++] = 0x00
        dnsPayload[offset++] = 0x01
        dnsPayload[offset++] = 0x00
        dnsPayload[offset] = 0x01

        val udpLength = 8 + dnsPayload.size
        val totalLength = 20 + udpLength
        val packet = ByteArray(totalLength)

        packet[0] = 0x45
        packet[9] = 17
        packet[2] = ((totalLength ushr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()

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
        packet[22] = 0x00
        packet[23] = 0x35
        packet[24] = ((udpLength ushr 8) and 0xFF).toByte()
        packet[25] = (udpLength and 0xFF).toByte()

        System.arraycopy(dnsPayload, 0, packet, 28, dnsPayload.size)
        return packet
    }
}
