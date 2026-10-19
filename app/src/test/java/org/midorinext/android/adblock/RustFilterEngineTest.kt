package org.midorinext.android.adblock

import java.io.File
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class RustFilterEngineTest {
    private val resources get() = File("src/main/assets/adblock/redirect-resources.json").readText()

    @Test
    fun cspFiltersAndTheirExceptionsPreserveThePageRequest() {
        RustFilterEngine.compile(listOf("||news.example^${'$'}csp=img-src 'none'\n@@||news.example/allowed^${'$'}csp")).use { engine ->
            val response = engine.query("https://news.example/article", "https://news.example/", "document")
            assertFalse(response.getBoolean("cancel"))
            assertEquals("img-src 'none'", response.getString("csp"))
            assertFalse(engine.query("https://news.example/allowed", "https://news.example/", "document").has("csp"))
        }
    }

    @Test
    fun bundledBinaryEnginesMatchTheCurrentSourcesAndPreserveBlocking() {
        val root = File("src/main/assets/adblock")
        val lists = OfficialFilterCatalog.all.filter { it.fallbackAsset != null }.map { source ->
            source to NativeFilterRules.fromCompiled(File(root, "compiled/${source.id}.json").readText()).engineSource
        }
        for (texts in listOf(lists.map { it.second }, lists.filter { it.first.tracker }.map { it.second })) {
            val fingerprint = RustFilterEngine.fingerprint(texts, resources)
            val file = File(root, "compiled/rust/$fingerprint.dat")
            assertTrue(file.path, file.isFile)
            RustFilterEngine.restore(file.readBytes(), resources).use { engine ->
                assertTrue(engine.query("https://www.google-analytics.com/analytics.js", "https://news.example/", "script").let { it.optBoolean("cancel") || it.optBoolean("blocked") })
                assertFalse(engine.query("https://news.example/articles/story", "https://news.example/", "document").getBoolean("cancel"))
            }
        }
    }

    @Test
    fun networkRegexesAndBadfilterWorkAcrossListsRegardlessOfOptionOrder() {
        RustFilterEngine.compile(listOf(
            "/ads[0-9]+\\.js/${'$'}script\n||disabled.example^${'$'}script,third-party",
            "||disabled.example^${'$'}third-party,badfilter,script",
        )).use { engine ->
            assertTrue(engine.query("https://cdn.example/ads42.js", "https://news.example/", "script").getBoolean("cancel"))
            assertFalse(engine.query("https://cdn.example/article.js", "https://news.example/", "script").getBoolean("cancel"))
            assertFalse(engine.query("https://disabled.example/ad.js", "https://news.example/", "script").getBoolean("cancel"))
        }
    }

    @Test
    fun redirectsUseBundledAliasesAndRedirectRulesRequireABlockingMatch() {
        RustFilterEngine.compile(listOf("""
            ||ads.example^${'$'}script,redirect=noopjs
            ||cdn.example/player.js${'$'}script,redirect-rule=noopjs
            ||blocked.example^${'$'}script
            ||blocked.example^${'$'}script,redirect-rule=noopjs
            @@||ads.example/editorial.js${'$'}script
        """.trimIndent()), resources).use { engine ->
            val response = engine.query("https://ads.example/ad.js", "https://news.example/", "script")
            assertFalse(response.getBoolean("cancel"))
            assertTrue(response.getBoolean("blocked"))
            assertTrue(response.getString("redirectUrl").startsWith("data:application/javascript;base64,"))
            assertFalse(engine.query("https://cdn.example/player.js", "https://news.example/", "script").has("redirectUrl"))
            assertTrue(engine.query("https://blocked.example/player.js", "https://news.example/", "script").has("redirectUrl"))
            assertFalse(engine.query("https://ads.example/editorial.js", "https://news.example/", "script").has("redirectUrl"))
        }
    }

    @Test
    fun importantBlockingCannotBeBypassedByAnOrdinaryExceptionInAnotherSubscription() {
        RustFilterEngine.compile(listOf("||ads.example^${'$'}script,important", "@@||ads.example^${'$'}script")).use { engine ->
            assertTrue(engine.query("https://ads.example/ad.js", "https://news.example/", "script").getBoolean("cancel"))
            assertFalse(engine.query("https://ads.example/photo.jpg", "https://news.example/", "image").getBoolean("cancel"))
        }
    }

    @Test
    fun parameterCleaningKeepsContentParametersAndHonorsHttpMethods() {
        RustFilterEngine.compile(listOf("||news.example^${'$'}removeparam=utm_source", "||ads.example^${'$'}method=POST")).use { engine ->
            assertEquals("https://news.example/article?id=42", engine.query("https://news.example/article?id=42&utm_source=ads", "https://news.example/", "document").getString("redirectUrl"))
            assertFalse(engine.query("https://ads.example/api", "https://news.example/", "xmlhttprequest", "GET").getBoolean("cancel"))
            assertTrue(engine.query("https://ads.example/api", "https://news.example/", "xmlhttprequest", "POST").getBoolean("cancel"))
        }
    }

    @Test
    fun documentExceptionsApplyToDescendantFramesWhileResourceExceptionsKeepTheirScope() {
        RustFilterEngine.compile(listOf("""
            ||ads.example^${'$'}script
            @@||reader.example^${'$'}document
            @@||video.example/player.js${'$'}script
        """.trimIndent())).use { engine ->
            assertFalse(engine.query("https://ads.example/ad.js", "https://embedded.example/", "script", document = "https://reader.example/article").getBoolean("cancel"))
            assertTrue(engine.query("https://ads.example/ad.js", "https://video.example/player.js", "script", document = "https://video.example/player.js").getBoolean("cancel"))
            assertTrue(engine.cosmetics("https://reader.example/article").getBoolean("disabled"))
        }
        RustFilterEngine.compile(listOf("@@||reader.example^${'$'}document", "@@||reader.example^${'$'}document,badfilter", "||ads.example^")).use { engine ->
            assertTrue(engine.query("https://ads.example/ad.js", "https://reader.example/", "script", document = "https://reader.example/").getBoolean("cancel"))
        }
    }

    @Test
    fun privateSuffixesDetermineThirdPartyAndWebSocketsAreFiltered() {
        RustFilterEngine.compile(listOf("||cdn.blogspot.com^${'$'}third-party,script", "||ads.example^${'$'}websocket")).use { engine ->
            assertTrue(engine.query("https://cdn.blogspot.com/a.js", "https://user.blogspot.com/", "script").getBoolean("cancel"))
            assertFalse(engine.query("https://cdn.blogspot.com/a.js", "https://cdn.blogspot.com/", "script").getBoolean("cancel"))
            assertTrue(engine.query("wss://ads.example/channel", "https://news.example/", "websocket").getBoolean("cancel"))
        }
    }

    @Test
    fun cosmeticEntitiesProceduralActionsGenericTokensAndExceptionsSurviveSerialization() {
        RustFilterEngine.compile(listOf("""
            ##.generic-ad
            example.*##.sponsor
            news.example#@#.generic-ad
            news.example##article:has-text(Sponsored):upward(1)
            news.example##.overlay:remove()
            @@||reader.example^${'$'}generichide
        """.trimIndent())).use { original ->
            RustFilterEngine.restore(original.serialize()).use { engine ->
                val entity = engine.cosmetics("https://www.example.co.uk/", listOf("generic-ad"))
                assertTrue(entity.getJSONArray("hide_selectors").toString().contains(".sponsor"))
                assertTrue(entity.getJSONArray("hide_selectors").toString().contains(".generic-ad"))
                val news = engine.cosmetics("https://news.example/", listOf("generic-ad"))
                assertFalse(news.getJSONArray("hide_selectors").toString().contains(".generic-ad"))
                assertEquals(2, news.getJSONArray("procedural_actions").length())
                assertTrue(engine.cosmetics("https://reader.example/", listOf("generic-ad")).getBoolean("generichide"))
            }
        }
    }

    @Test
    fun fullSubscriptionsAcceptNetworkSyntaxThatThePreviousParserDiscarded() {
        val parsed = parseHostSource("/advert[0-9]+\\.js/${'$'}script\n0.0.0.0 ads.example tracker.example # local hosts")
        RustFilterEngine.compile(listOf(parsed.engineSource)).use { engine ->
            assertTrue(engine.query("https://cdn.example/advert9.js", "https://news.example/", "script").getBoolean("cancel"))
            assertTrue(engine.query("https://tracker.example/beacon", "https://news.example/", "image").getBoolean("cancel"))
        }
    }

    @Test
    fun concurrentQueriesUseSafeNativeOwnership() {
        RustFilterEngine.compile(listOf("||ads.example^")).use { engine ->
            val executor = Executors.newFixedThreadPool(4)
            try {
                val queries = (1..100).map { executor.submit<Boolean> {
                    engine.query("https://ads.example/ad.js", "https://news.example/", "script").getBoolean("cancel")
                } }
                assertTrue(queries.all { it.get() })
            } finally { executor.shutdownNow() }
        }
    }
}
