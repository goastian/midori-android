package org.midorinext.android.adblock

import java.io.File
import java.security.MessageDigest

object GenerateBundledFilters {
    @JvmStatic
    fun main(args: Array<String>) {
        val root = File(args.single())
        val output = File(root, "compiled").apply { mkdirs() }
        for (source in OfficialFilterCatalog.all.filter { it.fallbackAsset != null }) {
            val file = File(root, "${source.fallbackAsset}.txt")
            val text = file.readText()
            val parsed = parseHostSource(text, trusted = true)
            val data = parsed.compiled().put("sourceSha256", MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray()).joinToString("") { "%02x".format(it) })
            File(output, "${source.id}.json").writeText(data.toString())
            println("${source.id}: ${parsed.supportedCount} rules, ${parsed.scriptlets.size} scriptlets")
        }
    }
}
