package com.dzaky.vclasstracker.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

internal object VClassSessionPolicy {
    const val ORIGIN = "https://v-class.gunadarma.ac.id"
    const val LOGIN_URL = "$ORIGIN/login/index.php?loginredirect=1"
    private val origin = ORIGIN.toHttpUrl()
    private val sessionNames = setOf("MoodleSession", "srv_id")

    fun officialUrl(value: String): HttpUrl? {
        if (value.isBlank()) return null
        val url = origin.resolve(value) ?: return null
        return url.takeIf {
            it.isHttps && it.host == origin.host && it.port == 443 &&
                it.username.isEmpty() && it.password.isEmpty()
        }
    }

    fun accepts(cookie: Cookie, now: Long = System.currentTimeMillis()): Boolean =
        cookie.name in sessionNames && cookie.domain == origin.host &&
            cookie.path == "/" && cookie.value.isNotBlank() && cookie.expiresAt > now

    // CookieManager returns a Cookie header, not Set-Cookie metadata. Scope each
    // allowlisted pair to the exact VClass host; verification supplies authenticity.
    fun importHeader(header: String): List<Cookie> {
        val pairs = header.split(';').mapNotNull {
            val pair = it.trim().split('=', limit = 2)
            if (pair.size != 2 || pair[0] !in sessionNames || pair[1].isBlank()) null
            else pair[0] to pair[1]
        }
        if (pairs.groupBy { it.first }.any { it.value.size > 1 }) return emptyList()
        return pairs.mapNotNull { (name, value) ->
            runCatching {
                Cookie.Builder().name(name).value(value).hostOnlyDomain(origin.host)
                    .path("/").secure().httpOnly().build()
            }.getOrNull()
        }.takeIf { cookies -> cookies.any { it.name == "MoodleSession" } }.orEmpty()
    }

    fun authenticated(url: String, html: String): Boolean {
        val parsedUrl = url.toHttpUrlOrNull() ?: return false
        if (officialUrl(url) == null || isAuthPath(parsedUrl.encodedPath)) return false
        val doc = Jsoup.parse(html, ORIGIN)
        if (doc.selectFirst("body.notloggedin, body.guestuser, #page-login-index, form#login") != null) return false
        if (doc.selectFirst("input[name=username], input[name=password]") != null) return false
        if (doc.selectFirst("a[href*='/login/logout.php']") != null) return true
        val userId = Regex("[\"']userid[\"']\\s*:\\s*[\"']?(\\d+)")
            .find(html)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        return userId > 0 && doc.selectFirst(".usermenu, [data-region=usermenu]") != null
    }

    fun isAuthPath(path: String): Boolean =
        path.startsWith("/login/") || path.startsWith("/auth/") ||
            path == "/admin/oauth2callback.php"

    fun isAuthNavigation(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        if (!url.isHttps || url.port != 443 || url.username.isNotBlank() || url.password.isNotBlank()) return false
        return officialUrl(value) != null || url.host == "login.microsoftonline.com" ||
            url.host == "login.live.com" || url.host == "account.live.com" ||
            url.host == "login.microsoft.com" || url.host == "aadcdn.msauth.net" ||
            url.host == "aadcdn.msftauth.net"
    }
}

internal class MemoryCookieJar(private val responseAllowed: () -> Boolean = { true }) : CookieJar {
    private val cookies = mutableListOf<Cookie>()
    private var active = false

    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!active || !responseAllowed() || VClassSessionPolicy.officialUrl(url.toString()) == null) return
        for (cookie in cookies.filter { it.domain == url.host && it.path == "/" }) {
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (VClassSessionPolicy.accepts(cookie)) this.cookies.add(cookie)
        }
    }

    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
        getAll().filter { VClassSessionPolicy.officialUrl(url.toString()) != null && it.matches(url) }

    @Synchronized fun replaceAll(newCookies: List<Cookie>) {
        cookies.clear()
        cookies.addAll(newCookies.filter { VClassSessionPolicy.accepts(it) }
            .distinctBy { Triple(it.name, it.domain, it.path) })
        active = cookies.any { it.name == "MoodleSession" }
    }

    @Synchronized fun getAll(): List<Cookie> {
        cookies.removeAll { !VClassSessionPolicy.accepts(it) }
        return cookies.toList()
    }

    @Synchronized fun clear() { active = false; cookies.clear() }
}

internal class SessionExpiredException : java.io.IOException("Sesi VClass berakhir. Masuk kembali dengan Microsoft.")
