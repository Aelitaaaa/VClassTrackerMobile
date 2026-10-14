package com.dzaky.vclasstracker.data.remote

import org.jsoup.Jsoup

internal enum class EnrollmentState {
    ALREADY_ENROLLED, SELF_ENROLLMENT, KEY_REQUIRED, MANUAL, UNAVAILABLE, GUEST, RESTRICTED, UNKNOWN
}

internal class EnrollmentForm(val action: String, val fields: Map<String, String>, val keyField: String?)

internal data class EnrollmentResult(
    val success: Boolean,
    val state: EnrollmentState = EnrollmentState.UNKNOWN,
    val message: String,
    val officialUrl: String = "",
    val form: EnrollmentForm? = null
)

internal object EnrollmentParser {
    fun parse(html: String, pageUrl: String, courseId: String): EnrollmentResult {
        val url = VClassSessionPolicy.officialUrl(pageUrl)
            ?: return EnrollmentResult(false, message = "URL pendaftaran tidak valid.")
        val doc = Jsoup.parse(html, url.toString())
        for (form in doc.select("form")) {
            if (form.selectFirst("input[name=_qf__enrol_self_enrol_form]") == null) continue
            val action = if (form.attr("action").isBlank()) url
                else VClassSessionPolicy.officialUrl(form.absUrl("action")) ?: continue
            if (action.encodedPath != "/enrol/index.php" || !form.attr("method").equals("post", true)) continue
            val fields = form.select("input[type=hidden][name]").associate { it.attr("name") to it.attr("value") }.toMutableMap()
            if (fields["sesskey"].isNullOrBlank() || (fields["id"] ?: action.queryParameter("id")) != courseId) continue
            val unsupportedInput = form.select("input:not([type=hidden]):not([type=submit]):not([type=button])")
                .any { it.attr("name") != "enrolpassword" && !it.hasAttr("disabled") }
            if (unsupportedInput || form.select("select, textarea, input[type=file]").isNotEmpty()) continue
            if (fields.keys.any { it.contains("password", true) || it.contains("token", true) }) continue
            val submit = form.select("input[type=submit][name], button[type=submit][name]")
                .firstOrNull { !it.attr("name").contains("cancel", true) }
                ?: continue
            fields[submit.attr("name")] = submit.attr("value")
            val requiresKey = form.selectFirst("input[name=enrolpassword]") != null
            return EnrollmentResult(
                true, if (requiresKey) EnrollmentState.KEY_REQUIRED else EnrollmentState.SELF_ENROLLMENT,
                if (requiresKey) "Kelas memerlukan kunci pendaftaran dari dosen." else "Tambahkan kelas ini ke VClass kamu?",
                url.toString(), EnrollmentForm(action.toString(), fields, if (requiresKey) "enrolpassword" else null)
            )
        }
        val text = doc.select("#region-main, [role=main]").text().ifBlank { doc.body().text() }.lowercase()
        val state = when {
            "not permitted" in text || "no permission" in text || "tidak memiliki izin" in text -> EnrollmentState.RESTRICTED
            "cannot enrol" in text || "can not enrol" in text || "enrolment is disabled" in text || "tidak dapat mendaftar" in text -> EnrollmentState.UNAVAILABLE
            "approval" in text || "manual enrol" in text || "persetujuan" in text -> EnrollmentState.MANUAL
            doc.select("form[action*='/enrol/guest/'], input[name=guestpassword]").isNotEmpty() || "guest access" in text -> EnrollmentState.GUEST
            else -> EnrollmentState.UNKNOWN
        }
        val message = when (state) {
            EnrollmentState.RESTRICTED -> "Akun ini tidak memiliki izin untuk mendaftar kelas tersebut."
            EnrollmentState.UNAVAILABLE -> "Pendaftaran mandiri kelas ini tidak tersedia di VClass."
            EnrollmentState.MANUAL -> "Pendaftaran memerlukan persetujuan atau tindakan dosen melalui VClass."
            EnrollmentState.GUEST -> "Akses tamu tidak berarti kamu terdaftar dalam kelas. Periksa pendaftaran di VClass."
            else -> "Selesaikan pendaftaran melalui halaman resmi VClass. Keanggotaan akan diperiksa setelah kembali."
        }
        return EnrollmentResult(true, state, message, url.toString())
    }
}
