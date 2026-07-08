package org.midorinext.android.extensions.store

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object ExtensionStoreSerializer : Serializer<ExtensionStorePreferences> {
    override val defaultValue: ExtensionStorePreferences =
        ExtensionStorePreferences.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ExtensionStorePreferences {
        try {
            return ExtensionStorePreferences.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read protobuf.", exception)
        }
    }

    override suspend fun writeTo(t: ExtensionStorePreferences, output: OutputStream) =
        t.writeTo(output)
}

