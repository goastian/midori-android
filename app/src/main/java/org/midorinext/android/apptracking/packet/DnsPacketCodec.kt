package org.midorinext.android.apptracking.packet

import kotlin.math.min

object DnsPacketCodec {

    fun createNxDomainResponse(queryPayload: ByteArray): ByteArray {
        val response = queryPayload.copyOf()
        if (response.size < DNS_HEADER_SIZE) return response

        // Response + recursion available + NXDOMAIN code.
        response[2] = 0x81.toByte()
        response[3] = 0x83.toByte()
        response[6] = 0x00
        response[7] = 0x00
        response[8] = 0x00
        response[9] = 0x00
        response[10] = 0x00
        response[11] = 0x00

        return response
    }

    fun buildIpv4UdpPacket(
        sourceIp: ByteArray,
        destinationIp: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val packetSize = ipHeaderLen + udpHeaderLen + payload.size
        val packet = ByteArray(packetSize)

        packet[0] = 0x45
        packet[1] = 0x00
        writeShort(packet, 2, packetSize)
        writeShort(packet, 4, 0)
        writeShort(packet, 6, 0)
        packet[8] = 64
        packet[9] = 17
        writeShort(packet, 10, 0)

        copyIp(sourceIp, packet, 12)
        copyIp(destinationIp, packet, 16)

        writeShort(packet, 20, sourcePort)
        writeShort(packet, 22, destinationPort)
        writeShort(packet, 24, udpHeaderLen + payload.size)
        writeShort(packet, 26, 0)

        System.arraycopy(payload, 0, packet, 28, payload.size)

        val headerChecksum = ipv4HeaderChecksum(packet, ipHeaderLen)
        writeShort(packet, 10, headerChecksum)

        return packet
    }

    private fun copyIp(source: ByteArray, target: ByteArray, start: Int) {
        val safeLength = min(source.size, 4)
        for (index in 0 until safeLength) {
            target[start + index] = source[index]
        }
    }

    private fun writeShort(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    private fun ipv4HeaderChecksum(packet: ByteArray, headerLength: Int): Int {
        var sum = 0
        var index = 0
        while (index < headerLength) {
            if (index == 10) {
                index += 2
                continue
            }
            val word = ((packet[index].toInt() and 0xFF) shl 8) or (packet[index + 1].toInt() and 0xFF)
            sum += word
            while (sum ushr 16 != 0) {
                sum = (sum and 0xFFFF) + (sum ushr 16)
            }
            index += 2
        }
        return sum.inv() and 0xFFFF
    }

    private const val DNS_HEADER_SIZE = 12
}
