package org.midorinext.android.apptracking.packet

data class UdpPacketInfo(
    val sourceIp: ByteArray,
    val destinationIp: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray
)

data class DnsQuestionPacket(
    val domain: String,
    val udpInfo: UdpPacketInfo,
    val dnsPayload: ByteArray
)
