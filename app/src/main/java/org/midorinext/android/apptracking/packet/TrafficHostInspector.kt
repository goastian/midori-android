package org.midorinext.android.apptracking.packet

import java.nio.charset.StandardCharsets

class HostTrafficSignal(
    val host: String,
    val protocol: Int,
    val sourceIp: ByteArray,
    val destinationIp: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int
)

class TrafficHostInspector {

    fun parseHostSignal(packet: ByteArray, length: Int): HostTrafficSignal? {
        if (length < MIN_IPV4_HEADER_SIZE) return null

        val version = packet[0].toInt() ushr 4
        if (version != IPV4_VERSION) return null

        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        if (length < ipHeaderLen + MIN_TCP_HEADER_SIZE) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != TCP_PROTOCOL) return null

        val sourceIp = packet.copyOfRange(12, 16)
        val destinationIp = packet.copyOfRange(16, 20)

        val tcpStart = ipHeaderLen
        val sourcePort = readUnsignedShort(packet, tcpStart)
        val destinationPort = readUnsignedShort(packet, tcpStart + 2)

        val tcpHeaderLen = ((packet[tcpStart + 12].toInt() ushr 4) and 0x0F) * 4
        if (tcpHeaderLen < MIN_TCP_HEADER_SIZE || length < tcpStart + tcpHeaderLen) return null

        val payloadStart = tcpStart + tcpHeaderLen
        if (payloadStart >= length) return null

        val payload = packet.copyOfRange(payloadStart, length)
        val host = when (destinationPort) {
            HTTP_PORT -> parseHttpHost(payload)
            TLS_PORT -> parseTlsSni(payload)
            else -> null
        } ?: return null

        return HostTrafficSignal(
            host = host,
            protocol = protocol,
            sourceIp = sourceIp,
            destinationIp = destinationIp,
            sourcePort = sourcePort,
            destinationPort = destinationPort
        )
    }

    private fun parseHttpHost(payload: ByteArray): String? {
        val text = payload.copyOfRange(0, minOf(payload.size, HTTP_INSPECTION_LIMIT))
            .toString(StandardCharsets.ISO_8859_1)
        if (!HTTP_METHOD_PATTERN.containsMatchIn(text)) return null

        val hostLine = text.lineSequence()
            .firstOrNull { it.startsWith("Host:", ignoreCase = true) }
            ?: return null

        val hostValue = hostLine.substringAfter(':', "")
            .trim()
            .substringBefore(':')
            .trim()
            .lowercase()
        return hostValue.takeIf { it.isNotBlank() }
    }

    private fun parseTlsSni(payload: ByteArray): String? {
        if (payload.size < TLS_RECORD_HEADER_SIZE + TLS_HANDSHAKE_HEADER_SIZE) return null
        if (payload[0].toInt() and 0xFF != TLS_HANDSHAKE_RECORD_TYPE) return null

        val recordLength = readUnsignedShort(payload, 3)
        if (recordLength <= 0 || payload.size < TLS_RECORD_HEADER_SIZE + recordLength) return null

        val handshakeStart = TLS_RECORD_HEADER_SIZE
        if (payload[handshakeStart].toInt() and 0xFF != TLS_CLIENT_HELLO_TYPE) return null

        var cursor = handshakeStart + TLS_HANDSHAKE_HEADER_SIZE
        if (cursor + TLS_CLIENT_HELLO_FIXED_BODY > payload.size) return null

        cursor += TLS_CLIENT_HELLO_FIXED_BODY

        val sessionIdLength = readUnsignedByte(payload, cursor)
        cursor += 1 + sessionIdLength
        if (cursor + 2 > payload.size) return null

        val cipherSuitesLength = readUnsignedShort(payload, cursor)
        cursor += 2 + cipherSuitesLength
        if (cursor + 1 > payload.size) return null

        val compressionMethodsLength = readUnsignedByte(payload, cursor)
        cursor += 1 + compressionMethodsLength
        if (cursor + 2 > payload.size) return null

        val extensionsLength = readUnsignedShort(payload, cursor)
        cursor += 2
        val extensionsEnd = cursor + extensionsLength
        if (extensionsEnd > payload.size) return null

        while (cursor + 4 <= extensionsEnd) {
            val extensionType = readUnsignedShort(payload, cursor)
            val extensionLength = readUnsignedShort(payload, cursor + 2)
            cursor += 4
            if (cursor + extensionLength > extensionsEnd) return null

            if (extensionType == TLS_SNI_EXTENSION_TYPE) {
                val host = parseSniExtension(payload, cursor, extensionLength)
                if (!host.isNullOrBlank()) return host
            }

            cursor += extensionLength
        }

        return null
    }

    private fun parseSniExtension(payload: ByteArray, offset: Int, length: Int): String? {
        if (length < 5 || offset + length > payload.size) return null

        val serverNameListLength = readUnsignedShort(payload, offset)
        var cursor = offset + 2
        val listEnd = cursor + serverNameListLength
        if (listEnd > offset + length) return null

        while (cursor + 3 <= listEnd) {
            val nameType = readUnsignedByte(payload, cursor)
            val nameLength = readUnsignedShort(payload, cursor + 1)
            cursor += 3
            if (cursor + nameLength > listEnd) return null

            if (nameType == TLS_SNI_HOST_NAME_TYPE) {
                return payload.copyOfRange(cursor, cursor + nameLength)
                    .toString(StandardCharsets.US_ASCII)
                    .lowercase()
                    .trim()
                    .takeIf { it.isNotBlank() }
            }

            cursor += nameLength
        }

        return null
    }

    private fun readUnsignedByte(bytes: ByteArray, offset: Int): Int {
        return bytes[offset].toInt() and 0xFF
    }

    private fun readUnsignedShort(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    companion object {
        private const val IPV4_VERSION = 4
        private const val MIN_IPV4_HEADER_SIZE = 20
        private const val MIN_TCP_HEADER_SIZE = 20
        private const val TCP_PROTOCOL = 6

        private const val HTTP_PORT = 80
        private const val TLS_PORT = 443
        private const val HTTP_INSPECTION_LIMIT = 2048
        private val HTTP_METHOD_PATTERN = Regex("^(GET|POST|PUT|DELETE|HEAD|OPTIONS|PATCH|CONNECT)\\s", RegexOption.IGNORE_CASE)

        private const val TLS_RECORD_HEADER_SIZE = 5
        private const val TLS_HANDSHAKE_HEADER_SIZE = 4
        private const val TLS_CLIENT_HELLO_FIXED_BODY = 34
        private const val TLS_HANDSHAKE_RECORD_TYPE = 0x16
        private const val TLS_CLIENT_HELLO_TYPE = 0x01
        private const val TLS_SNI_EXTENSION_TYPE = 0
        private const val TLS_SNI_HOST_NAME_TYPE = 0
    }
}

