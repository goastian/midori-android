package org.midorinext.android.apptracking.packet

object TcpPacketCodec {

    fun createTcpResetResponse(packet: ByteArray, length: Int): ByteArray? {
        if (length < MIN_IPV4_HEADER_SIZE + MIN_TCP_HEADER_SIZE) return null

        val version = packet[0].toInt() ushr 4
        if (version != IPV4_VERSION) return null

        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        if (ipHeaderLen < MIN_IPV4_HEADER_SIZE || length < ipHeaderLen + MIN_TCP_HEADER_SIZE) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != TCP_PROTOCOL) return null

        val totalLength = readUnsignedShort(packet, 2)
        if (totalLength < ipHeaderLen + MIN_TCP_HEADER_SIZE || totalLength > length) return null

        val tcpStart = ipHeaderLen
        val tcpHeaderLen = ((packet[tcpStart + 12].toInt() ushr 4) and 0x0F) * 4
        if (tcpHeaderLen < MIN_TCP_HEADER_SIZE || totalLength < tcpStart + tcpHeaderLen) return null

        val sourceIp = packet.copyOfRange(12, 16)
        val destinationIp = packet.copyOfRange(16, 20)

        val sourcePort = readUnsignedShort(packet, tcpStart)
        val destinationPort = readUnsignedShort(packet, tcpStart + 2)

        val sequenceNumber = readUnsignedInt(packet, tcpStart + 4)
        val acknowledgementNumber = readUnsignedInt(packet, tcpStart + 8)
        val flags = readUnsignedByte(packet, tcpStart + 13)

        val payloadLength = totalLength - ipHeaderLen - tcpHeaderLen
        val sequenceAdvance = payloadLength +
            if ((flags and TCP_FLAG_SYN) != 0 || (flags and TCP_FLAG_FIN) != 0) 1 else 0

        val responseSequence = acknowledgementNumber
        val responseAcknowledgement = sequenceNumber + sequenceAdvance

        return buildIpv4TcpPacket(
            sourceIp = destinationIp,
            destinationIp = sourceIp,
            sourcePort = destinationPort,
            destinationPort = sourcePort,
            sequenceNumber = responseSequence,
            acknowledgementNumber = responseAcknowledgement,
            flags = TCP_FLAG_RST or TCP_FLAG_ACK
        )
    }

    private fun buildIpv4TcpPacket(
        sourceIp: ByteArray,
        destinationIp: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        sequenceNumber: Long,
        acknowledgementNumber: Long,
        flags: Int
    ): ByteArray {
        val ipHeaderLen = MIN_IPV4_HEADER_SIZE
        val tcpHeaderLen = MIN_TCP_HEADER_SIZE
        val totalLength = ipHeaderLen + tcpHeaderLen
        val packet = ByteArray(totalLength)

        packet[0] = 0x45
        packet[1] = 0x00
        writeShort(packet, 2, totalLength)
        writeShort(packet, 4, 0)
        writeShort(packet, 6, 0)
        packet[8] = 64
        packet[9] = TCP_PROTOCOL.toByte()
        writeShort(packet, 10, 0)

        copyIp(sourceIp, packet, 12)
        copyIp(destinationIp, packet, 16)

        writeShort(packet, ipHeaderLen, sourcePort)
        writeShort(packet, ipHeaderLen + 2, destinationPort)
        writeInt(packet, ipHeaderLen + 4, sequenceNumber)
        writeInt(packet, ipHeaderLen + 8, acknowledgementNumber)
        packet[ipHeaderLen + 12] = 0x50
        packet[ipHeaderLen + 13] = (flags and 0x3F).toByte()
        writeShort(packet, ipHeaderLen + 14, 65535)
        writeShort(packet, ipHeaderLen + 16, 0)
        writeShort(packet, ipHeaderLen + 18, 0)

        val ipv4Checksum = ipv4HeaderChecksum(packet, ipHeaderLen)
        writeShort(packet, 10, ipv4Checksum)

        val tcpChecksum = tcpChecksum(packet, ipHeaderLen, tcpHeaderLen)
        writeShort(packet, ipHeaderLen + 16, tcpChecksum)

        return packet
    }

    private fun tcpChecksum(packet: ByteArray, ipHeaderLen: Int, tcpLength: Int): Int {
        var sum = 0L

        // Pseudo-header: source IP + destination IP
        for (i in 12 until 20 step 2) {
            sum += readUnsignedShort(packet, i).toLong()
        }

        // Pseudo-header: zero + protocol + TCP length
        sum += TCP_PROTOCOL.toLong()
        sum += tcpLength.toLong()

        // TCP header + payload (payload is empty here)
        var offset = ipHeaderLen
        while (offset < ipHeaderLen + tcpLength) {
            val word = ((packet[offset].toInt() and 0xFF) shl 8) or
                (packet[offset + 1].toInt() and 0xFF)
            sum += word.toLong()
            offset += 2
        }

        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }

        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun copyIp(source: ByteArray, target: ByteArray, start: Int) {
        for (index in 0 until 4) {
            target[start + index] = source.getOrElse(index) { 0 }
        }
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

    private fun readUnsignedByte(bytes: ByteArray, offset: Int): Int {
        return bytes[offset].toInt() and 0xFF
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

    private const val IPV4_VERSION = 4
    private const val TCP_PROTOCOL = 6
    private const val MIN_IPV4_HEADER_SIZE = 20
    private const val MIN_TCP_HEADER_SIZE = 20

    private const val TCP_FLAG_FIN = 0x01
    private const val TCP_FLAG_SYN = 0x02
    private const val TCP_FLAG_RST = 0x04
    private const val TCP_FLAG_ACK = 0x10
}

