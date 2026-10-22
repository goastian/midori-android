package org.midorinext.android.adblock

import org.junit.Assert.*
import org.json.JSONObject
import org.junit.Test
import java.io.File

class ScriptletRulesTest {
    @Test
    fun aliasesResolveAndArgumentsRemainData() {
        val rule = parseScriptletRule("news.example,~account.news.example##+js(set, player.adEnabled, false)")!!
        assertEquals("ubo/set-constant.js", rule.id)
        assertEquals(listOf("player.adEnabled", "false"), rule.args)
        assertEquals(listOf("news.example"), rule.included)
        assertEquals(listOf("account.news.example"), rule.excluded)
        assertEquals(rule.id, parseScriptletRule("news.example##+js(set-constant.js, player.adEnabled, false)")!!.id)
        val injection = "'); globalThis.injected = true; //"
        val adguard = parseScriptletRule("news.example#%#//scriptlet('set-constant', \"$injection\", 'false')")!!
        assertEquals(injection, adguard.args.first())
        assertEquals("adg/set-constant", adguard.id)
    }

    @Test
    fun escapedCommasQuotesAndRegexEscapesArePreserved() {
        assertEquals(listOf("json-prune", "ad,slot", "", "url:/player\\?id=/"),
            parseScriptletArguments("json-prune, ad\\,slot, , url:/player\\?id=/"))
        assertEquals(listOf("set", "a'b", "false"), parseScriptletArguments("'set', 'a\\'b', 'false'"))
        assertNull(parseScriptletArguments("'set', 'unterminated"))
        assertNull(parseScriptletArguments("'set' arbitrary-code"))
    }

    @Test
    fun trustedScriptletsRequireAnOfficialSourceAndArbitraryJavascriptIsExcluded() {
        val rule = "video.example##+js(trusted-rpfr, adPlacements, ignored, player)"
        assertNull(parseScriptletRule(rule))
        assertEquals("ubo/trusted-replace-fetch-response.js", parseScriptletRule(rule, trusted = true)!!.id)
        assertNull(parseScriptletRule("example.com#%#window.arbitrary = true;"))
        assertNull(parseScriptletRule("example.com##+js(unknown-function, value)"))
        assertNull(parseScriptletRule("##+js(set, advertising, false)"))
        assertEquals(0, parseHostSource("$rule\n||ads.example^").scriptlets.size)
        assertEquals(1, parseHostSource("$rule\n||ads.example^", trusted = true).scriptlets.size)
    }

    @Test
    fun tokenlessExceptionsEntitiesRegexAndAncestorScopesAreRecognized() {
        assertNull(parseScriptletRule("example.com#@#+js()")!!.id)
        assertTrue(parseScriptletRule("example.com#@%#//scriptlet()")!!.exception)
        val rule = parseScriptletRule("news.*,publisher.example>>,/[a-z]{2,3}\\.example/##+js(aopr, adblock)")!!
        assertEquals(listOf("news.*", "publisher.example>>", "/[a-z]{2,3}\\.example/"), rule.included)
    }

    @Test
    fun conditionalListsUseTheBranchSupportedByThisEngine() {
        val rules = parseHostSource("""
            !#if !cap_html_filtering && env_firefox
            video.example##+js(json-prune, adPlacements)
            !#else
            video.example##+js(set, shouldNotRun, true)
            !#endif
            !#if env_chromium
            video.example##+js(set, chromiumOnly, true)
            !#endif
        """.trimIndent())
        assertEquals(1, rules.scriptlets.size)
        assertEquals("ubo/json-prune.js", rules.scriptlets.single().id)
    }

    @Test
    fun bundledCataloguesCoverRealVideoRulesAndBothPublisherFormats() {
        assertNotNull(ScriptletCatalog.resolve("mpegdash-prune", true))
        assertNotNull(ScriptletCatalog.resolve("json-edit-fetch-request", true))
        assertNotNull(ScriptletCatalog.resolve("trusted-json-edit", true))
        assertNotNull(ScriptletCatalog.resolve("m3u-prune", false))
        val rules = parseHostSource(File("src/main/assets/adblock/ubo-ads.txt").readText(), trusted = true)
        assertTrue(rules.scriptlets.size > 8_000)
        assertTrue(rules.scriptlets.any { it.id == "ubo/json-prune.js" && "m.youtube.com" in it.included })
        assertTrue(rules.scriptlets.any { it.id == "ubo/trusted-replace-fetch-response.js" })
    }

    @Test
    fun theDirectlyWrittenJsonIsTheSameAsTheObjectModel() {
        val rules = listOf(
            ScriptletRule("set-constant", listOf("advertising", "false", "quote\"slash\\ </script> \u2028 ñ"), listOf("example.com", "*.example.org"), listOf("safe.example.com"), false, true),
            ScriptletRule(null, emptyList(), emptyList(), emptyList(), true, false),
            ScriptletRule("abort-on-property-read", listOf("a"), emptyList(), emptyList(), false, false),
        )
        for (tracker in listOf(false, true)) for (rule in rules) {
            val written = StringBuilder().also { rule.appendJson(it, tracker) }.toString()
            val parsed = JSONObject(written)
            val expected = rule.toJson(tracker)
            assertEquals(written, expected.isNull("id"), parsed.isNull("id"))
            if (!expected.isNull("id")) assertEquals(expected.getString("id"), parsed.getString("id"))
            for (key in listOf("args", "included", "excluded")) assertEquals(key, expected.getJSONArray(key).toString(), parsed.getJSONArray(key).toString())
            for (key in listOf("exception", "ublock", "tracker")) assertEquals(key, expected.getBoolean(key), parsed.getBoolean(key))
        }
    }
}
