package org.midorinext.android.apptracking.packet

import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

class TunPacketPipeline(descriptor: ParcelFileDescriptor) {
    private val inputStream = FileInputStream(descriptor.fileDescriptor)
    private val outputStream = FileOutputStream(descriptor.fileDescriptor)

    fun readPacket(buffer: ByteArray): Int {
        return inputStream.read(buffer)
    }

    fun writePacket(packet: ByteArray) {
        outputStream.write(packet)
        outputStream.flush()
    }

    fun close() {
        inputStream.close()
        outputStream.close()
    }
}
