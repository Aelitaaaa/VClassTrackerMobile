package com.dzaky.vclasstracker.data.remote

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VClassParserTest {
    private val origin = VClassSessionPolicy.ORIGIN
    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/fixtures/$name")).readText()

    @Test fun `search supports different cards reordered parameters and stable deduplication`() {
        val html = fixture("course-search.html")
        val courses = VClassClient.parseSearchCourses(html)
        assertEquals(listOf("12", "13"), courses.map { it.id })
        assertEquals("Algoritma", courses[0].name)
        assertEquals("IT101", courses[0].code)
        assertEquals("1IA10", courses[0].className)
        assertEquals("Dosen Contoh", courses[0].instructor)
        assertEquals("Pemrograman", courses[1].name)
        assertEquals("", courses[1].instructor)
        assertEquals(1, VClassClient.nextSearchPage(html, "1IA10", 0))
        assertNull(VClassClient.nextSearchPage(html, "Algoritma", 0))
        assertNull(VClassClient.nextSearchPage(html, "1IA10", 1))
        assertEquals("", VClassClient.extractCourseId("/mod/quiz/view.php?id=12"))
        assertEquals("", VClassClient.extractCourseId("https://example.invalid/course/view.php?id=12"))
    }

    @Test fun `plain and malformed course names retain useful text`() {
        val course = VClassClient.normalizeCourse("14", "Algoritma &amp; Pemrograman", "$origin/course/view.php?id=14")
        assertEquals("Algoritma & Pemrograman", course.name)
        assertEquals("", course.semester)
        assertTrue(VClassClient.parseSearchCourses("<main>no courses</main>").isEmpty())
    }

    @Test fun `missing catalog data is an error while explicit empty results are successful`() {
        val empty = VClassClient.parseCatalogPage("<main>No courses were found with the words 'Algoritma'</main>", "Algoritma", 0)
        assertTrue(empty.success)
        assertTrue(empty.courses.isEmpty())
        val changed = VClassClient.parseCatalogPage("<main><form id='coursesearch'></form><div id='new-client-renderer'></div></main>", "Algoritma", 0)
        assertFalse(changed.success)
        assertTrue(changed.courses.isEmpty())
    }

    @Test fun `My Courses ignores catalog and navigation links outside enrolled course regions`() {
        val html = """
            <nav><a href="/course/view.php?id=99">Catalog shortcut</a></nav>
            <div class="block_myoverview"><div class="card dashboard-card" data-course-id="12">
            <a class="coursename" href="/course/view.php?id=12">Algoritma</a></div></div>
        """.trimIndent()
        assertEquals(listOf("12"), VClassClient.getCoursesViaHtml(html).map { it.id })
        assertTrue(VClassClient.getCoursesViaHtml("<nav><a href='/course/view.php?id=99'>Catalog</a></nav>").isEmpty())
    }

    private fun form(extra: String = "", action: String = "/enrol/index.php") = """
        <main><form method="post" action="$action">
        <input type="hidden" name="id" value="12">
        <input type="hidden" name="instance" value="7">
        <input type="hidden" name="sesskey" value="public-fixture">
        <input type="hidden" name="_qf__enrol_self_enrol_form" value="1">
        $extra<button type="submit" name="submitbutton" value="Enrol me">Enrol me</button>
        </form></main>
    """.trimIndent()

    @Test fun `native enrollment uses only discovered legitimate self enrollment form`() {
        val result = EnrollmentParser.parse(form(), "$origin/enrol/index.php?id=12", "12")
        assertEquals(EnrollmentState.SELF_ENROLLMENT, result.state)
        assertEquals("7", result.form?.fields?.get("instance"))
        assertEquals("public-fixture", result.form?.fields?.get("sesskey"))
        assertEquals("$origin/enrol/index.php", result.form?.action)
        val keyed = EnrollmentParser.parse(form("<input type='password' name='enrolpassword'>"), "$origin/enrol/index.php?id=12", "12")
        assertEquals(EnrollmentState.KEY_REQUIRED, keyed.state)
        assertEquals("enrolpassword", keyed.form?.keyField)
        assertFalse(keyed.form!!.fields.containsKey("enrolpassword"))
    }

    @Test fun `foreign forms missing CSRF different course and extra consent use official fallback`() {
        for (html in listOf(form(action = "https://example.invalid/enrol/index.php"), form().replace("name=\"sesskey\"", "name=\"other\""), form().replace("value=\"12\"", "value=\"13\""), form("<input type='checkbox' name='consent' required>"), form().replace("method=\"post\"", "method=\"get\""))) {
            assertNull(EnrollmentParser.parse(html, "$origin/enrol/index.php?id=12", "12").form)
        }
        assertEquals(EnrollmentState.UNAVAILABLE, EnrollmentParser.parse("<main>You cannot enrol yourself in this course</main>", "$origin/enrol/index.php?id=12", "12").state)
        assertEquals(EnrollmentState.MANUAL, EnrollmentParser.parse("<main>Approval required</main>", "$origin/enrol/index.php?id=12", "12").state)
        assertEquals(EnrollmentState.GUEST, EnrollmentParser.parse("<main>Guest access</main>", "$origin/enrol/index.php?id=12", "12").state)
        assertEquals(EnrollmentState.RESTRICTED, EnrollmentParser.parse("<main>No permission</main>", "$origin/enrol/index.php?id=12", "12").state)
        assertNotEquals(EnrollmentState.ALREADY_ENROLLED, EnrollmentParser.parse("<main id='page-course-view'>Course is accessible</main>", "$origin/course/view.php?id=12", "12").state)
    }

    @Test fun `dates preserve WIB WITA WIT and explicit ISO offsets`() {
        val expected = Instant.parse("2026-10-14T04:30:00Z").epochSecond
        assertEquals(expected, VClassClient.parseMoodleDate("Rabu, 14 Oktober 2026, 11:30 WIB"))
        assertEquals(expected, VClassClient.parseMoodleDate("14 October 2026, 12:30 WITA"))
        assertEquals(expected, VClassClient.parseMoodleDate("14 October 2026, 13:30 WIT"))
        assertEquals(expected, VClassClient.parseMoodleDate("2026-10-14T11:30:00+07:00"))
        assertNull(VClassClient.parseMoodleDate("not a date"))
    }

    @Test fun `remaining time changes to expired when the displayed deadline passes`() {
        val deadline = Instant.parse("2026-10-14T05:00:00Z").epochSecond
        val before = VClassClient.createDeadlineTimeInfo(deadline, deadline - 60)
        assertEquals("urgent", before.urgency)
        assertEquals("1 Menit Lagi", before.timeLeft)
        val after = VClassClient.createDeadlineTimeInfo(deadline, deadline + 1)
        assertEquals("expired", after.urgency)
        assertEquals("Terlewat", after.timeLeft)
        assertEquals(before.display, after.display)
    }

    @Test fun `deadline events deduplicate ignore invalid timestamps and sanitize HTML`() {
        val now = Instant.now().epochSecond
        val event = JSONObject().put("name", "<b>Tugas</b> &amp; kuis").put("timesort", 0)
            .put("timestart", now + 86400).put("eventtype", "due")
            .put("action", JSONObject().put("url", "$origin/mod/assign/view.php?id=81"))
            .put("course", JSONObject().put("id", 12).put("fullname", "Ganjil 2026/2027 | IT101 | Algoritma | 1IA10"))
        val events = JSONArray().put(event).put(event).put(JSONObject().put("timesort", Long.MAX_VALUE))
            .put(JSONObject().put("timesort", now - 1)).put("invalid-event")
        val deadlines = VClassClient.parseDeadlineEvents(events, now)
        assertEquals(1, deadlines.size)
        assertEquals("Tugas & kuis", deadlines.single().title)
        assertEquals("Tugas", deadlines.single().type)
        assertEquals("12", deadlines.single().courseId)
        assertTrue(deadlines.single().deadlineDisplay.endsWith("WIB"))
        assertEquals(VClassClient.deadlineId("$origin/mod/assign/view.php?id=81&action=editsubmission", now + 86400), deadlines.single().id)
    }

    @Test fun `HTML deadline IDs are stable when order changes and match AJAX identity`() {
        val timestamp = Instant.now().epochSecond + 86400
        val event = "<div class='event'><a href='/mod/assign/view.php?id=81'>Tugas</a><time data-timestamp='$timestamp'></time></div>"
        val other = "<div class='event'><a href='/mod/quiz/view.php?id=82'>Kuis</a><time datetime='${Instant.ofEpochSecond(timestamp + 10)}'></time></div>"
        val first = VClassClient.getDeadlinesViaHtml(event + other + event)
        val reordered = VClassClient.getDeadlinesViaHtml(other + event)
        assertEquals(2, first.size)
        assertEquals(first.map { it.id }.toSet(), reordered.map { it.id }.toSet())
        assertEquals(VClassClient.deadlineId("$origin/mod/assign/view.php?id=81", timestamp), first.first().id)
    }
}
