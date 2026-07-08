package org.midorinext.android.migration.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream


object MigrationDataSerializer : Serializer<MigrationData> {
    override val defaultValue: MigrationData = MigrationData.getDefaultInstance().toBuilder()
        .setMigration503(true)
        .setMigration504History(false)
        .setMigration504Bookmarks(false)
        .build()

    override suspend fun readFrom(input: InputStream): MigrationData {
        try {
            return MigrationData.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read protobuf.", exception)
        }
    }

    override suspend fun writeTo(t: MigrationData, output: OutputStream) = t.writeTo(output)
}