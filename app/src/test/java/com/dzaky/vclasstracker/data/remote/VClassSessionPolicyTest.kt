package com.dzaky.vclasstracker.data.remote

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class VClassSessionPolicyTest {
    private val origin = VClassSessionPolicy.ORIGIN

    @Test fun `anonymous Moodle session and login page never prove authentication`() {
        assertFalse(VClassSessionPolicy.authenticated("$origin/my/", "<input name='sesskey' value='fixture'>"))
        assertFalse(VClassSessionPolicy.authenticated("$origin/login/index.php", "<a href='/login/logout.php'>Logout</a>"))
        assertFalse(VClassSessionPolicy.authenticated("$origin/my/", "<body class='notloggedin'><a href='/login/logout.php'>Logout</a>"))
        assertFalse(VClassSessionPolicy.authenticated("https://login.microsoftonline.com/common/", "<a href='/login/logout.php'>Logout</a>"))
        assertFalse(VClassSessionPolicy.authenticated("$origin/admin/oauth2callback.php", "<a href='/login/logout.php'>Logout</a>"))
        assertTrue(VClassSessionPolicy.authenticated("$origin/my/", "<a href='/login/logout.php'>Logout</a>"))
    }

    @Test fun `imports only VClass session and affinity cookies scoped to HTTPS root`() {
        val cookies = VClassSessionPolicy.importHeader("MoodleSession=fixture; srv_id=fixture-route; MicrosoftToken=private; MOODLEID=remembered")
        assertEquals(setOf("MoodleSession", "srv_id"), cookies.map { it.name }.toSet())
        assertTrue(cookies.all { it.secure && it.httpOnly && it.hostOnly && it.domain == "v-class.gunadarma.ac.id" && it.path == "/" })
        assertTrue(VClassSessionPolicy.importHeader("srv_id=fixture").isEmpty())
        assertTrue(VClassSessionPolicy.importHeader("MoodleSession=one; MoodleSession=two").isEmpty())
        assertTrue(VClassSessionPolicy.importHeader("MoodleSession=").isEmpty())
    }

    @Test fun `rejects lookalike domains HTTP credentials and foreign ports`() {
        listOf("http://v-class.gunadarma.ac.id/", "https://v-class.gunadarma.ac.id.evil.test/", "https://evil.test/", "https://v-class.gunadarma.ac.id:444/", "https://user:secret@v-class.gunadarma.ac.id/").forEach {
            assertNull(VClassSessionPolicy.officialUrl(it))
        }
        assertNotNull(VClassSessionPolicy.officialUrl("/course/view.php?id=12"))
        assertFalse(VClassSessionPolicy.isAuthNavigation("https://login.microsoftonline.com.evil.test/"))
        assertFalse(VClassSessionPolicy.isAuthNavigation("file:///tmp/login"))
        assertTrue(VClassSessionPolicy.isAuthNavigation("https://login.microsoftonline.com/common/"))
    }

    @Test fun `jar deduplicates expires and cannot resurrect cleared sessions`() {
        val jar = MemoryCookieJar()
        val url = origin.toHttpUrl()
        val session = VClassSessionPolicy.importHeader("MoodleSession=fixture").single()
        jar.replaceAll(listOf(session, session))
        assertEquals(1, jar.getAll().size)
        assertTrue(jar.loadForRequest("https://login.microsoftonline.com/".toHttpUrl()).isEmpty())
        val expired = Cookie.Builder().name("MoodleSession").value("fixture").hostOnlyDomain(url.host).expiresAt(1).build()
        jar.saveFromResponse(url, listOf(expired))
        assertTrue(jar.getAll().isEmpty())
        jar.clear()
        jar.saveFromResponse(url, listOf(session))
        assertTrue(jar.getAll().isEmpty())
        jar.replaceAll(listOf(Cookie.Builder().name("MoodleSession").value("fixture").domain("gunadarma.ac.id").build()))
        assertTrue(jar.getAll().isEmpty())
    }

    @Test fun `stale response cannot overwrite a newly imported account session`() {
        var responseIsCurrent = true
        val jar = MemoryCookieJar { responseIsCurrent }
        val old = VClassSessionPolicy.importHeader("MoodleSession=old-fixture").single()
        val current = VClassSessionPolicy.importHeader("MoodleSession=new-fixture").single()
        jar.replaceAll(listOf(old))
        jar.clear()
        jar.replaceAll(listOf(current))
        responseIsCurrent = false
        jar.saveFromResponse(origin.toHttpUrl(), listOf(old))
        assertEquals("new-fixture", jar.getAll().single().value)
    }

    @Test fun `same account handoff checks own profile ID when names are identical or absent`() {
        fun page(id: Int) = """
            <div class="usermenu"><a href="/user/profile.php">Profil</a></div>
            <div class="logininfo"><a href="/user/profile.php?id=$id">Mahasiswa</a></div>
            <div class="block_online_users"><a href="/user/profile.php?id=99">Other fixture user</a></div>
        """.trimIndent()
        val current = VClassSessionPolicy.userId(page(42))
        assertEquals(42L, current)
        assertTrue(VClassSessionPolicy.sameAccount(current, VClassSessionPolicy.userId(page(42))))
        assertFalse(VClassSessionPolicy.sameAccount(current, VClassSessionPolicy.userId(page(43))))
        assertFalse(VClassSessionPolicy.sameAccount(current, VClassSessionPolicy.userId("<div class='usermenu'>Mahasiswa</div>")))
        assertFalse(VClassSessionPolicy.sameAccount(0, current))
        assertEquals(42L, VClassSessionPolicy.userId("<script>M.cfg = {\"userid\":42};</script>"))
    }

    @Test fun `identity parser rejects unrelated foreign invalid or conflicting profile IDs`() {
        assertEquals(0L, VClassSessionPolicy.userId("<div class='block_online_users'><a href='/user/profile.php?id=42'>Fixture</a></div>"))
        assertEquals(0L, VClassSessionPolicy.userId("<script>var anotherUser = {\"userid\":42};</script>"))
        assertEquals(0L, VClassSessionPolicy.userId("<div class='logininfo'><a href='https://other.invalid/user/profile.php?id=42'>Fixture</a></div>"))
        assertEquals(0L, VClassSessionPolicy.userId("<div class='logininfo'><a href='/user/profile.php?id=-1'>Fixture</a></div>"))
        assertEquals(0L, VClassSessionPolicy.userId("<div class='logininfo'><a href='/user/profile.php?id=42'>Fixture</a></div><div class='usermenu'><a href='/user/profile.php?id=43'>Profil</a></div>"))
    }
}
