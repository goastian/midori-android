package org.midorinext.android.apptracking.packet

class DnsPacketParser {

    fun parseDnsQuery(packet: ByteArray, length: Int): DnsQuestionPacket? {
        if (length < MIN_IPV4_HEADER_SIZE) return null

        val version = packet[0].toInt() ushr 4
        if (version != IPV4_VERSION) return null

        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        if (length < ipHeaderLen + UDP_HEADER_SIZE) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != UDP_PROTOCOL) return null

        val sourceIp = packet.copyOfRange(12, 16)
        val destinationIp = packet.copyOfRange(16, 20)

        val udpStart = ipHeaderLen
        val sourcePort = readUnsignedShort(packet, udpStart)
        val destinationPort = readUnsignedShort(packet, udpStart + 2)
        val udpLength = readUnsignedShort(packet, udpStart + 4)
        if (udpLength < UDP_HEADER_SIZE || udpStart + udpLength > length) return null

        val payloadStart = udpStart + UDP_HEADER_SIZE
        val payloadLength = udpLength - UDP_HEADER_SIZE
        if (payloadLength < DNS_HEADER_SIZE) return null

        val payload = packet.copyOfRange(payloadStart, payloadStart + payloadLength)
        if (!isStandardQuery(payload)) return null

        val domain = parseDomain(payload) ?: return null

        return DnsQuestionPacket(
            domain = domain,
            udpInfo = UdpPacketInfo(
                sourceIp = sourceIp,
                destinationIp = destinationIp,
                sourcePort = sourcePort,
                destinationPort = destinationPort,
                payload = payload
            ),
            dnsPayload = payload
        )
    }

    private fun isStandardQuery(payload: ByteArray): Boolean {
        val flags = readUnsignedShort(payload, 2)
        val questionCount = readUnsignedShort(payload, 4)
        val answerCount = readUnsignedShort(payload, 6)
        return flags and QUERY_RESPONSE_MASK == 0 && questionCount > 0 && answerCount == 0
    }

    private fun parseDomain(payload: ByteArray): String? {
        var index = DNS_HEADER_SIZE
        val labels = mutableListOf<String>()

        while (index < payload.size) {
            val labelLen = payload[index].toInt() and 0xFF
            if (labelLen == 0) {
                return if (labels.isEmpty()) null else labels.joinToString(".").lowercase()
            }

            index += 1
            if (index + labelLen > payload.size) return null

            val label = payload.copyOfRange(index, index + labelLen).decodeToString()
            labels += label
            index += labelLen
        }

        return null
    }

    private fun readUnsignedShort(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    companion object {
        private const val IPV4_VERSION = 4
        private const val UDP_PROTOCOL = 17
        private const val MIN_IPV4_HEADER_SIZE = 20
        private const val UDP_HEADER_SIZE = 8
        private const val DNS_HEADER_SIZE = 12
        private const val QUERY_RESPONSE_MASK = 0x8000
    }
}
