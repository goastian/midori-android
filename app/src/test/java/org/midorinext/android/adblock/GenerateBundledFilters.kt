package org.midorinext.android.adblock

import java.io.File
import java.security.MessageDigest

object GenerateBundledFilters {
    @JvmStatic
    fun main(args: Array<String>) {
        val root = File(args.single())
        val output = File(root, "compiled").apply { mkdirs() }
        val all = mutableListOf<String>()
        val trackers = mutableListOf<String>()
        for (source in OfficialFilterCatalog.all.filter { it.fallbackAsset != null }) {
            val file = File(root, "${source.fallbackAsset}.txt")
            val text = file.readText()
            val parsed = parseHostSource(text, trusted = true)
            all += parsed.engineSource
            if (source.tracker) trackers += parsed.engineSource
            val data = parsed.compiled(contentOnly = true).put("sourceSha256", MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray()).joinToString("") { "%02x".format(it) })
            File(output, "${source.id}.json").writeText(data.toString())
            println("${source.id}: ${parsed.supportedCount} rules, ${parsed.scriptlets.size} scriptlets")
        }
        val resources = File(root, "redirect-resources.json").readText()
        val native = File(output, "rust").apply { mkdirs() }
        native.listFiles().orEmpty().filter { it.extension == "dat" }.forEach { it.delete() }
        for (lists in listOf(all, trackers)) {
            RustFilterEngine.compile(lists, resources).use { engine ->
                val file = File(native, RustFilterEngine.fingerprint(lists, resources) + ".dat")
                file.writeBytes(engine.serialize())
                println("${file.name}: ${file.length()} bytes")
            }
        }
    }
}
