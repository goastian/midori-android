package contentBlocker

import org.midorinext.android.contentBlocker.ContentBlockerService
import kotlinx.coroutines.runBlocking
import mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.MalformedURLException
import java.net.URL
import java.security.MessageDigest
import kotlin.collections.mutableListOf

class ContentBlockerServiceTest {
    private val contentBlockerService = ContentBlockerService(
        cacheRepository = ContentBlockerCacheRepositoryMock(),
        client = HttpURLConnectionClient()
    )

    enum class TestUrlResult {
        BLOCKED_DOMAIN,
        BLOCKED_URL,
        NOT_BLOCKED_NOT_LISTED,
        NOT_BLOCKED_NO_HOST,
        NOT_BLOCKED_MALFORMED_URL,
        NOT_BLOCKED_NULL_URL
    }

    @Test
    fun test_url_list_from_assets() {
        val testResults = mutableListOf<Pair<String, TestUrlResult>>()

        val inputStream = javaClass.classLoader?.getResourceAsStream("pornsites.csv")
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line = reader.readLine()
        while (line != null) {
            println("checking $line")

            val uri = try {
                URL(line)
            } catch (e: MalformedURLException) {
                println("$line not blocked because url malformed")
                testResults.add(Pair(line, TestUrlResult.NOT_BLOCKED_MALFORMED_URL))
                null
            }

            uri?.let { uri ->
                val host = uri.hostWithoutCommonPrefixes
                host?.let { host ->
                    val path = if (uri.path?.isNotEmpty() == true && uri.path != "/") uri.path else null
                    runBlocking {
                        if (contentBlockerService.isDomainBlocked(getHash(host))) {
                            println("    blocked by domain")
                            testResults.add(Pair(line, TestUrlResult.BLOCKED_DOMAIN))
                        } else if (contentBlockerService.isUrlBLocked(getHash(host, path))) {
                            testResults.add(Pair(line, TestUrlResult.BLOCKED_URL))
                            println("    blocked by url")
                        } else {
                            testResults.add(Pair(line, TestUrlResult.NOT_BLOCKED_NOT_LISTED))
                            println("    not blocked")
                        }
                    }
                } ?: {
                    testResults.add(Pair(line, TestUrlResult.NOT_BLOCKED_NO_HOST))
                    println("    not blocked because no host")
                }
            } ?: {
                testResults.add(Pair(line, TestUrlResult.NOT_BLOCKED_NULL_URL))
                println("$line returned null uri")
            }

            line = reader.readLine()
        }

        printResults(testResults)
    }

    private fun printResults(results: List<Pair<String, TestUrlResult>>) {
        val blockedDomainCount = results.count { it.second == TestUrlResult.BLOCKED_DOMAIN }
        val blockedUrlCount = results.count { it.second == TestUrlResult.BLOCKED_URL }
        println("Blocked urls: ${blockedDomainCount + blockedUrlCount}")
        println("    by domain: $blockedDomainCount")
        println("    by url: $blockedUrlCount")

        if (results.count { it.second != TestUrlResult.BLOCKED_DOMAIN && it.second != TestUrlResult.BLOCKED_URL } == 0) {
            println("Not URL was left unblocked")
        } else {
            var unblockedCount = 0
            println("Not blocked:")
            results
                .filter { it.second != TestUrlResult.BLOCKED_DOMAIN && it.second != TestUrlResult.BLOCKED_URL }
                .forEach {
                    println("    ${it.first}")
                    unblockedCount++
                }
            println("Total not blocked: $unblockedCount URLs")
        }
    }

    private fun getHash(host: String, path: String? = null): String {
        val reversedHost = host.split(".").reversed().joinToString(".")
        var fullPath = reversedHost
        path?.let { fullPath += it }
        return fullPath.md5()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(this.toByteArray())
        return digest.toHexString()
    }

    val commonPrefixes = listOf("www.", "mobile.", "m.")
    val URL.hostWithoutCommonPrefixes: String?
        get() {
            val host = host ?: return null
            for (prefix in commonPrefixes) {
                if (host.startsWith(prefix)) return host.substring(prefix.length)
            }
            return host
        }
}