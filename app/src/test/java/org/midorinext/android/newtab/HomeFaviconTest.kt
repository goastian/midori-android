package org.midorinext.android.newtab

import mozilla.components.browser.icons.IconRequest
import org.junit.Assert.*
import org.junit.Test

class HomeFaviconTest {
    @Test
    fun discoversTouchAndVectorIconsWithRelativeAndEscapedAddresses() {
        val links = discoverHomeIconLinks("""<head>
            <link rel='shortcut icon' href='small.ico' sizes='16x16 32x32'>
            <LINK HREF="/touch.png?size=192&amp;v=2" REL="apple-touch-icon" SIZES="192x192">
            <link rel=icon href=logo.svg sizes=any type=image/svg+xml>
            <link rel=manifest href=app/manifest.json>
            </head><link rel=icon href=not-a-site-icon.png>""", "https://example.org/path/page")
        assertEquals(3, links.resources.size)
        assertEquals("https://example.org/path/small.ico", links.resources[0].url)
        assertEquals("https://example.org/touch.png?size=192&v=2", links.resources[1].url)
        assertEquals(192, links.resources[1].sizes.single().width)
        assertEquals(IconRequest.Resource.Type.APPLE_TOUCH_ICON, links.resources[1].type)
        assertEquals(Int.MAX_VALUE, links.resources[2].sizes.single().width)
        assertEquals("https://example.org/path/app/manifest.json", links.manifest)
    }

    @Test
    fun respectsBaseAndIgnoresCommentAndScriptContent() {
        val links = discoverHomeIconLinks("""<head><base href="https://cdn.example.org/assets/">
            <!-- <link rel=icon href=fake-comment.png> -->
            <script>const ignored = '<link rel=icon href=fake-script.png>'</script>
            <link rel=icon href=real.png><link rel=icon href=real.png></head>""", "https://example.org/")
        assertEquals("https://cdn.example.org/assets/real.png", links.resources.single().url)
    }

    @Test
    fun rejectsUnsafeSchemesCredentialsEmptyLinksAndInvalidSizes() {
        val links = discoverHomeIconLinks("""<link rel=icon href='javascript:alert(1)'>
            <link rel=icon href='file:///tmp/icon.png'><link rel=icon href='https://user:pass@example.org/icon'>
            <link rel=icon href=''><link rel=icon href='good.png' sizes='0x0 -1x50 nope'>""", "https://example.org/")
        assertEquals("https://example.org/good.png", links.resources.single().url)
        assertTrue(links.resources.single().sizes.isEmpty())
    }

    @Test
    fun manifestPathsUseManifestAddressAndKeepLargeFullColorIcons() {
        val icons = discoverManifestIcons("""{"icons":[
            {"src":"small.png","sizes":"48x48"},
            {"src":"../large.png","sizes":"512x512","purpose":"any maskable","type":"image/png"},
            {"src":"badge.png","sizes":"192x192","purpose":"monochrome"},
            {"src":"javascript:alert(1)"}, {"sizes":"192x192"}]}""", "https://example.org/app/manifest.json")
        assertEquals(2, icons.size)
        assertEquals("https://example.org/large.png", icons[1].url)
        assertEquals(512, icons[1].sizes.single().height)
        assertEquals(IconRequest.Resource.Type.MANIFEST_ICON, icons[1].type)
    }

    @Test
    fun originsGroupPagesWithoutMergingPortsOrSubdomains() {
        assertEquals("https://www.example.org/", homeIconOrigin("https://www.example.org/path?q=2#here"))
        assertEquals("https://example.org:8443/", homeIconOrigin("https://example.org:8443/path"))
        assertNotEquals(homeIconOrigin("https://a.example.org/"), homeIconOrigin("https://b.example.org/"))
        assertNull(homeIconOrigin(NATIVE_HOME_URL))
    }

    @Test
    fun cachedIconMetadataPreservesResourceTypesAndSizes() {
        val icon = discoverManifestIcons("""{"icons":[{"src":"https://example.org/touch.png",
            "sizes":"180x180","iconType":"APPLE_TOUCH_ICON"}]}""", "https://example.org/").single()
        assertEquals(IconRequest.Resource.Type.APPLE_TOUCH_ICON, icon.type)
        assertEquals(180, icon.sizes.single().width)
    }

    @Test
    fun resolutionTakesPriorityOverIconTypeAndUnknownTouchIconsAreTriedBeforeTinyFavicons() {
        val page = discoverHomeIconLinks("""<link rel=icon href=large.png sizes=512x512>
            <link rel=apple-touch-icon href=touch.png sizes=180x180>
            <link rel=apple-touch-icon href=unknown.png><link rel=icon href=tiny.ico sizes=16x16>""", "https://example.org/")
        assertEquals(listOf("large.png", "unknown.png", "touch.png", "tiny.ico"),
            rankHomeIcons(page.resources).map { it.url.substringAfterLast('/') })
    }
}
