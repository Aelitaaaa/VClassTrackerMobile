package com.dzaky.vclasstracker.data.remote

import android.content.Context
import com.dzaky.vclasstracker.data.local.VClassSessionStore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object VClassClient {

    private const val BASE_URL =
        "https://v-class.gunadarma.ac.id"

    private const val MY_COURSES_URL =
        "$BASE_URL/my/"

    private const val COURSE_SEARCH_URL =
        "$BASE_URL/course/search.php"

    private const val UPCOMING_URL =
        "$BASE_URL/calendar/view.php?view=upcoming"

    private const val AJAX_URL =
        "$BASE_URL/lib/ajax/service.php"

    private val wibZone =
        ZoneId.of("Asia/Jakarta")

    private val requestVersion = ThreadLocal<Long>()
    private val cookieJar = MemoryCookieJar { requestVersion.get() == generation.get() }

    private var applicationContext: Context? =
        null

    private val generation = AtomicLong(0)
    private val changes = MutableStateFlow(0L)
    val sessionChanges = changes.asStateFlow()

    @Volatile private var restoredDisplayName =
        ""

    private val client =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(true)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val version = generation.get()
                requestVersion.set(version)
                val request =
                    chain.request()
                        .newBuilder()
                        .tag(SessionVersion::class.java, SessionVersion(version))
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36"
                        )
                        .header(
                            "Accept-Language",
                            "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
                        )
                        .build()

                try { chain.proceed(request) } finally { requestVersion.remove() }
            }
            .addNetworkInterceptor { chain ->
                val version = chain.request().tag(SessionVersion::class.java)?.value
                if (version != generation.get()) throw SessionExpiredException()
                if (VClassSessionPolicy.officialUrl(chain.request().url.toString()) == null) {
                    throw SessionExpiredException()
                }
                val response = chain.proceed(chain.request())
                if (version != generation.get()) {
                    response.close()
                    throw SessionExpiredException()
                }
                response
            }
            .build()

    @Synchronized fun initialize(
        context: Context
    ) {
        if (applicationContext != null) return
        applicationContext = context.applicationContext
        restoreSavedSession()
    }

    fun getRestoredDisplayName(): String {
        return restoredDisplayName
    }

    fun hasSavedSession(): Boolean {
        val context =
            applicationContext
                ?: return false

        return VClassSessionStore(
            context
        ).hasSession()
    }

    private fun restoreSavedSession() {
        val context =
            applicationContext
                ?: return

        val session =
            VClassSessionStore(
                context
            ).loadSession()
                ?: return

        cookieJar.replaceAll(
            session.cookies
        )

        restoredDisplayName =
            session.displayName
    }

    private fun saveCurrentSession(
        displayName: String
    ) {
        val context =
            applicationContext
                ?: throw IllegalStateException("VClass client is not initialized")

        val cookies =
            cookieJar.getAll()

        if (cookies.isEmpty()) {
            return
        }

        VClassSessionStore(
            context
        ).saveSession(
            displayName = displayName,
            cookies = cookies
        )

        restoredDisplayName =
            displayName
    }

    @Synchronized fun clearSession() {
        generation.incrementAndGet()
        client.dispatcher.cancelAll()
        cookieJar.clear()

        applicationContext?.let { context ->
            VClassSessionStore(
                context
            ).clear()
        }

        restoredDisplayName = ""
        changes.value = generation.get()
    }

    fun currentSessionVersion(): Long = generation.get()

    fun isSessionCurrent(version: Long): Boolean =
        version == generation.get() && cookieJar.getAll().any { it.name == "MoodleSession" }

    fun sessionCookies(): List<okhttp3.Cookie> = cookieJar.getAll()

    internal fun <T> withCurrentSession(version: Long, action: () -> T): T? = synchronized(this) {
        if (isSessionCurrent(version)) action() else null
    }

    internal fun withNoSession(action: () -> Unit) = synchronized(this) {
        if (cookieJar.getAll().none { it.name == "MoodleSession" }) action()
    }

    fun verifySession(): LoginResult {
        val version = generation.get()
        val page = getAuthenticatedPage(MY_COURSES_URL)
        if (!page.success) return LoginResult(false, page.message, sessionExpired = page.sessionExpired)
        val name = extractDisplayName(Jsoup.parse(page.html, BASE_URL)).ifBlank { "Mahasiswa" }
        return try {
            withCurrentSession(version) {
                saveCurrentSession(name)
                LoginResult(true, "Sesi VClass terverifikasi.", name)
            } ?: LoginResult(false, "Sesi VClass telah berubah.", sessionExpired = true)
        } catch (_: Exception) {
            LoginResult(false, "Tidak dapat menyimpan sesi terenkripsi. Coba masuk kembali.")
        }
    }

    fun completeMicrosoftLogin(cookieHeader: String, sameAccountOnly: Boolean = false): LoginResult {
        val imported = VClassSessionPolicy.importHeader(cookieHeader)
        if (imported.isEmpty()) return LoginResult(false, "Sesi VClass belum tersedia. Selesaikan login di halaman resmi.")
        val version = generation.get()
        val candidateJar = MemoryCookieJar().apply { replaceAll(imported) }
        val candidateClient = client.newBuilder().cookieJar(candidateJar).build()
        return try {
            candidateClient.newCall(Request.Builder().url(MY_COURSES_URL).build()).execute().use { response ->
                val html = response.body?.string().orEmpty()
                if (!response.isSuccessful) return LoginResult(false, "VClass mengembalikan HTTP ${response.code}.")
                if (!VClassSessionPolicy.authenticated(response.request.url.toString(), html)) {
                    return LoginResult(false, "Login VClass belum selesai. Lanjutkan melalui halaman resmi.")
                }
                val name = extractDisplayName(Jsoup.parse(html, BASE_URL)).ifBlank { "Mahasiswa" }
                synchronized(this) {
                    if (version != generation.get()) return LoginResult(false, "Login dibatalkan.")
                    if (sameAccountOnly && restoredDisplayName.isNotBlank() && restoredDisplayName != name) {
                        return LoginResult(false, "Akun pada halaman resmi berbeda. Keluar dari aplikasi lalu masuk kembali.")
                    }
                    generation.incrementAndGet()
                    client.dispatcher.cancelAll()
                    cookieJar.replaceAll(candidateJar.getAll())
                    try { saveCurrentSession(name) } catch (e: Exception) { clearSession(); throw e }
                    changes.value = generation.get()
                }
                LoginResult(true, "Login VClass berhasil.", name)
            }
        } catch (_: Exception) {
            LoginResult(false, "Sesi VClass belum dapat diverifikasi. Periksa koneksi dan coba lagi.")
        }
    }

    fun getMyCourses(): CoursesResult {
        val version = generation.get()
        return try {
            val page =
                getAuthenticatedPage(
                    MY_COURSES_URL
                )

            if (!page.success) {
                return CoursesResult(
                    success = false,
                    message = page.message,
                    sessionExpired = page.sessionExpired
                )
            }

            val sesskey =
                extractSesskey(
                    page.html
                )

            if (sesskey.isNotBlank()) {
                val ajaxCourses =
                    getCoursesViaAjax(
                        sesskey
                    )

                if (!isSessionCurrent(version)) throw SessionExpiredException()
                if (ajaxCourses != null) {
                    return CoursesResult(true, ajaxCourses, if (ajaxCourses.isEmpty()) "Belum ada mata kuliah terdaftar." else "${ajaxCourses.size} mata kuliah ditemukan.")
                }
            }

            val htmlCourses =
                getCoursesViaHtml(
                    page.html
                )

            val htmlCourseText = Jsoup.parse(page.html).select("#region-main, [role=main]").text().lowercase()
            val explicitlyEmpty = "no courses" in htmlCourseText || "not enrolled" in htmlCourseText ||
                "tidak ada kursus" in htmlCourseText || "tidak ada mata kuliah" in htmlCourseText ||
                "belum ada mata kuliah" in htmlCourseText || "belum terdaftar" in htmlCourseText
            if (htmlCourses.isEmpty() && !explicitlyEmpty) {
                return CoursesResult(false, message = "Daftar kelas belum dapat dibaca dari VClass. Coba sinkronkan kembali.")
            }
            if (!isSessionCurrent(version)) throw SessionExpiredException()
            CoursesResult(
                success = true,
                courses = htmlCourses,
                message =
                    if (htmlCourses.isNotEmpty()) {
                        "${htmlCourses.size} mata kuliah ditemukan."
                    } else {
                        "Belum ada mata kuliah ditemukan."
                    }
            )
        } catch (_: SessionExpiredException) {
            synchronized(this) { if (version == generation.get()) clearSession() }
            return CoursesResult(false, message = "Sesi VClass berakhir. Masuk kembali dengan Microsoft.", sessionExpired = true)
        } catch (_: Exception) {
            CoursesResult(
                success = false,
                message = "Tidak dapat menghubungi VClass. Periksa koneksi dan coba lagi."
            )
        }
    }

    fun searchCourses(query: String, pageIndex: Int = 0): SearchCoursesResult {
        val value = query.trim()
        if (value.length < 2 || value.length > 200 || pageIndex !in 0..1000) {
            return SearchCoursesResult(false, message = "Masukkan 2–200 karakter untuk mencari kelas.")
        }
        return try {
            val url = COURSE_SEARCH_URL.toHttpUrl().newBuilder()
                .addQueryParameter("search", value).addQueryParameter("page", pageIndex.toString()).build()
            val page = getAuthenticatedPage(url.toString())
            if (!page.success) return SearchCoursesResult(false, message = page.message, sessionExpired = page.sessionExpired)
            parseCatalogPage(page.html, value, pageIndex)
        } catch (_: Exception) {
            SearchCoursesResult(false, message = "Tidak dapat mencari kelas. Periksa koneksi dan coba lagi.")
        }
    }

    internal fun parseCatalogPage(html: String, query: String, currentPage: Int): SearchCoursesResult {
        val courses = parseSearchCourses(html)
        val doc = Jsoup.parse(html, COURSE_SEARCH_URL)
        val text = doc.select("#region-main, [role=main]").text().ifBlank { doc.body().text() }.lowercase()
        val empty = "no courses" in text || "no results" in text || "tidak ada" in text ||
            "tidak ditemukan" in text || Regex("(?:search results|hasil pencarian)\\s*:\\s*0").containsMatchIn(text)
        if (courses.isEmpty() && !empty) {
            return SearchCoursesResult(false, message = "Format hasil pencarian VClass belum dapat dibaca. Coba lagi atau gunakan pencarian resmi.")
        }
        return SearchCoursesResult(true, courses,
            if (courses.isEmpty()) "Tidak ada kelas yang cocok." else "${courses.size} kelas ditemukan.",
            nextPage = nextSearchPage(html, query, currentPage))
    }

    internal fun nextSearchPage(html: String, query: String, currentPage: Int): Int? =
        Jsoup.parse(html, COURSE_SEARCH_URL).select(".pagination a[href], .paging a[href], a[rel=next]")
            .mapNotNull { node ->
                VClassSessionPolicy.officialUrl(node.absUrl("href"))?.takeIf {
                    it.encodedPath == "/course/search.php" && it.queryParameter("search") == query
                }?.queryParameter("page")?.toIntOrNull()
            }.filter { it > currentPage && it <= 1000 }.minOrNull()

    private val enrollingCourseIds = mutableSetOf<String>()

    internal fun inspectEnrollment(course: VClassCourse): EnrollmentResult {
        val enrolled = getMyCourses()
        if (!enrolled.success) return EnrollmentResult(false, message = enrolled.message)
        if (enrolled.courses.any { it.id == course.id }) {
            return EnrollmentResult(true, EnrollmentState.ALREADY_ENROLLED, "Sudah terdaftar", course.url)
        }
        val url = VClassSessionPolicy.officialUrl(course.url)
            ?: return EnrollmentResult(false, message = "URL kelas tidak valid.")
        if (url.encodedPath != "/course/view.php" || url.queryParameter("id") != course.id) {
            return EnrollmentResult(false, message = "ID kelas tidak sesuai dengan URL VClass.")
        }
        val page = getAuthenticatedPage(url.toString())
        if (!page.success) return EnrollmentResult(false, message = page.message)
        return EnrollmentParser.parse(page.html, page.finalUrl, course.id)
    }

    internal fun enrollInCourse(course: VClassCourse, enrollmentKey: String): EnrollmentResult {
        synchronized(enrollingCourseIds) {
            if (!enrollingCourseIds.add(course.id)) return EnrollmentResult(false, message = "Pendaftaran kelas sedang diproses.")
        }
        val version = generation.get()
        return try {
            val inspection = inspectEnrollment(course)
            if (!inspection.success || inspection.state == EnrollmentState.ALREADY_ENROLLED) return inspection
            val form = inspection.form ?: return inspection.copy(success = false)
            if (form.keyField != null && enrollmentKey.isBlank()) {
                return inspection.copy(success = false, message = "Masukkan kunci pendaftaran dari dosen.")
            }
            val body = FormBody.Builder()
            form.fields.forEach { (name, value) -> body.add(name, value) }
            form.keyField?.let { body.add(it, enrollmentKey) }
            if (!isSessionCurrent(version)) return EnrollmentResult(false, message = "Sesi VClass telah berubah. Masuk kembali.")
            val error = client.newCall(Request.Builder().url(form.action).header("Referer", inspection.officialUrl)
                .post(body.build()).build()).execute().use { response ->
                val html = response.body?.string().orEmpty()
                if (!response.isSuccessful) return EnrollmentResult(false, message = "VClass mengembalikan HTTP ${response.code}.")
                if (!VClassSessionPolicy.authenticated(response.request.url.toString(), html)) throw SessionExpiredException()
                Jsoup.parse(html).select(".ferror, .invalid-feedback, .alert-danger, .errormessage").text()
            }
            val verified = getMyCourses()
            if (verified.success && verified.courses.any { it.id == course.id } && isSessionCurrent(version)) {
                EnrollmentResult(true, EnrollmentState.ALREADY_ENROLLED, "Kelas berhasil ditambahkan dan terverifikasi di Kelas saya.", course.url)
            } else EnrollmentResult(false, inspection.state,
                if (!verified.success) verified.message else error.ifBlank { "VClass belum mengonfirmasi pendaftaran. Periksa kunci atau gunakan halaman resmi." },
                inspection.officialUrl)
        } catch (_: SessionExpiredException) {
            synchronized(this) { if (version == generation.get()) clearSession() }
            EnrollmentResult(false, message = "Sesi VClass berakhir. Masuk kembali dengan Microsoft.")
        } catch (_: Exception) {
            EnrollmentResult(false, message = "Pendaftaran belum dapat diverifikasi. Periksa Kelas saya sebelum mencoba kembali.")
        } finally { synchronized(enrollingCourseIds) { enrollingCourseIds.remove(course.id) } }
    }

    fun getDeadlines(): DeadlinesResult {
        val version = generation.get()
        return try {
            val myPage =
                getAuthenticatedPage(
                    MY_COURSES_URL
                )

            if (!myPage.success) {
                return DeadlinesResult(
                    success = false,
                    message = myPage.message,
                    sessionExpired = myPage.sessionExpired
                )
            }

            val sesskey =
                extractSesskey(
                    myPage.html
                )

            if (sesskey.isNotBlank()) {
                val ajaxDeadlines =
                    getDeadlinesViaAjax(
                        sesskey
                    )

                if (!isSessionCurrent(version)) throw SessionExpiredException()
                if (ajaxDeadlines != null) {
                    return DeadlinesResult(
                        success = true,
                        deadlines = ajaxDeadlines,
                        message = "${ajaxDeadlines.size} deadline aktif ditemukan."
                    )
                }
            }

            val upcomingPage =
                getAuthenticatedPage(
                    UPCOMING_URL
                )

            if (!upcomingPage.success) {
                return DeadlinesResult(
                    success = false,
                    message = upcomingPage.message,
                    sessionExpired = upcomingPage.sessionExpired
                )
            }

            val htmlDeadlines =
                getDeadlinesViaHtml(
                    upcomingPage.html
                )

            if (!isSessionCurrent(version)) throw SessionExpiredException()
            val calendarText = Jsoup.parse(upcomingPage.html).select("#region-main, [role=main]").text().lowercase()
            val noEvents = "no upcoming events" in calendarText || "no events" in calendarText ||
                "tidak ada" in calendarText || "tidak ditemukan" in calendarText
            if (htmlDeadlines.isEmpty() && !noEvents) {
                return DeadlinesResult(false, message = "Format deadline VClass belum dapat dibaca. Coba muat ulang tugas.")
            }
            DeadlinesResult(
                success = true,
                deadlines = htmlDeadlines,
                message =
                    if (htmlDeadlines.isNotEmpty()) {
                        "${htmlDeadlines.size} deadline aktif ditemukan."
                    } else {
                        "Tidak ada deadline aktif saat ini."
                    }
            )
        } catch (_: SessionExpiredException) {
            synchronized(this) { if (version == generation.get()) clearSession() }
            return DeadlinesResult(false, message = "Sesi VClass berakhir. Masuk kembali dengan Microsoft.", sessionExpired = true)
        } catch (_: Exception) {
            DeadlinesResult(
                success = false,
                message = "Tidak dapat menghubungi VClass. Periksa koneksi dan coba lagi."
            )
        }
    }

    private fun getDeadlinesViaAjax(sesskey: String): List<VClassDeadline>? {
        val now = Instant.now().epochSecond
        val until = now + 120L * 86400L
        val deadlines = mutableListOf<VClassDeadline>()
        var cursor = 0L
        repeat(20) {
            val args = JSONObject().put("timesortfrom", now).put("timesortto", until)
                .put("limitnum", 50).put("aftereventid", cursor).put("limittononsuspendedevents", true)
            val payload = JSONArray().put(JSONObject().put("index", 0)
                .put("methodname", "core_calendar_get_action_events_by_timesort").put("args", args)).toString()
            val url = AJAX_URL.toHttpUrl().newBuilder().addQueryParameter("sesskey", sesskey)
                .addQueryParameter("info", "core_calendar_get_action_events_by_timesort").build()
            val text = client.newCall(Request.Builder().url(url).header("Referer", MY_COURSES_URL)
                .header("X-Requested-With", "XMLHttpRequest")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType())).build())
                .execute().use { response ->
                    if (response.code == 401 || response.code == 403) throw SessionExpiredException()
                    if (!response.isSuccessful) return null
                    response.body?.string().orEmpty()
                }
            val item = try { JSONArray(text).optJSONObject(0) } catch (_: Exception) { return null } ?: return null
            if (item.optBoolean("error", false)) {
                val code = item.optJSONObject("exception")?.optString("errorcode").orEmpty()
                if (code in setOf("requireloginerror", "invalidsesskey", "servicerequireslogin")) throw SessionExpiredException()
                return null
            }
            val data = item.optJSONObject("data") ?: return null
            val events = data.optJSONArray("events") ?: return null
            deadlines += parseDeadlineEvents(events, now)
            if (events.length() < 50) return deadlines.distinctBy { it.id }.sortedBy { it.deadlineTimestamp }
            val next = data.optLong("lastid", events.optJSONObject(events.length() - 1)?.optLong("id", 0L) ?: 0L)
            if (next == 0L || next == cursor) throw java.io.IOException("VClass calendar pagination did not advance")
            cursor = next
        }
        throw java.io.IOException("VClass calendar pagination limit exceeded")
    }

    internal fun parseDeadlineEvents(events: JSONArray, nowSeconds: Long = Instant.now().epochSecond): List<VClassDeadline> {
        val deadlines = mutableListOf<VClassDeadline>()
        for (index in 0 until events.length()) {
            val event = events.optJSONObject(index) ?: continue
            val action = event.optJSONObject("action")
            val course = event.optJSONObject("course")
            val rawTitle = event.optString("name").ifBlank { action?.optString("name").orEmpty() }
            val title = Jsoup.parse(rawTitle).text().trim().ifBlank { "Aktivitas VClass" }
            val rawUrl = action?.optString("url").orEmpty().ifBlank { event.optString("url") }.ifBlank { UPCOMING_URL }
            val url = VClassSessionPolicy.officialUrl(rawUrl)?.toString() ?: continue
            val rawCourse = course?.optString("fullnamedisplay").orEmpty()
                .ifBlank { course?.optString("fullname").orEmpty() }.ifBlank { "Mata Kuliah Umum" }
            val parsedCourse = parseCourseParts(rawCourse)
            val timestamp = event.optLong("timesort", 0L).takeIf { it > 0L } ?: event.optLong("timestart", 0L)
            if (timestamp <= nowSeconds || timestamp > 253402300799L) continue
            val time = createDeadlineTimeInfo(timestamp)
            if (time.urgency == "expired") continue
            val type = classifyActivityType(url, title, event.optString("eventtype").ifBlank { event.optString("modulename") })
            deadlines.add(VClassDeadline(
                id = deadlineId(url, timestamp), title = title,
                courseName = parsedCourse.name, courseCode = parsedCourse.code,
                courseId = course?.optString("id").orEmpty(), semester = parsedCourse.semester,
                type = type, deadlineTimestamp = timestamp, deadlineIso = time.iso,
                deadlineDisplay = time.display, timeLeft = time.timeLeft, urgency = time.urgency, url = url
            ))
        }
        return deadlines.distinctBy { it.id }.sortedBy { it.deadlineTimestamp }
    }

    internal fun deadlineId(url: String, timestamp: Long): String {
        val parsed = VClassSessionPolicy.officialUrl(url)
        val identity = if (parsed?.encodedPath?.startsWith("/mod/") == true) {
            parsed.newBuilder().query(null).addQueryParameter("id", parsed.queryParameter("id").orEmpty()).build().toString()
        } else url
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest("$identity|$timestamp".toByteArray(Charsets.UTF_8))
        return "task-vclass-" + digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    internal fun getDeadlinesViaHtml(
        html: String
    ): List<VClassDeadline> {
        val document =
            Jsoup.parse(
                html,
                UPCOMING_URL
            )

        val deadlines =
            mutableListOf<VClassDeadline>()

        val seenIds = mutableSetOf<String>()

        val eventNodes =
            document.select(
                ".event, .calendar_event, [data-region=event-list-item], [data-region=event-list-content] .card"
            )

        for (
            node in eventNodes
        ) {
            val activityLink =
                node.selectFirst(
                    "a[href*='/mod/assign/'], a[href*='/mod/quiz/'], a[href*='/mod/forum/']"
                )
                    ?: continue

            val url =
                activityLink
                    .absUrl(
                        "href"
                    )
                    .ifBlank {
                        activityLink.attr(
                            "href"
                        )
                    }

            if (VClassSessionPolicy.officialUrl(url) == null) continue

            val title =
                activityLink
                    .text()
                    .trim()
                    .ifBlank {
                        "Tugas VClass"
                    }

            val courseNode =
                node.selectFirst(
                    ".course, .course-name, .event-course, [class*=course]"
                )

            val rawCourse =
                courseNode
                    ?.text()
                    ?.trim()
                    .orEmpty()

            val parsedCourse =
                parseCourseParts(
                    rawCourse
                )

            val courseLink =
                courseNode
                    ?.selectFirst(
                        "a[href*='course/view.php']"
                    )

            val courseId =
                extractCourseId(
                    courseLink
                        ?.attr(
                            "href"
                        )
                        .orEmpty()
                )

            val dateNode =
                node.selectFirst(
                    ".date, .event-date, .description, [class*=date]"
                )

            val rawDate =
                dateNode
                    ?.text()
                    ?.trim()
                    .orEmpty()

            val timeNode = node.selectFirst("time[datetime], [data-timestamp], [data-time]")
            val timeValue = timeNode?.attr("datetime").orEmpty().ifBlank {
                timeNode?.attr("data-timestamp").orEmpty().ifBlank { timeNode?.attr("data-time").orEmpty() }
            }
            val timestamp = timeValue.toLongOrNull() ?: parseMoodleDate(timeValue) ?: parseMoodleDate(rawDate) ?: continue
            if (timestamp <= 0L || timestamp > 253402300799L) continue
            val id = deadlineId(normalizeUrl(url), timestamp)
            if (!seenIds.add(id)) continue

            val info =
                createDeadlineTimeInfo(
                    timestamp
                )

            if (
                info.urgency ==
                "expired"
            ) {
                continue
            }

            deadlines.add(
                VClassDeadline(
                    id =
                        id,
                    title =
                        title,
                    courseName =
                        parsedCourse.name
                            .ifBlank {
                                "Mata Kuliah"
                            },
                    courseCode =
                        parsedCourse.code,
                    courseId =
                        courseId,
                    semester =
                        parsedCourse.semester,
                    type =
                        classifyActivityType(
                            url = url,
                            title = title,
                            eventType = ""
                        ),
                    deadlineTimestamp =
                        timestamp,
                    deadlineIso =
                        info.iso,
                    deadlineDisplay =
                        info.display,
                    timeLeft =
                        info.timeLeft,
                    urgency =
                        info.urgency,
                    url =
                        normalizeUrl(
                            url
                        )
                )
            )
        }

        return deadlines.sortedBy {
            it.deadlineTimestamp
        }
    }

    internal fun createDeadlineTimeInfo(
        timestamp: Long,
        nowTimestamp: Long = Instant.now().epochSecond
    ): DeadlineTimeInfo {
        val deadline =
            Instant
                .ofEpochSecond(
                    timestamp
                )
                .atZone(
                    wibZone
                )

        val now = Instant.ofEpochSecond(nowTimestamp).atZone(wibZone)

        val seconds =
            deadline.toEpochSecond() -
                now.toEpochSecond()

        val formatter =
            DateTimeFormatter.ofPattern(
                "dd MMM yyyy, HH:mm 'WIB'",
                Locale.forLanguageTag("id-ID")
            )

        val display =
            deadline.format(
                formatter
            )

        val iso =
            deadline
                .toOffsetDateTime()
                .toString()

        if (seconds <= 0L) {
            return DeadlineTimeInfo(
                iso = iso,
                display = display,
                timeLeft = "Terlewat",
                urgency = "expired"
            )
        }

        if (seconds < 3600L) {
            val minutes =
                maxOf(
                    1L,
                    seconds / 60L
                )

            return DeadlineTimeInfo(
                iso = iso,
                display = display,
                timeLeft = "$minutes Menit Lagi",
                urgency = "urgent"
            )
        }

        if (seconds < 86400L) {
            val hours =
                seconds / 3600L

            return DeadlineTimeInfo(
                iso = iso,
                display = display,
                timeLeft = "$hours Jam Lagi",
                urgency = "urgent"
            )
        }

        if (seconds < 86400L * 2L) {
            return DeadlineTimeInfo(
                iso = iso,
                display = display,
                timeLeft = "Besok",
                urgency = "soon"
            )
        }

        if (seconds < 86400L * 3L) {
            return DeadlineTimeInfo(
                iso = iso,
                display = display,
                timeLeft = "Lusa",
                urgency = "soon"
            )
        }

        if (seconds < 86400L * 7L) {
            val days =
                seconds / 86400L

            return DeadlineTimeInfo(
                iso = iso,
                display = display,
                timeLeft = "$days Hari Lagi",
                urgency =
                    if (days <= 3L) {
                        "soon"
                    } else {
                        "safe"
                    }
            )
        }

        if (seconds < 86400L * 14L) {
            return DeadlineTimeInfo(
                iso = iso,
                display = display,
                timeLeft = "Minggu Depan",
                urgency = "safe"
            )
        }

        val weeks =
            seconds /
                (
                    86400L *
                        7L
                    )

        return DeadlineTimeInfo(
            iso = iso,
            display = display,
            timeLeft = "$weeks Minggu",
            urgency = "safe"
        )
    }

    internal fun parseMoodleDate(
        rawValue: String
    ): Long? {
        if (rawValue.isBlank()) {
            return null
        }

        val raw = rawValue.trim()
        runCatching { java.time.OffsetDateTime.parse(raw).toEpochSecond() }.getOrNull()?.let { return it }
        runCatching { Instant.parse(raw).epochSecond }.getOrNull()?.let { return it }
        val zone = when {
            Regex("\\bWITA\\b", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> ZoneId.of("Asia/Makassar")
            Regex("\\bWIT\\b", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> ZoneId.of("Asia/Jayapura")
            else -> wibZone
        }
        var value = raw

        val replacements =
            mapOf(
                "Januari" to "January",
                "Februari" to "February",
                "Pebruari" to "February",
                "Maret" to "March",
                "April" to "April",
                "Mei" to "May",
                "Juni" to "June",
                "Juli" to "July",
                "Agustus" to "August",
                "September" to "September",
                "Oktober" to "October",
                "November" to "November",
                "Nopember" to "November",
                "Desember" to "December"
            )

        for (
            replacement in replacements
        ) {
            value =
                value.replace(
                    replacement.key,
                    replacement.value,
                    ignoreCase = true
                )
        }

        value =
            value.replace(
                Regex(
                    "^(Senin|Selasa|Rabu|Kamis|Jumat|Sabtu|Minggu|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),?\\s*",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )

        value =
            value.replace(
                Regex(
                    "\\s*(WIB|WITA|WIT)",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
                .trim()

        val patterns =
            listOf(
                "d MMMM yyyy, HH:mm",
                "d MMM yyyy, HH:mm",
                "d MMMM yyyy HH:mm",
                "d MMM yyyy HH:mm",
                "d MMMM yyyy, h:mm a",
                "d MMM yyyy, h:mm a",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss"
            )

        for (
            pattern in patterns
        ) {
            try {
                val formatter = java.time.format.DateTimeFormatterBuilder().parseCaseInsensitive()
                    .appendPattern(pattern).toFormatter(Locale.ENGLISH)

                val local =
                    LocalDateTime.parse(
                        value,
                        formatter
                    )

                return local
                    .atZone(
                        zone
                    )
                    .toEpochSecond()
            } catch (_: Exception) {
            }
        }

        return null
    }

    private fun classifyActivityType(url: String, title: String, eventType: String): String {
        val path = VClassSessionPolicy.officialUrl(url)?.encodedPath.orEmpty()
        return when {
            path.startsWith("/mod/quiz/") -> "Kuis"
            path.startsWith("/mod/forum/") -> "Forum"
            path.startsWith("/mod/assign/") -> "Tugas"
            else -> {
                val text = "$title $eventType".lowercase()
                when {
                    "quiz" in text || "kuis" in text -> "Kuis"
                    "forum" in text || "diskusi" in text -> "Forum"
                    "assign" in text || "tugas" in text -> "Tugas"
                    else -> "Materi"
                }
            }
        }
    }

    private fun getCoursesViaAjax(
        sesskey: String
    ): List<VClassCourse>? {
        val classifications =
            listOf(
                "all",
                "inprogress",
                "future",
                "past"
            )

        val courses = mutableListOf<VClassCourse>()
        var validFallbackReplies = 0

        val seenIds =
            mutableSetOf<String>()

        for (
            classification in classifications
        ) {
            try {
                val args =
                    JSONObject()
                        .put(
                            "offset",
                            0
                        )
                        .put(
                            "limit",
                            0
                        )
                        .put(
                            "classification",
                            classification
                        )
                        .put(
                            "sort",
                            "fullname"
                        )
                        .put(
                            "customfieldname",
                            ""
                        )
                        .put(
                            "customfieldvalue",
                            ""
                        )

                val item =
                    JSONObject()
                        .put(
                            "index",
                            0
                        )
                        .put(
                            "methodname",
                            "core_course_get_enrolled_courses_by_timeline_classification"
                        )
                        .put(
                            "args",
                            args
                        )

                val payload =
                    JSONArray()
                        .put(
                            item
                        )
                        .toString()

                val body =
                    payload.toRequestBody(
                        "application/json; charset=utf-8"
                            .toMediaType()
                    )

                val url =
                    "$AJAX_URL?sesskey=$sesskey&info=core_course_get_enrolled_courses_by_timeline_classification"

                val request =
                    Request.Builder()
                        .url(url)
                        .header(
                            "Accept",
                            "application/json, text/javascript, */*; q=0.01"
                        )
                        .header(
                            "X-Requested-With",
                            "XMLHttpRequest"
                        )
                        .header(
                            "Referer",
                            MY_COURSES_URL
                        )
                        .post(
                            body
                        )
                        .build()

                val responseText =
                    client
                        .newCall(
                            request
                        )
                        .execute()
                        .use { response ->

                            if (response.code == 401 || response.code == 403) throw SessionExpiredException()
                            if (!response.isSuccessful) {
                                ""
                            } else {
                                response.body
                                    ?.string()
                                    .orEmpty()
                            }
                        }

                if (responseText.isBlank()) {
                    continue
                }

                val responseArray =
                    JSONArray(
                        responseText
                    )

                if (
                    responseArray.length() == 0
                ) {
                    continue
                }

                val responseItem =
                    responseArray
                        .optJSONObject(
                            0
                        )
                        ?: continue

                if (responseItem.optBoolean("error", false)) {
                    val errorCode = responseItem.optJSONObject("exception")?.optString("errorcode").orEmpty()
                    if (errorCode in setOf("requireloginerror", "invalidsesskey", "servicerequireslogin")) throw SessionExpiredException()
                    continue
                }

                val data =
                    responseItem
                        .optJSONObject(
                            "data"
                        )
                        ?: continue

                val rawCourses =
                    data.optJSONArray(
                        "courses"
                    )
                        ?: continue

                if (classification != "all") validFallbackReplies++
                for (
                    index in 0 until rawCourses.length()
                ) {
                    val courseJson =
                        rawCourses
                            .optJSONObject(
                                index
                            )
                            ?: continue

                    val courseId =
                        courseJson
                            .optString(
                                "id"
                            )
                            .trim()

                    if (
                        courseId.toLongOrNull()?.let { it > 1 } != true ||
                        courseId in seenIds
                    ) {
                        continue
                    }

                    val rawName =
                        courseJson
                            .optString(
                                "fullnamedisplay"
                            )
                            .ifBlank {
                                courseJson.optString(
                                    "fullname"
                                )
                            }
                            .ifBlank {
                                courseJson.optString(
                                    "shortname",
                                    "Course $courseId"
                                )
                            }

                    val urlValue =
                        courseJson
                            .optString(
                                "viewurl"
                            )
                            .ifBlank {
                                "$BASE_URL/course/view.php?id=$courseId"
                            }

                    seenIds.add(
                        courseId
                    )

                    courses.add(
                        normalizeCourse(
                            courseId = courseId,
                            rawName = rawName,
                            url = urlValue
                        )
                    )
                }

                if (classification == "all") return courses
            } catch (e: SessionExpiredException) {
                throw e
            } catch (_: Exception) {
            }
        }

        return courses.takeIf { validFallbackReplies == 3 }
    }

    internal fun getCoursesViaHtml(
        html: String
    ): List<VClassCourse> {
        val document =
            Jsoup.parse(
                html,
                MY_COURSES_URL
            )

        val courses =
            mutableListOf<VClassCourse>()

        val seenIds =
            mutableSetOf<String>()

        val cards =
            document.select(
                "div.card.dashboard-card[data-course-id], div[data-region=course-content][data-course-id]"
            )

        for (
            card in cards
        ) {
            var courseId =
                card.attr(
                    "data-course-id"
                )
                    .trim()

            val link =
                card.selectFirst(
                    "a.coursename[href], a[href*='course/view.php']"
                )

            if (courseId.isBlank()) {
                courseId =
                    extractCourseId(
                        link
                            ?.attr(
                                "href"
                            )
                            .orEmpty()
                    )
            }

            if (
                courseId.isBlank() ||
                courseId in seenIds
            ) {
                continue
            }

            val rawName =
                if (link != null) {
                    extractCourseName(
                        link
                    )
                } else {
                    ""
                }

            if (rawName.isBlank()) {
                continue
            }

            val url =
                link
                    ?.absUrl(
                        "href"
                    )
                    .orEmpty()
                    .ifBlank {
                        "$BASE_URL/course/view.php?id=$courseId"
                    }

            seenIds.add(
                courseId
            )

            courses.add(
                normalizeCourse(
                    courseId = courseId,
                    rawName = rawName,
                    url = url
                )
            )
        }

        if (courses.isNotEmpty()) {
            return courses
        }

        val links =
            document.select(
                "[data-region=course-content] a[href*='course/view.php'], .block_myoverview a[href*='course/view.php'], #mycourses a[href*='course/view.php']"
            )

        for (
            link in links
        ) {
            val href =
                link.attr(
                    "href"
                )

            val courseId =
                extractCourseId(
                    href
                )

            if (
                courseId.isBlank() ||
                courseId in seenIds
            ) {
                continue
            }

            val rawName =
                extractCourseName(
                    link
                )

            if (rawName.isBlank()) {
                continue
            }

            seenIds.add(
                courseId
            )

            courses.add(
                normalizeCourse(
                    courseId = courseId,
                    rawName = rawName,
                    url =
                        link.absUrl(
                            "href"
                        )
                            .ifBlank {
                                "$BASE_URL/course/view.php?id=$courseId"
                            }
                )
            )
        }

        return courses
    }

    internal fun parseSearchCourses(
        html: String
    ): List<VClassCourse> {
        val document =
            Jsoup.parse(
                html,
                COURSE_SEARCH_URL
            )

        val courses =
            mutableListOf<VClassCourse>()

        val seenIds =
            mutableSetOf<String>()

        val courseBoxes =
            document.select(
                ".coursebox, .course-search-result, [data-courseid], [data-course-id]"
            )

        for (
            box in courseBoxes
        ) {
            val link =
                findCourseLink(
                    box
                )
                    ?: continue

            val href =
                link.absUrl(
                    "href"
                )
                    .ifBlank {
                        link.attr(
                            "href"
                        )
                    }

            val courseId =
                extractCourseId(
                    href
                )

            if (
                courseId.isBlank() ||
                courseId in seenIds
            ) {
                continue
            }

            val rawName =
                extractCourseName(
                    link
                )

            if (rawName.isBlank()) {
                continue
            }

            seenIds.add(
                courseId
            )

            courses.add(
                normalizeCourse(
                    courseId = courseId,
                    rawName = rawName,
                    url = normalizeUrl(href)
                ).let { course ->
                    val instructor = box.select(".teachers a, .instructors a, [data-region=teacher]").text().trim()
                    if (instructor.isNotBlank()) course.copy(instructor = instructor) else course
                }
            )
        }

        if (courses.isNotEmpty()) {
            return courses
        }

        val links =
            document.select(
                "a[href*='course/view.php']"
            )

        for (
            link in links
        ) {
            val href =
                link.absUrl(
                    "href"
                )
                    .ifBlank {
                        link.attr(
                            "href"
                        )
                    }

            val courseId =
                extractCourseId(
                    href
                )

            if (
                courseId.isBlank() ||
                courseId in seenIds
            ) {
                continue
            }

            val rawName =
                extractCourseName(
                    link
                )

            if (
                rawName.isBlank() ||
                rawName.equals(
                    "View course",
                    ignoreCase = true
                )
            ) {
                continue
            }

            seenIds.add(
                courseId
            )

            courses.add(
                normalizeCourse(
                    courseId = courseId,
                    rawName = rawName,
                    url =
                        normalizeUrl(
                            href
                        )
                )
            )
        }

        return courses
    }

    private fun findCourseLink(
        element: Element
    ): Element? {
        val selectors =
            listOf(
                ".coursename a[href*='course/view.php']",
                "h3 a[href*='course/view.php']",
                "h4 a[href*='course/view.php']",
                "a[href*='course/view.php']"
            )

        for (
            selector in selectors
        ) {
            val result =
                element.selectFirst(
                    selector
                )

            if (result != null) {
                return result
            }
        }

        return null
    }

    internal fun normalizeCourse(
        courseId: String,
        rawName: String,
        url: String
    ): VClassCourse {
        val parsed =
            parseCourseParts(
                rawName
            )

        return VClassCourse(
            id = courseId,
            name = parsed.name,
            code = parsed.code,
            semester = parsed.semester,
            instructor = parsed.instructor,
            url =
                normalizeUrl(
                    url
                ),
            rawFullName =
                parsed.rawFullName,
            className = parsed.className
        )
    }

    private fun parseCourseParts(rawName: String): ParsedCourse {
        val clean = Jsoup.parse(rawName).text().replace(Regex("\\s+"), " ").trim()
        val parts = clean.split("|").map { it.trim() }.filter { it.isNotBlank() }
        val semesterPattern = Regex("^[A-Za-z]+\\s*\\d{4}\\s*/\\s*\\d{4}$")
        val classPattern = Regex("\\b[1-9][A-Z]{2}\\d{2}\\b", RegexOption.IGNORE_CASE)
        val className = classPattern.find(clean)?.value.orEmpty().uppercase()
        var semester = ""
        var code = ""
        var name = clean
        var instructor = ""
        if (parts.size >= 3 && semesterPattern.matches(parts[0])) {
            semester = parts[0]
            code = parts[1]
            val titleParts = parts.drop(2).toMutableList()
            if (titleParts.size >= 2 && !classPattern.matches(titleParts.last())) instructor = titleParts.removeAt(titleParts.lastIndex)
            name = titleParts.filterNot { classPattern.matches(it) }.joinToString(" | ")
        } else if (parts.size == 2 && Regex("[A-Za-z0-9_-]{3,}").matches(parts[0])) {
            code = parts[0]
            name = parts[1]
        }
        name = name.replace(Regex("\\s*\\*\\s*$"), "").trim().ifBlank { clean }
        return ParsedCourse(name, code, semester, instructor, clean, className)
    }

    private fun extractCourseName(
        link: Element
    ): String {
        val cloned =
            link.clone()

        cloned
            .select(
                ".sr-only, .accesshide, .visually-hidden"
            )
            .remove()

        return cloned
            .text()
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    private fun extractSesskey(
        html: String
    ): String {
        val document =
            Jsoup.parse(
                html
            )

        val inputValue =
            document
                .selectFirst(
                    "input[name=sesskey]"
                )
                ?.attr(
                    "value"
                )
                .orEmpty()

        if (inputValue.isNotBlank()) {
            return inputValue
        }

        val patterns =
            listOf(
                Regex(
                    "\"sesskey\"\\s*:\\s*\"([^\"]+)\""
                ),
                Regex(
                    "sesskey=([a-zA-Z0-9]+)"
                ),
                Regex(
                    "M\\.cfg\\.sesskey\\s*=\\s*\"([^\"]+)\""
                )
            )

        for (
            pattern in patterns
        ) {
            val value =
                pattern
                    .find(
                        html
                    )
                    ?.groupValues
                    ?.getOrNull(
                        1
                    )
                    .orEmpty()

            if (value.isNotBlank()) {
                return value
            }
        }

        return ""
    }

    internal fun extractCourseId(url: String): String {
        val official = VClassSessionPolicy.officialUrl(url) ?: return ""
        if (official.encodedPath != "/course/view.php") return ""
        return official.queryParameter("id")?.takeIf { it.toLongOrNull()?.let { id -> id > 1 } == true }.orEmpty()
    }

    private fun extractDisplayName(
        document: Document
    ): String {
        val selectors =
            listOf(
                "[data-region=usermenu] .usertext",
                ".userbutton .usertext",
                ".usertext",
                ".usermenu .usertext"
            )

        for (
            selector in selectors
        ) {
            val name =
                document
                    .selectFirst(
                        selector
                    )
                    ?.text()
                    ?.trim()
                    .orEmpty()

            if (name.isNotBlank()) {
                return name
            }
        }

        return ""
    }

    private fun normalizeUrl(url: String): String =
        VClassSessionPolicy.officialUrl(url)?.toString().orEmpty()

    private fun getAuthenticatedPage(url: String): PageResult {
        val version = generation.get()
        return try {
            val official = VClassSessionPolicy.officialUrl(url)
                ?: return PageResult(false, message = "URL VClass tidak valid.")
            client.newCall(Request.Builder().url(official).get().build()).execute().use { response ->
                val html = response.body?.string().orEmpty()
                if (response.code == 401 || response.code == 403 && VClassSessionPolicy.isAuthPath(response.request.url.encodedPath)) {
                    throw SessionExpiredException()
                }
                if (!response.isSuccessful) return PageResult(false, message = "VClass mengembalikan HTTP ${response.code}.")
                if (!VClassSessionPolicy.authenticated(response.request.url.toString(), html)) throw SessionExpiredException()
                if (!isSessionCurrent(version)) throw SessionExpiredException()
                PageResult(true, html = html, finalUrl = response.request.url.toString())
            }
        } catch (_: SessionExpiredException) {
            synchronized(this) { if (version == generation.get()) clearSession() }
            PageResult(false, message = "Sesi VClass berakhir. Masuk kembali dengan Microsoft.", sessionExpired = true)
        } catch (_: Exception) {
            PageResult(false, message = "Tidak dapat menghubungi VClass. Periksa koneksi dan coba lagi.")
        }
    }

}

data class LoginResult(
    val success: Boolean,
    val message: String,
    val displayName: String = "",
    val sessionExpired: Boolean = false
)

data class CoursesResult(
    val success: Boolean,
    val courses: List<VClassCourse> = emptyList(),
    val message: String = "",
    val sessionExpired: Boolean = false
)

data class SearchCoursesResult(
    val success: Boolean,
    val courses: List<VClassCourse> = emptyList(),
    val message: String = "",
    val sessionExpired: Boolean = false,
    val nextPage: Int? = null
)

data class DeadlinesResult(
    val success: Boolean,
    val deadlines: List<VClassDeadline> = emptyList(),
    val message: String = "",
    val sessionExpired: Boolean = false
)

data class VClassCourse(
    val id: String,
    val name: String,
    val code: String,
    val semester: String,
    val instructor: String,
    val url: String,
    val rawFullName: String,
    val className: String = ""
)

data class VClassDeadline(
    val id: String,
    val title: String,
    val courseName: String,
    val courseCode: String,
    val courseId: String,
    val semester: String,
    val type: String,
    val deadlineTimestamp: Long,
    val deadlineIso: String,
    val deadlineDisplay: String,
    val timeLeft: String,
    val urgency: String,
    val url: String
)

private data class ParsedCourse(
    val name: String,
    val code: String,
    val semester: String,
    val instructor: String,
    val rawFullName: String,
    val className: String = ""
)

internal data class DeadlineTimeInfo(
    val iso: String,
    val display: String,
    val timeLeft: String,
    val urgency: String
)

private data class PageResult(
    val success: Boolean,
    val finalUrl: String = "",
    val html: String = "",
    val message: String = "",
    val sessionExpired: Boolean = false
)


private data class SessionVersion(val value: Long)
