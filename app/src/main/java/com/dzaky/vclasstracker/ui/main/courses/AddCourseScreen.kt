package com.dzaky.vclasstracker.ui.courses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.dzaky.vclasstracker.data.remote.EnrollmentResult
import com.dzaky.vclasstracker.data.remote.EnrollmentState
import com.dzaky.vclasstracker.ui.login.OfficialVClassWebView
import com.dzaky.vclasstracker.data.remote.VClassLinks
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dzaky.vclasstracker.data.remote.VClassClient
import com.dzaky.vclasstracker.data.remote.VClassCourse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Background = Color(0xFFF4F5F6)
private val Header = Color(0xFFDCE8F2)
private val Primary = Color(0xFF183B56)
private val PrimarySoft = Color(0xFFE8EEF3)
private val Accent = Color(0xFFD6A34A)
private val AccentSoft = Color(0xFFF7EEDB)
private val TextPrimary = Color(0xFF17212B)
private val TextSecondary = Color(0xFF69747D)
private val Border = Color(0xFFDDE2E6)
private val FieldBackground = Color(0xFFF9FAFB)
private val White = Color.White

@Composable
fun AddCourseScreen(
    onBack: () -> Unit,
    onCourseSelected: (VClassCourse) -> Unit
) {
    var query by rememberSaveable {
        mutableStateOf("")
    }

    var results by remember {
        mutableStateOf<List<VClassCourse>>(
            emptyList()
        )
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var searched by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf("")
    }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val context = LocalContext.current
    var submittedQuery by remember { mutableStateOf("") }
    var nextPage by remember { mutableStateOf<Int?>(null) }
    var selectedCourse by remember { mutableStateOf<VClassCourse?>(null) }
    var enrollment by remember { mutableStateOf<EnrollmentResult?>(null) }
    var enrollmentKey by remember { mutableStateOf("") }
    var enrolling by remember { mutableStateOf(false) }
    var inOfficialPage by remember { mutableStateOf(false) }

    fun search(append: Boolean = false) {
        if (loading || selectedCourse != null) return
        val searchQuery = if (append) submittedQuery else query.trim()
        val page = if (append) nextPage ?: return else 0
        if (searchQuery.length !in 2..200) {
            results = emptyList()
            nextPage = null
            message = "Masukkan 2–200 karakter."
            searched = false
            return
        }
        loading = true
        searched = true
        if (!append) { results = emptyList(); submittedQuery = searchQuery }
        message = ""
        focusManager.clearFocus()
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { VClassClient.searchCourses(searchQuery, page) }
                if (result.success) results = (if (append) results + result.courses else result.courses).distinctBy { it.id }
                nextPage = result.nextPage
                message = result.message
            } finally { loading = false }
        }
    }

    fun inspect(course: VClassCourse) {
        if (enrolling) return
        selectedCourse = course
        enrollment = null
        enrollmentKey = ""
        enrolling = true
        scope.launch {
            try { enrollment = withContext(Dispatchers.IO) { VClassClient.inspectEnrollment(course) } }
            finally { enrolling = false }
        }
    }

    fun verifyMembership(cookieHeader: String = "") {
        val course = selectedCourse ?: return
        if (enrolling) return
        inOfficialPage = false
        enrolling = true
        scope.launch {
            try {
                val handoff = if (cookieHeader.isNotBlank()) withContext(Dispatchers.IO) {
                    VClassClient.completeMicrosoftLogin(cookieHeader, sameAccountOnly = true)
                } else null
                if (handoff != null && !handoff.success) {
                    enrollment = EnrollmentResult(false, message = handoff.message, officialUrl = course.url)
                    return@launch
                }
                val result = withContext(Dispatchers.IO) { VClassClient.getMyCourses() }
                enrollment = if (result.success && result.courses.any { it.id == course.id }) {
                    EnrollmentResult(true, EnrollmentState.ALREADY_ENROLLED, "Kelas terverifikasi di Kelas saya.", course.url)
                } else EnrollmentResult(false, message = if (!result.success) result.message else "Kamu belum terdaftar dalam kelas ini. Selesaikan pendaftaran di VClass.", officialUrl = course.url)
            } finally { enrolling = false; enrollmentKey = "" }
        }
    }

    if (inOfficialPage) {
        val course = selectedCourse
        if (course != null) {
            OfficialVClassWebView(
                initialUrl = enrollment?.officialUrl?.ifBlank { course.url } ?: course.url,
                authentication = false,
                onClose = { verifyMembership() },
                onVerifyEnrollment = { header -> verifyMembership(header) }
            )
            return
        }
    }

    selectedCourse?.let { course ->
        AlertDialog(
            onDismissRequest = { if (!enrolling) { selectedCourse = null; enrollmentKey = ""; enrollment = null } },
            title = { Text(course.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    listOf(course.code, course.className, course.semester, course.instructor).filter { it.isNotBlank() }.forEach { Text(it) }
                    Spacer(Modifier.height(12.dp))
                    Text(if (enrolling) "Memeriksa VClass..." else enrollment?.message.orEmpty())
                    if (enrollment?.state == EnrollmentState.KEY_REQUIRED && enrollment?.form != null) {
                        OutlinedTextField(
                            value = enrollmentKey, onValueChange = { enrollmentKey = it },
                            label = { Text("Kunci pendaftaran kelas") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), enabled = !enrolling
                        )
                    }
                    if (enrollment?.state == EnrollmentState.ALREADY_ENROLLED) {
                        TextButton(onClick = { VClassLinks.open(context, course.url) }) { Text("Buka kelas di VClass") }
                    }
                }
            },
            confirmButton = {
                val state = enrollment
                when {
                    state?.state == EnrollmentState.ALREADY_ENROLLED -> TextButton(onClick = { selectedCourse = null; onCourseSelected(course) }) { Text("Lihat kelas saya") }
                    state?.form != null -> TextButton(
                        enabled = !enrolling && (state.state != EnrollmentState.KEY_REQUIRED || enrollmentKey.isNotBlank()),
                        onClick = {
                            val key = enrollmentKey
                            enrollmentKey = ""
                            enrolling = true
                            scope.launch {
                                try { enrollment = withContext(Dispatchers.IO) { VClassClient.enrollInCourse(course, key) } }
                                finally { enrolling = false; enrollmentKey = "" }
                            }
                        }
                    ) { Text("Daftar kelas") }
                    !enrolling -> TextButton(onClick = { enrollmentKey = ""; inOfficialPage = true }) { Text("Buka pendaftaran resmi") }
                }
            },
            dismissButton = {
                TextButton(enabled = !enrolling, onClick = { selectedCourse = null; enrollmentKey = ""; enrollment = null }) { Text("Tutup") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(
                rememberScrollState()
            )
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Header)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 48.dp,
                    bottom = 27.dp
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                10.dp
                            )
                        )
                        .background(
                            White.copy(
                                alpha = 0.75f
                            )
                        )
                        .clickable {
                            onBack()
                        }
                        .padding(
                            horizontal = 13.dp,
                            vertical = 9.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "‹",
                        color = Primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(
                            start = 14.dp
                        )
                        .weight(
                            1f
                        )
                ) {
                    Text(
                        text = "Tambah kelas",
                        color = TextPrimary,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(
                        modifier = Modifier.height(
                            3.dp
                        )
                    )

                    Text(
                        text = "Cari course langsung dari VClass",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                9.dp
                            )
                        )
                        .background(
                            White.copy(
                                alpha = 0.72f
                            )
                        )
                        .padding(
                            horizontal = 10.dp,
                            vertical = 7.dp
                        )
                ) {
                    Text(
                        text = "VCLASS",
                        color = Primary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(
                    29.dp
                )
            )

            Text(
                text = "Cari mata kuliah",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(
                    8.dp
                )
            )

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it

                    if (message.isNotBlank()) {
                        message = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        58.dp
                    ),
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Nama mata kuliah, kelas, atau semester",
                        color = Color(
                            0xFF9CA3A8
                        ),
                        fontSize = 12.sp
                    )
                },
                shape = RoundedCornerShape(
                    12.dp
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        search()
                    }
                ),
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Border,
                    focusedContainerColor = White,
                    unfocusedContainerColor = White,
                    cursorColor = Primary
                )
            )

            Spacer(
                modifier = Modifier.height(
                    11.dp
                )
            )

            Button(
                onClick = {
                    search()
                },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        49.dp
                    ),
                shape = RoundedCornerShape(
                    11.dp
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = White,
                    disabledContainerColor = Color(
                        0xFF9DAAB3
                    )
                )
            ) {
                Text(
                    text = if (loading) {
                        "Mencari kelas..."
                    } else {
                        "Cari di VClass"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(
                    13.dp
                )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            7.dp
                        )
                        .clip(
                            CircleShape
                        )
                        .background(
                            Accent
                        )
                )

                Text(
                    text = "  Contoh: 1IA10, Algoritma, ATA 2025/2026",
                    color = TextSecondary,
                    fontSize = 9.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 23.dp
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(
                            1f
                        )
                ) {
                    Text(
                        text = "Hasil pencarian",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(
                            3.dp
                        )
                    )

                    Text(
                        text = when {
                            loading ->
                                "Menghubungi VClass..."

                            searched ->
                                message

                            else ->
                                message.ifBlank { "Cari kelas untuk melihat hasil" }
                        },
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }

                if (
                    results.isNotEmpty()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    8.dp
                                )
                            )
                            .background(
                                AccentSoft
                            )
                            .padding(
                                horizontal = 10.dp,
                                vertical = 6.dp
                            )
                    ) {
                        Text(
                            text = "${results.size} HASIL",
                            color = Primary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(
                    16.dp
                )
            )

            when {
                loading -> {
                    SearchLoadingCard()

                    Spacer(
                        modifier = Modifier.height(
                            10.dp
                        )
                    )

                    SearchLoadingCard()

                    Spacer(
                        modifier = Modifier.height(
                            10.dp
                        )
                    )

                    SearchLoadingCard()
                }

                results.isNotEmpty() -> {
                    results.forEachIndexed {
                            index,
                            course ->

                        SearchCourseCard(
                            course = course,
                            onClick = {
                                inspect(course)
                            }
                        )

                        if (
                            index != results.lastIndex
                        ) {
                            Spacer(
                                modifier = Modifier.height(
                                    10.dp
                                )
                            )
                        }
                    }
                }

                searched -> {
                    EmptySearchCard(
                        message = message
                    )
                }

                else -> {
                    SearchIntroductionCard()
                }
            }

            if (nextPage != null && !loading) {
                Button(onClick = { search(append = true) }, modifier = Modifier.fillMaxWidth()) { Text("Muat hasil berikut") }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun SearchCourseCard(
    course: VClassCourse,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },
        shape = RoundedCornerShape(
            16.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    17.dp
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            48.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                14.dp
                            )
                        )
                        .background(
                            PrimarySoft
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = course.code
                            .take(
                                4
                            )
                            .ifBlank {
                                "MK"
                            },
                        color = Primary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(
                            start = 13.dp
                        )
                        .weight(
                            1f
                        )
                ) {
                    if (
                        course.semester.isNotBlank()
                    ) {
                        Text(
                            text = course.semester,
                            color = Accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(
                            modifier = Modifier.height(
                                4.dp
                            )
                        )
                    }

                    Text(
                        text = course.name,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (
                        course.code.isNotBlank()
                    ) {
                        Spacer(
                            modifier = Modifier.height(
                                6.dp
                            )
                        )

                        Text(
                            text = listOf(course.code, course.className).filter { it.isNotBlank() }.joinToString(" · "),
                            color = Primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = "›",
                    color = Primary,
                    fontSize = 23.sp
                )
            }

            if (
                course.instructor.isNotBlank()
            ) {
                Spacer(
                    modifier = Modifier.height(
                        13.dp
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(
                                9.dp
                            )
                        )
                        .background(
                            Color(
                                0xFFF6F7F7
                            )
                        )
                        .padding(
                            horizontal = 11.dp,
                            vertical = 9.dp
                        )
                ) {
                    Text(
                        text = course.instructor,
                        color = TextSecondary,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(
                    13.dp
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VClass Course #${course.id}",
                    color = Color(
                        0xFF939A9F
                    ),
                    fontSize = 8.sp,
                    modifier = Modifier
                        .weight(
                            1f
                        )
                )

                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                8.dp
                            )
                        )
                        .background(
                            Primary
                        )
                        .padding(
                            horizontal = 11.dp,
                            vertical = 7.dp
                        )
                ) {
                    Text(
                        text = "Pilih",
                        color = White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchLoadingCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(
            16.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    17.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(
                        48.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            14.dp
                        )
                    )
                    .background(
                        PrimarySoft
                    )
            )

            Column(
                modifier = Modifier
                    .padding(
                        start = 13.dp
                    )
                    .weight(
                        1f
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            0.78f
                        )
                        .height(
                            12.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                5.dp
                            )
                        )
                        .background(
                            Color(
                                0xFFE7EAEC
                            )
                        )
                )

                Spacer(
                    modifier = Modifier.height(
                        8.dp
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            0.42f
                        )
                        .height(
                            9.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                5.dp
                            )
                        )
                        .background(
                            Color(
                                0xFFF0F1F2
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun SearchIntroductionCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(
            17.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    20.dp
                )
        ) {
            Box(
                modifier = Modifier
                    .size(
                        46.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            13.dp
                        )
                    )
                    .background(
                        PrimarySoft
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⌕",
                    color = Primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(
                    16.dp
                )
            )

            Text(
                text = "Cari kelas VClass",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(
                    6.dp
                )
            )

            Text(
                text = "Gunakan nama mata kuliah, kode kelas, atau semester untuk menemukan course.",
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun EmptySearchCard(
    message: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(
            17.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    20.dp
                )
        ) {
            Box(
                modifier = Modifier
                    .size(
                        43.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            12.dp
                        )
                    )
                    .background(
                        AccentSoft
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    color = Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(
                    14.dp
                )
            )

            Text(
                text = "Kelas tidak ditemukan",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(
                    5.dp
                )
            )

            Text(
                text = message.ifBlank {
                    "Coba gunakan kata pencarian lain."
                },
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 16.sp
            )
        }
    }
}