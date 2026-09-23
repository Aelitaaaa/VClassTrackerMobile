package com.dzaky.vclasstracker.data.remote

import android.content.Context
import com.dzaky.vclasstracker.data.local.VClassSessionStore
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
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

    private const val LOGIN_URL =
        "$BASE_URL/login/index.php"

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

    private val cookieJar =
        MemoryCookieJar()

    private var applicationContext: Context? =
        null

    private var restoredDisplayName =
        ""

    private val client =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request =
                    chain.request()
                        .newBuilder()
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36"
                        )
                        .header(
                            "Accept-Language",
                            "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
                        )
                        .build()

                chain.proceed(request)
            }
            .build()

    fun initialize(
        context: Context
    ) {
        applicationContext =
            context.applicationContext

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
                ?: return

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

    fun clearSession() {
        cookieJar.clear()

        applicationContext?.let { context ->
            VClassSessionStore(
                context
            ).clear()
        }

        restoredDisplayName =
            ""
    }

    fun login(
        username: String,
        password: String
    ): LoginResult {
        return try {
            val loginPageRequest =
                Request.Builder()
                    .url(LOGIN_URL)
                    .get()
                    .build()

            val loginPageHtml =
                client
                    .newCall(
                        loginPageRequest
                    )
                    .execute()
                    .use { response ->

                        if (!response.isSuccessful) {
                            return LoginResult(
                                success = false,
                                message = "Gagal membuka halaman login VClass."
                            )
                        }

                        response.body
                            ?.string()
                            .orEmpty()
                    }

            val document =
                Jsoup.parse(
                    loginPageHtml
                )

            val loginToken =
                document
                    .selectFirst(
                        "input[name=logintoken]"
                    )
                    ?.attr(
                        "value"
                    )
                    .orEmpty()

            val formBuilder =
                FormBody.Builder()
                    .add(
                        "username",
                        username
                    )
                    .add(
                        "password",
                        password
                    )
                    .add(
                        "rememberusername",
                        "0"
                    )
                    .add(
                        "anchor",
                        ""
                    )

            if (loginToken.isNotBlank()) {
                formBuilder.add(
                    "logintoken",
                    loginToken
                )
            }

            val loginRequest =
                Request.Builder()
                    .url(LOGIN_URL)
                    .post(
                        formBuilder.build()
                    )
                    .build()

            client
                .newCall(
                    loginRequest
                )
                .execute()
                .use { response ->

                    val html =
                        response.body
                            ?.string()
                            .orEmpty()

                    val finalUrl =
                        response.request
                            .url
                            .toString()

                    val resultDocument =
                        Jsoup.parse(
                            html,
                            BASE_URL
                        )

                    val stillHasPasswordField =
                        resultDocument.selectFirst(
                            "input[name=password]"
                        ) != null

                    val hasLogoutLink =
                        resultDocument.selectFirst(
                            "a[href*=login/logout.php]"
                        ) != null

                    val errorText =
                        resultDocument
                            .selectFirst(
                                ".loginerrors, .alert-danger, .errormessage"
                            )
                            ?.text()
                            .orEmpty()

                    if (
                        finalUrl.contains(
                            "login/index.php"
                        ) ||
                        (
                            stillHasPasswordField &&
                                !hasLogoutLink
                            )
                    ) {
                        return LoginResult(
                            success = false,
                            message =
                                if (errorText.isNotBlank()) {
                                    errorText
                                } else {
                                    "Username atau password VClass salah."
                                }
                        )
                    }

                    val displayName =
                        extractDisplayName(
                            resultDocument
                        )

                    val finalDisplayName =
                        displayName.ifBlank {
                            username
                        }

                    saveCurrentSession(
                        finalDisplayName
                    )

                    LoginResult(
                        success = true,
                        message =
                            if (displayName.isNotBlank()) {
                                "Login berhasil sebagai $displayName"
                            } else {
                                "Login VClass berhasil."
                            },
                        displayName =
                            finalDisplayName
                    )
                }
        } catch (e: Exception) {
            LoginResult(
                success = false,
                message =
                    e.message
                        ?: "Terjadi kesalahan saat menghubungi VClass."
            )
        }
    }

    fun getMyCourses(): CoursesResult {
        return try {
            val page =
                getAuthenticatedPage(
                    MY_COURSES_URL
                )

            if (!page.success) {
                return CoursesResult(
                    success = false,
                    message = page.message
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

                if (ajaxCourses.isNotEmpty()) {
                    return CoursesResult(
                        success = true,
                        courses = ajaxCourses,
                        message = "${ajaxCourses.size} mata kuliah ditemukan."
                    )
                }
            }

            val htmlCourses =
                getCoursesViaHtml(
                    page.html
                )

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
        } catch (e: Exception) {
            CoursesResult(
                success = false,
                message =
                    e.message
                        ?: "Gagal mengambil daftar mata kuliah."
            )
        }
    }

    fun searchCourses(
        query: String
    ): SearchCoursesResult {
        if (query.trim().length < 2) {
            return SearchCoursesResult(
                success = false,
                message = "Masukkan minimal 2 karakter untuk mencari kelas."
            )
        }

        return try {
            val encodedQuery =
                URLEncoder.encode(
                    query.trim(),
                    "UTF-8"
                )

            val searchUrl =
                "$COURSE_SEARCH_URL?search=$encodedQuery"

            val page =
                getAuthenticatedPage(
                    searchUrl
                )

            if (!page.success) {
                return SearchCoursesResult(
                    success = false,
                    message = page.message
                )
            }

            val courses =
                parseSearchCourses(
                    page.html
                )

            SearchCoursesResult(
                success = true,
                courses = courses,
                message =
                    if (courses.isEmpty()) {
                        "Tidak ada kelas yang cocok."
                    } else {
                        "${courses.size} kelas ditemukan."
                    }
            )
        } catch (e: Exception) {
            SearchCoursesResult(
                success = false,
                message =
                    e.message
                        ?: "Terjadi kesalahan saat mencari kelas."
            )
        }
    }

    fun getDeadlines(): DeadlinesResult {
        return try {
            val myPage =
                getAuthenticatedPage(
                    MY_COURSES_URL
                )

            if (!myPage.success) {
                return DeadlinesResult(
                    success = false,
                    message = myPage.message
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

                if (ajaxDeadlines.isNotEmpty()) {
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
                    message = upcomingPage.message
                )
            }

            val htmlDeadlines =
                getDeadlinesViaHtml(
                    upcomingPage.html
                )

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
        } catch (e: Exception) {
            DeadlinesResult(
                success = false,
                message =
                    e.message
                        ?: "Gagal mengambil deadline VClass."
            )
        }
    }

    private fun getDeadlinesViaAjax(
        sesskey: String
    ): List<VClassDeadline> {
        return try {
            val now =
                Instant.now()
                    .epochSecond

            val until =
                now +
                    (
                        120L *
                            24L *
                            60L *
                            60L
                        )

            val args =
                JSONObject()
                    .put(
                        "timesortfrom",
                        now
                    )
                    .put(
                        "timesortto",
                        until
                    )
                    .put(
                        "limitnum",
                        50
                    )
                    .put(
                        "limittononsuspendedevents",
                        true
                    )

            val requestItem =
                JSONObject()
                    .put(
                        "index",
                        0
                    )
                    .put(
                        "methodname",
                        "core_calendar_get_action_events_by_timesort"
                    )
                    .put(
                        "args",
                        args
                    )

            val payload =
                JSONArray()
                    .put(
                        requestItem
                    )
                    .toString()

            val body =
                payload.toRequestBody(
                    "application/json; charset=utf-8"
                        .toMediaType()
                )

            val url =
                "$AJAX_URL?sesskey=$sesskey&info=core_calendar_get_action_events_by_timesort"

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
                    .post(body)
                    .build()

            val responseText =
                client
                    .newCall(
                        request
                    )
                    .execute()
                    .use { response ->

                        if (!response.isSuccessful) {
                            return emptyList()
                        }

                        response.body
                            ?.string()
                            .orEmpty()
                    }

            if (responseText.isBlank()) {
                return emptyList()
            }

            val responseArray =
                JSONArray(
                    responseText
                )

            if (responseArray.length() == 0) {
                return emptyList()
            }

            val responseItem =
                responseArray
                    .optJSONObject(
                        0
                    )
                    ?: return emptyList()

            if (
                responseItem.optBoolean(
                    "error",
                    false
                )
            ) {
                return emptyList()
            }

            val data =
                responseItem
                    .optJSONObject(
                        "data"
                    )
                    ?: return emptyList()

            val events =
                data.optJSONArray(
                    "events"
                )
                    ?: return emptyList()

            val deadlines =
                mutableListOf<VClassDeadline>()

            for (
                index in 0 until events.length()
            ) {
                val event =
                    events
                        .optJSONObject(
                            index
                        )
                        ?: continue

                val action =
                    event.optJSONObject(
                        "action"
                    )

                val courseInfo =
                    event.optJSONObject(
                        "course"
                    )

                val title =
                    event
                        .optString(
                            "name"
                        )
                        .ifBlank {
                            action
                                ?.optString(
                                    "name"
                                )
                                .orEmpty()
                        }
                        .ifBlank {
                            "Tugas VClass"
                        }

                val url =
                    action
                        ?.optString(
                            "url"
                        )
                        .orEmpty()
                        .ifBlank {
                            event.optString(
                                "url"
                            )
                        }
                        .ifBlank {
                            UPCOMING_URL
                        }

                val rawCourse =
                    courseInfo
                        ?.optString(
                            "fullnamedisplay"
                        )
                        .orEmpty()
                        .ifBlank {
                            courseInfo
                                ?.optString(
                                    "fullname"
                                )
                                .orEmpty()
                        }
                        .ifBlank {
                            "Mata Kuliah Umum"
                        }

                val parsedCourse =
                    parseCourseParts(
                        rawCourse
                    )

                val courseId =
                    courseInfo
                        ?.optString(
                            "id"
                        )
                        .orEmpty()

                val timestamp =
                    if (
                        event.has(
                            "timesort"
                        ) &&
                        !event.isNull(
                            "timesort"
                        )
                    ) {
                        event.optLong(
                            "timesort",
                            0L
                        )
                    } else {
                        event.optLong(
                            "timestart",
                            0L
                        )
                    }

                if (timestamp <= 0L) {
                    continue
                }

                val deadlineInfo =
                    createDeadlineTimeInfo(
                        timestamp
                    )

                if (
                    deadlineInfo.urgency ==
                    "expired"
                ) {
                    continue
                }

                val eventType =
                    event
                        .optString(
                            "eventtype"
                        )
                        .ifBlank {
                            event.optString(
                                "modulename"
                            )
                        }

                val type =
                    classifyActivityType(
                        url = url,
                        title = title,
                        eventType = eventType
                    )

                val eventId =
                    event
                        .optString(
                            "id"
                        )
                        .ifBlank {
                            (index + 1)
                                .toString()
                        }

                deadlines.add(
                    VClassDeadline(
                        id =
                            "task-vclass-$eventId",
                        title =
                            title,
                        courseName =
                            parsedCourse.name,
                        courseCode =
                            parsedCourse.code,
                        courseId =
                            courseId,
                        semester =
                            parsedCourse.semester,
                        type =
                            type,
                        deadlineTimestamp =
                            timestamp,
                        deadlineIso =
                            deadlineInfo.iso,
                        deadlineDisplay =
                            deadlineInfo.display,
                        timeLeft =
                            deadlineInfo.timeLeft,
                        urgency =
                            deadlineInfo.urgency,
                        url =
                            normalizeUrl(
                                url
                            )
                    )
                )
            }

            deadlines.sortedBy {
                it.deadlineTimestamp
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getDeadlinesViaHtml(
        html: String
    ): List<VClassDeadline> {
        val document =
            Jsoup.parse(
                html,
                UPCOMING_URL
            )

        val deadlines =
            mutableListOf<VClassDeadline>()

        val seenUrls =
            mutableSetOf<String>()

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

            if (
                url.isBlank() ||
                url in seenUrls
            ) {
                continue
            }

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
                        "a[href*='course/view.php?id=']"
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

            val timestamp =
                parseMoodleDate(
                    rawDate
                )
                    ?: continue

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

            seenUrls.add(
                url
            )

            deadlines.add(
                VClassDeadline(
                    id =
                        "task-html-${deadlines.size + 1}",
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

    private fun createDeadlineTimeInfo(
        timestamp: Long
    ): DeadlineTimeInfo {
        val deadline =
            Instant
                .ofEpochSecond(
                    timestamp
                )
                .atZone(
                    wibZone
                )

        val now =
            ZonedDateTime.now(
                wibZone
            )

        val seconds =
            deadline.toEpochSecond() -
                now.toEpochSecond()

        val formatter =
            DateTimeFormatter.ofPattern(
                "dd MMM yyyy, HH:mm 'WIB'",
                Locale(
                    "id",
                    "ID"
                )
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

    private fun parseMoodleDate(
        rawValue: String
    ): Long? {
        if (rawValue.isBlank()) {
            return null
        }

        var value =
            rawValue.trim()

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
                val formatter =
                    DateTimeFormatter.ofPattern(
                        pattern,
                        Locale.ENGLISH
                    )

                val local =
                    LocalDateTime.parse(
                        value,
                        formatter
                    )

                return local
                    .atZone(
                        wibZone
                    )
                    .toEpochSecond()
            } catch (_: Exception) {
            }
        }

        return null
    }

    private fun classifyActivityType(
        url: String,
        title: String,
        eventType: String
    ): String {
        val text =
            "$url $title $eventType"
                .lowercase()

        return when {
            "/mod/quiz/" in text ||
                "quiz" in text ||
                "kuis" in text ->

                "Kuis"

            "/mod/forum/" in text ||
                "forum" in text ||
                "diskusi" in text ->

                "Forum"

            "/mod/assign/" in text ||
                "assignment" in text ||
                "tugas" in text ->

                "Tugas"

            else ->
                "Materi"
        }
    }

    private fun getCoursesViaAjax(
        sesskey: String
    ): List<VClassCourse> {
        val classifications =
            listOf(
                "all",
                "inprogress",
                "future",
                "past"
            )

        val courses =
            mutableListOf<VClassCourse>()

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

                if (
                    responseItem.optBoolean(
                        "error",
                        false
                    )
                ) {
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
                        courseId.isBlank() ||
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

                if (
                    classification == "all" &&
                    courses.isNotEmpty()
                ) {
                    break
                }
            } catch (_: Exception) {
            }
        }

        return courses
    }

    private fun getCoursesViaHtml(
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
                    "a.coursename[href], a[href*='course/view.php?id=']"
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
                "a[href*='course/view.php?id=']"
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

    private fun parseSearchCourses(
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
                ".coursebox, .course-search-result, [data-courseid]"
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
                    url =
                        normalizeUrl(
                            href
                        )
                )
            )
        }

        if (courses.isNotEmpty()) {
            return courses
        }

        val links =
            document.select(
                "a[href*='course/view.php?id=']"
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
                ".coursename a[href*='course/view.php?id=']",
                "h3 a[href*='course/view.php?id=']",
                "h4 a[href*='course/view.php?id=']",
                "a[href*='course/view.php?id=']"
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

    private fun normalizeCourse(
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
                parsed.rawFullName
        )
    }

    private fun parseCourseParts(
        rawName: String
    ): ParsedCourse {
        val cleanRawName =
            Jsoup.parse(
                rawName
            )
                .text()
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim()

        val parts =
            cleanRawName
                .split(
                    "|"
                )
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }

        val semesterRegex =
            Regex(
                "^[A-Za-z]+\\s*\\d{4}/\\d{4}$"
            )

        var semester = ""
        var code = ""
        var name = cleanRawName
        var instructor = ""

        if (
            parts.size >= 4 &&
            semesterRegex.matches(
                parts[0]
            )
        ) {
            semester =
                parts[0]

            code =
                parts[1]

            name =
                parts
                    .subList(
                        2,
                        parts.size - 1
                    )
                    .joinToString(
                        " | "
                    )

            instructor =
                parts.last()
        } else if (
            parts.size >= 3 &&
            semesterRegex.matches(
                parts[0]
            )
        ) {
            semester =
                parts[0]

            code =
                parts[1]

            name =
                parts
                    .drop(
                        2
                    )
                    .joinToString(
                        " | "
                    )
        } else if (
            parts.size == 2
        ) {
            code =
                parts[0]

            name =
                parts[1]
        }

        name =
            name
                .replace(
                    Regex("\\s*\\*\\s*$"),
                    ""
                )
                .trim()

        return ParsedCourse(
            name = name,
            code = code,
            semester = semester,
            instructor = instructor,
            rawFullName = cleanRawName
        )
    }

    private fun extractCourseName(
        link: Element
    ): String {
        val cloned =
            link.clone()

        cloned
            .select(
                ".sr-only, .accesshide"
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

    private fun extractCourseId(
        url: String
    ): String {
        return Regex(
            "[?&]id=(\\d+)"
        )
            .find(
                url
            )
            ?.groupValues
            ?.getOrNull(
                1
            )
            .orEmpty()
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

    private fun normalizeUrl(
        url: String
    ): String {
        return when {
            url.startsWith(
                "https://"
            ) ->
                url

            url.startsWith(
                "http://"
            ) ->
                url

            url.startsWith(
                "/"
            ) ->
                "$BASE_URL$url"

            url.isBlank() ->
                ""

            else ->
                "$BASE_URL/$url"
        }
    }

    private fun getAuthenticatedPage(
        url: String
    ): PageResult {
        return try {
            val request =
                Request.Builder()
                    .url(url)
                    .get()
                    .build()

            client
                .newCall(
                    request
                )
                .execute()
                .use { response ->

                    if (!response.isSuccessful) {
                        return PageResult(
                            success = false,
                            message = "VClass mengembalikan HTTP ${response.code}."
                        )
                    }

                    val html =
                        response.body
                            ?.string()
                            .orEmpty()

                    val finalUrl =
                        response.request
                            .url
                            .toString()

                    if (
                        finalUrl.contains(
                            "login/index.php"
                        )
                    ) {
                        return PageResult(
                            success = false,
                            message = "Session VClass sudah berakhir."
                        )
                    }

                    PageResult(
                        success = true,
                        html = html
                    )
                }
        } catch (e: Exception) {
            PageResult(
                success = false,
                message =
                    e.message
                        ?: "Gagal menghubungi VClass."
            )
        }
    }
}

data class LoginResult(
    val success: Boolean,
    val message: String,
    val displayName: String = ""
)

data class CoursesResult(
    val success: Boolean,
    val courses: List<VClassCourse> = emptyList(),
    val message: String = ""
)

data class SearchCoursesResult(
    val success: Boolean,
    val courses: List<VClassCourse> = emptyList(),
    val message: String = ""
)

data class DeadlinesResult(
    val success: Boolean,
    val deadlines: List<VClassDeadline> = emptyList(),
    val message: String = ""
)

data class VClassCourse(
    val id: String,
    val name: String,
    val code: String,
    val semester: String,
    val instructor: String,
    val url: String,
    val rawFullName: String
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
    val rawFullName: String
)

private data class DeadlineTimeInfo(
    val iso: String,
    val display: String,
    val timeLeft: String,
    val urgency: String
)

private data class PageResult(
    val success: Boolean,
    val html: String = "",
    val message: String = ""
)

private class MemoryCookieJar :
    CookieJar {

    private val cookies =
        mutableListOf<Cookie>()

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>
    ) {
        synchronized(
            this.cookies
        ) {
            this.cookies.removeAll {
                    oldCookie ->

                cookies.any {
                        newCookie ->

                    oldCookie.name ==
                        newCookie.name &&
                        oldCookie.domain ==
                        newCookie.domain &&
                        oldCookie.path ==
                        newCookie.path
                }
            }

            this.cookies.addAll(
                cookies
            )
        }
    }

    override fun loadForRequest(
        url: HttpUrl
    ): List<Cookie> {
        synchronized(
            cookies
        ) {
            val currentTime =
                System.currentTimeMillis()

            cookies.removeAll {
                it.expiresAt <
                    currentTime
            }

            return cookies.filter {
                it.matches(
                    url
                )
            }
        }
    }

    fun replaceAll(
        newCookies: List<Cookie>
    ) {
        synchronized(
            cookies
        ) {
            cookies.clear()

            cookies.addAll(
                newCookies
            )
        }
    }

    fun getAll(): List<Cookie> {
        synchronized(
            cookies
        ) {
            val currentTime =
                System.currentTimeMillis()

            cookies.removeAll {
                it.expiresAt <
                    currentTime
            }

            return cookies.toList()
        }
    }

    fun clear() {
        synchronized(
            cookies
        ) {
            cookies.clear()
        }
    }
}