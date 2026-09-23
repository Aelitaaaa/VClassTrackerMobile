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
    var query by remember {
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

    fun search() {
        if (loading) {
            return
        }

        if (query.trim().length < 2) {
            message = "Masukkan minimal 2 karakter."
            searched = false
            return
        }

        scope.launch {
            loading = true
            searched = true
            message = ""
            results = emptyList()

            focusManager.clearFocus()

            val result = withContext(
                Dispatchers.IO
            ) {
                VClassClient.searchCourses(
                    query.trim()
                )
            }

            results = result.courses
            message = result.message
            loading = false
        }
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
                                "Cari kelas untuk melihat hasil"
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
                                onCourseSelected(
                                    course
                                )
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

            Spacer(
                modifier = Modifier.height(
                    30.dp
                )
            )
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
                            text = course.code,
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