package org.midorinext.android.adblock

import java.io.File
import java.security.MessageDigest

object GenerateBundledFilters {
    @JvmStatic
    fun main(args: Array<String>) {
        val root = File(args.single())
        val output = File(root, "compiled").apply { mkdirs() }
        val all = mutableListOf<EngineList>()
        val trackers = mutableListOf<EngineList>()
        for (source in OfficialFilterCatalog.all.filter { it.fallbackAsset != null }) {
            val file = File(root, "${source.fallbackAsset}.txt")
            val parsed = parseHostFile(file, File(output, "${source.id}.engine"), trusted = true)
            val engine = checkNotNull(parsed.engine)
            all += engine
            if (source.tracker) trackers += engine
            val data = parsed.compiled(contentOnly = true).put("sourceSha256", MessageDigest.getInstance("SHA-256")
                .digest(file.readBytes()).joinToString("") { "%02x".format(it) })
            File(output, "${source.id}.json").writeText(data.toString())
            println("${source.id}: ${parsed.supportedCount} rules, ${parsed.scriptlets.size} scriptlets")
        }
        val resources = File(root, "redirect-resources.json").readText()
        val resourcesSha256 = RustFilterEngine.sha256(resources.toByteArray())
        val native = File(output, "rust").apply { mkdirs() }
        native.listFiles().orEmpty().filter { it.extension == "dat" }.forEach { it.delete() }
        for (lists in listOf(all, trackers)) {
            RustFilterEngine.compileFiles(lists.map { it.file }, resources).use { engine ->
                val file = File(native, RustFilterEngine.fingerprint(lists, resourcesSha256) + ".dat")
                engine.serializeTo(file)
                println("${file.name}: ${file.length()} bytes")
            }
        }
    }
}
