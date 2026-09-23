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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dzaky.vclasstracker.data.remote.VClassClient
import com.dzaky.vclasstracker.data.remote.VClassCourse
import com.dzaky.vclasstracker.ui.components.AppBottomNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Background = Color(0xFFF4F5F6)
private val Header = Color(0xFFDCE8F2)
private val Primary = Color(0xFF183B56)
private val PrimarySoft = Color(0xFFE8EEF3)
private val Accent = Color(0xFFD6A34A)
private val TextPrimary = Color(0xFF17212B)
private val TextSecondary = Color(0xFF69747D)
private val Border = Color(0xFFE3E6E8)
private val White = Color.White

@Composable
fun CoursesScreen(
    onBack: () -> Unit,
    onAddCourse: () -> Unit = {},
    onOpenDashboard: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    var courses by remember {
        mutableStateOf<List<VClassCourse>>(
            emptyList()
        )
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var message by remember {
        mutableStateOf("")
    }

    var selectedSemester by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {
        loading = true

        val result = withContext(
            Dispatchers.IO
        ) {
            VClassClient.getMyCourses()
        }

        courses = result.courses
        message = result.message

        val semesters = result.courses
            .map {
                it.semester
            }
            .filter {
                it.isNotBlank()
            }
            .distinct()

        if (semesters.isNotEmpty()) {
            selectedSemester = semesters.first()
        }

        loading = false
    }

    val semesters = courses
        .map {
            it.semester
        }
        .filter {
            it.isNotBlank()
        }
        .distinct()

    val visibleCourses =
        if (selectedSemester == null) {
            courses
        } else {
            courses.filter {
                it.semester == selectedSemester
            }
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Background
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    bottom = 105.dp
                )
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Header
                )
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 48.dp,
                    bottom = 28.dp
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
                        text = "Kelas saya",
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
                        text = "Mata kuliah dari akun VClass",
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
                        text = if (loading) {
                            "SYNC"
                        } else {
                            "${courses.size} KELAS"
                        },
                        color = Primary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(
                    28.dp
                )
            )

            Text(
                text = "Semester",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(
                    9.dp
                )
            )

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(
                                13.dp
                            )
                        )
                        .background(
                            White.copy(
                                alpha = 0.72f
                            )
                        )
                        .padding(
                            16.dp
                        )
                ) {
                    Text(
                        text = "Mengambil semester...",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else if (semesters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(
                                13.dp
                            )
                        )
                        .background(
                            White.copy(
                                alpha = 0.72f
                            )
                        )
                        .padding(
                            16.dp
                        )
                ) {
                    Text(
                        text = "Semester belum ditemukan",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        8.dp
                    )
                ) {
                    semesters.forEach {
                            semester ->

                        SemesterButton(
                            semester = semester,
                            selected =
                                semester == selectedSemester,
                            count =
                                courses.count {
                                    it.semester == semester
                                },
                            onClick = {
                                selectedSemester = semester
                            }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 24.dp
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
                        text =
                            selectedSemester
                                ?: "Semua kelas",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )

                    Spacer(
                        modifier = Modifier.height(
                            3.dp
                        )
                    )

                    Text(
                        text = if (loading) {
                            "Memuat kelas..."
                        } else {
                            "${visibleCourses.size} mata kuliah"
                        },
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                10.dp
                            )
                        )
                        .background(
                            Primary
                        )
                        .clickable {
                            onAddCourse()
                        }
                        .padding(
                            horizontal = 13.dp,
                            vertical = 9.dp
                        )
                ) {
                    Text(
                        text = "+ Tambah kelas",
                        color = White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(
                    18.dp
                )
            )

            if (loading) {
                CourseLoadingCard()

                Spacer(
                    modifier = Modifier.height(
                        10.dp
                    )
                )

                CourseLoadingCard()

                Spacer(
                    modifier = Modifier.height(
                        10.dp
                    )
                )

                CourseLoadingCard()
            } else if (visibleCourses.isEmpty()) {
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
                    Column(
                        modifier = Modifier.padding(
                            20.dp
                        )
                    ) {
                        Text(
                            text = "Belum ada mata kuliah",
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
                                "Tidak ada kelas untuk semester ini."
                            },
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                visibleCourses.forEachIndexed {
                        index,
                        course ->

                    CourseItem(
                        course = course
                    )

                    if (
                        index != visibleCourses.lastIndex
                    ) {
                        Spacer(
                            modifier = Modifier.height(
                                10.dp
                            )
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(
                    28.dp
                )
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onAddCourse()
                    },
                shape = RoundedCornerShape(
                    16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = Color(
                        0xFFE9EEF2
                    )
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
                                40.dp
                            )
                            .clip(
                                RoundedCornerShape(
                                    12.dp
                                )
                            )
                            .background(
                                White
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            color = Primary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Column(
                        modifier = Modifier
                            .padding(
                                start = 12.dp
                            )
                            .weight(
                                1f
                            )
                    ) {
                        Text(
                            text = "Semester baru?",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(
                            modifier = Modifier.height(
                                3.dp
                            )
                        )

                        Text(
                            text = "Cari dan tambahkan kelas dari VClass.",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }

                    Text(
                        text = "→",
                        color = Primary,
                        fontSize = 20.sp
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(
                    30.dp
                )
            )
        }
    }

        AppBottomNavigation(
            currentPage = "courses",
            onOpenDashboard = onOpenDashboard,
            onOpenTasks = onOpenTasks,
            onOpenCourses = {},
            onOpenProfile = onOpenProfile,
            modifier = Modifier
                .align(
                    Alignment.BottomCenter
                )
        )
    }
}

@Composable
private fun SemesterButton(
    semester: String,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    13.dp
                )
            )
            .background(
                if (selected) {
                    Primary
                } else {
                    White.copy(
                        alpha = 0.75f
                    )
                }
            )
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 15.dp,
                vertical = 13.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(
                    8.dp
                )
                .clip(
                    CircleShape
                )
                .background(
                    if (selected) {
                        Accent
                    } else {
                        Border
                    }
                )
        )

        Text(
            text = semester,
            color = if (selected) {
                White
            } else {
                TextPrimary
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(
                    start = 11.dp
                )
                .weight(
                    1f
                )
        )

        Text(
            text = "$count kelas",
            color = if (selected) {
                Color(
                    0xFFC5D4DF
                )
            } else {
                TextSecondary
            },
            fontSize = 9.sp
        )
    }
}

@Composable
private fun CourseItem(
    course: VClassCourse
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
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
                            47.dp
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
                    Text(
                        text = course.name,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (
                        course.code.isNotBlank()
                    ) {
                        Spacer(
                            modifier = Modifier.height(
                                5.dp
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
                        14.dp
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
        }
    }
}

@Composable
private fun CourseLoadingCard() {
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
                        47.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            13.dp
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
                                0xFFE8EBED
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
                            0.38f
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