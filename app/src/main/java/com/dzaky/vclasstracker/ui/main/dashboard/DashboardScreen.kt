package com.dzaky.vclasstracker.ui.dashboard

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dzaky.vclasstracker.R
import com.dzaky.vclasstracker.assistant.KaedePersona
import com.dzaky.vclasstracker.data.remote.VClassClient
import com.dzaky.vclasstracker.data.remote.VClassCourse
import com.dzaky.vclasstracker.data.remote.VClassDeadline
import com.dzaky.vclasstracker.ui.components.AppBottomNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AppBackground = Color(0xFFF4F5F6)
private val HeaderBackground = Color(0xFFDCE8F2)
private val Primary = Color(0xFF183B56)
private val PrimarySoft = Color(0xFFE8EEF3)
private val Accent = Color(0xFFD6A34A)
private val AccentSoft = Color(0xFFF7EEDB)
private val TextPrimary = Color(0xFF17212B)
private val TextSecondary = Color(0xFF69747D)
private val Success = Color(0xFF4D7556)
private val SuccessSoft = Color(0xFFE8F1E9)
private val Urgent = Color(0xFFA34848)
private val UrgentSoft = Color(0xFFF7E8E8)
private val Soon = Color(0xFF9A6C25)
private val SoonSoft = Color(0xFFF7EEDB)
private val Safe = Color(0xFF4D7556)
private val SafeSoft = Color(0xFFE8F1E9)
private val KaedeBackground = Color(0xFFF5E9EE)
private val KaedeText = Color(0xFF65545C)
private val KaedeTitle = Color(0xFF372D33)
private val KaedeSubText = Color(0xFF9B7585)
private val White = Color.White

@Composable
fun DashboardScreen(
    displayName: String,
    onOpenCourses: () -> Unit,
    onOpenTasks: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    var courses by remember {
        mutableStateOf<List<VClassCourse>>(
            emptyList()
        )
    }

    var deadlines by remember {
        mutableStateOf<List<VClassDeadline>>(
            emptyList()
        )
    }

    var loadingCourses by remember {
        mutableStateOf(
            true
        )
    }

    var loadingDeadlines by remember {
        mutableStateOf(
            true
        )
    }

    var courseMessage by remember {
        mutableStateOf(
            ""
        )
    }

    var deadlineMessage by remember {
        mutableStateOf(
            ""
        )
    }

    val formattedName =
        formatName(
            displayName
        )

    val initials =
        getInitials(
            displayName
        )

    val kaedeGreeting =
        remember(
            displayName
        ) {
            KaedePersona.greeting(
                displayName
            )
        }

    LaunchedEffect(Unit) {
        loadingCourses = true
        loadingDeadlines = true

        val courseResult =
            withContext(
                Dispatchers.IO
            ) {
                VClassClient.getMyCourses()
            }

        courses =
            courseResult.courses

        courseMessage =
            courseResult.message

        loadingCourses =
            false

        val deadlineResult =
            withContext(
                Dispatchers.IO
            ) {
                VClassClient.getDeadlines()
            }

        deadlines =
            deadlineResult.deadlines

        deadlineMessage =
            deadlineResult.message

        loadingDeadlines =
            false
    }

    val semesters =
        courses
            .map {
                it.semester
            }
            .filter {
                it.isNotBlank()
            }
            .distinct()

    val semesterDisplay =
        when {
            loadingCourses ->
                "Memuat..."

            semesters.isNotEmpty() ->
                semesters.first()

            else ->
                "Belum terbaca"
        }

    val nearestDeadline =
        deadlines
            .minByOrNull {
                it.deadlineTimestamp
            }

    val kaedeImage =
        when {
            loadingDeadlines ->
                R.drawable.kaede_avatar

            deadlines.isEmpty() ->
                R.drawable.kaede_happy

            nearestDeadline != null &&
                nearestDeadline.urgency == "urgent" ->
                R.drawable.kaede_angry

            else ->
                R.drawable.kaede_avatar
        }

    val kaedeMessage =
        when {
            loadingDeadlines ->
                kaedeGreeting

            deadlines.isEmpty() ->
                KaedePersona.noDeadline(
                    displayName
                )

            nearestDeadline != null ->
                KaedePersona.deadlineReminder(
                    displayName,
                    nearestDeadline
                )

            else ->
                kaedeGreeting
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                AppBackground
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        HeaderBackground
                    )
                    .padding(
                        start = 22.dp,
                        end = 22.dp,
                        top = 48.dp,
                        bottom = 42.dp
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            145.dp
                        )
                        .align(
                            Alignment.TopEnd
                        )
                        .offset(
                            x = 55.dp,
                            y = (-58).dp
                        )
                        .clip(
                            CircleShape
                        )
                        .background(
                            Primary.copy(
                                alpha = 0.05f
                            )
                        )
                )

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(
                                    44.dp
                                )
                                .clip(
                                    RoundedCornerShape(
                                        13.dp
                                    )
                                )
                                .background(
                                    Primary
                                ),
                            contentAlignment =
                                Alignment.Center
                        ) {
                            Text(
                                text = "V",
                                color = White,
                                fontSize = 20.sp,
                                fontWeight =
                                    FontWeight.Bold
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
                                text = "VClass Tracker",
                                color = Primary,
                                fontSize = 16.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(
                                text = "Gunadarma companion",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(
                                    42.dp
                                )
                                .clip(
                                    RoundedCornerShape(
                                        13.dp
                                    )
                                )
                                .background(
                                    White.copy(
                                        alpha = 0.8f
                                    )
                                ),
                            contentAlignment =
                                Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                color = Primary,
                                fontSize = 11.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(
                            31.dp
                        )
                    )

                    Text(
                        text = "Halo,",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(
                        modifier = Modifier.height(
                            4.dp
                        )
                    )

                    Text(
                        text = formattedName,
                        color = TextPrimary,
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontWeight =
                            FontWeight.Bold,
                        letterSpacing = (-0.7).sp,
                        maxLines = 2,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier = Modifier.height(
                            12.dp
                        )
                    )

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
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
                                    Success
                                )
                        )

                        Text(
                            text = "  Terhubung ke VClass",
                            color = Success,
                            fontSize = 11.sp,
                            fontWeight =
                                FontWeight.SemiBold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 18.dp
                    )
                    .offset(
                        y = (-20).dp
                    )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(
                        19.dp
                    ),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                White
                        ),
                    elevation =
                        CardDefaults.cardElevation(
                            defaultElevation =
                                2.dp
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(
                                18.dp
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(
                                        1f
                                    )
                            ) {
                                Text(
                                    text = "Ringkasan VClass",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                Spacer(
                                    modifier =
                                        Modifier.height(
                                            3.dp
                                        )
                                )

                                Text(
                                    text =
                                        semesterDisplay,
                                    color =
                                        TextSecondary,
                                    fontSize = 10.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            8.dp
                                        )
                                    )
                                    .background(
                                        if (
                                            loadingCourses ||
                                            loadingDeadlines
                                        ) {
                                            AccentSoft
                                        } else {
                                            SuccessSoft
                                        }
                                    )
                                    .padding(
                                        horizontal =
                                            10.dp,
                                        vertical =
                                            6.dp
                                    )
                            ) {
                                Text(
                                    text =
                                        if (
                                            loadingCourses ||
                                            loadingDeadlines
                                        ) {
                                            "SYNC"
                                        } else {
                                            "LIVE"
                                        },
                                    color =
                                        if (
                                            loadingCourses ||
                                            loadingDeadlines
                                        ) {
                                            Accent
                                        } else {
                                            Success
                                        },
                                    fontSize =
                                        9.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    letterSpacing =
                                        0.7.sp
                                )
                            }
                        }

                        Spacer(
                            modifier = Modifier.height(
                                20.dp
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    10.dp
                                )
                        ) {
                            SummaryItem(
                                value =
                                    if (
                                        loadingCourses
                                    ) {
                                        "..."
                                    } else {
                                        courses.size
                                            .toString()
                                    },
                                label =
                                    "Mata kuliah",
                                background =
                                    PrimarySoft,
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            )

                            SummaryItem(
                                value =
                                    if (
                                        loadingDeadlines
                                    ) {
                                        "..."
                                    } else {
                                        deadlines.size
                                            .toString()
                                    },
                                label =
                                    "Deadline",
                                background =
                                    AccentSoft,
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(
                        12.dp
                    )
                )

                KaedeAssistantCard(
                    message =
                        kaedeMessage,
                    imageRes =
                        kaedeImage
                )

                Spacer(
                    modifier = Modifier.height(
                        26.dp
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(
                                1f
                            )
                    ) {
                        Text(
                            text = "Deadline terdekat",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            modifier = Modifier.height(
                                3.dp
                            )
                        )

                        Text(
                            text =
                                when {
                                    loadingDeadlines ->
                                        "Mengambil deadline dari VClass..."

                                    deadlines.isEmpty() ->
                                        deadlineMessage

                                    else ->
                                        "${deadlines.size} deadline aktif"
                                },
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(
                        12.dp
                    )
                )

                when {
                    loadingDeadlines -> {
                        DeadlineLoadingCard()
                    }

                    nearestDeadline != null -> {
                        DeadlineCard(
                            deadline =
                                nearestDeadline
                        )
                    }

                    else -> {
                        EmptyDeadlineCard(
                            message =
                                deadlineMessage
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(
                        27.dp
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(
                                1f
                            )
                    ) {
                        Text(
                            text = "Kelas saya",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            modifier = Modifier.height(
                                3.dp
                            )
                        )

                        Text(
                            text =
                                if (
                                    loadingCourses
                                ) {
                                    "Mengambil data VClass..."
                                } else {
                                    courseMessage
                                },
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }

                    Text(
                        text = "Lihat semua",
                        color = Primary,
                        fontSize = 10.sp,
                        fontWeight =
                            FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    8.dp
                                )
                            )
                            .clickable {
                                onOpenCourses()
                            }
                            .padding(
                                horizontal =
                                    9.dp,
                                vertical =
                                    6.dp
                            )
                    )
                }

                Spacer(
                    modifier = Modifier.height(
                        12.dp
                    )
                )

                if (loadingCourses) {
                    LoadingCourseCard()

                    Spacer(
                        modifier = Modifier.height(
                            9.dp
                        )
                    )

                    LoadingCourseCard()
                } else if (courses.isEmpty()) {
                    EmptyCourseCard(
                        message =
                            courseMessage
                    )
                } else {
                    val previewCourses =
                        courses.take(
                            4
                        )

                    previewCourses
                        .forEachIndexed {
                                index,
                                course ->

                            CourseCard(
                                course =
                                    course
                            )

                            if (
                                index !=
                                previewCourses.lastIndex
                            ) {
                                Spacer(
                                    modifier =
                                        Modifier.height(
                                            9.dp
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

                Text(
                    text = "Semester ditemukan",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(
                        12.dp
                    )
                )

                if (semesters.isEmpty()) {
                    SemesterItem(
                        title =
                            "Belum ada data semester",
                        count = 0
                    )
                } else {
                    semesters.forEach {
                            semester ->

                        val count =
                            courses.count {
                                    course ->

                                course.semester ==
                                    semester
                            }

                        SemesterItem(
                            title =
                                semester,
                            count =
                                count
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(
                        25.dp
                    )
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOpenCourses()
                        },
                    shape = RoundedCornerShape(
                        16.dp
                    ),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                Primary
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                18.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(
                                    1f
                                )
                        ) {
                            Text(
                                text = "Kelola kelas",
                                color = White,
                                fontSize = 14.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        4.dp
                                    )
                            )

                            Text(
                                text = "Lihat kelas berdasarkan semester",
                                color =
                                    Color(
                                        0xFFC5D3DD
                                    ),
                                fontSize = 10.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(
                                    35.dp
                                )
                                .clip(
                                    RoundedCornerShape(
                                        10.dp
                                    )
                                )
                                .background(
                                    White.copy(
                                        alpha = 0.12f
                                    )
                                ),
                            contentAlignment =
                                Alignment.Center
                        ) {
                            Text(
                                text = "→",
                                color = White,
                                fontSize = 18.sp,
                                fontWeight =
                                    FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        AppBottomNavigation(
            currentPage = "dashboard",
            onOpenDashboard = {},
            onOpenTasks = onOpenTasks,
            onOpenCourses = onOpenCourses,
            onOpenProfile = onOpenProfile,
            modifier = Modifier
                .align(
                    Alignment.BottomCenter
                )
        )
    }
}

@Composable
private fun KaedeAssistantCard(
    message: String,
    imageRes: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(
            18.dp
        ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    KaedeBackground
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 6.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 8.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Image(
                painter =
                    painterResource(
                        id =
                            imageRes
                    ),
                contentDescription =
                    "Kaede",
                modifier = Modifier
                    .size(
                        108.dp
                    ),
                contentScale =
                    ContentScale.Fit
            )

            Column(
                modifier = Modifier
                    .padding(
                        start = 5.dp
                    )
                    .weight(
                        1f
                    )
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kaede",
                        color = KaedeTitle,
                        fontSize = 15.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text = "  •  VClass Assistant",
                        color = KaedeSubText,
                        fontSize = 8.sp,
                        fontWeight =
                            FontWeight.Medium
                    )
                }

                Spacer(
                    modifier = Modifier.height(
                        7.dp
                    )
                )

                Text(
                    text = message,
                    color = KaedeText,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontWeight =
                        FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun DeadlineCard(
    deadline: VClassDeadline
) {
    val statusColor =
        when (
            deadline.urgency
        ) {
            "urgent" ->
                Urgent

            "soon" ->
                Soon

            else ->
                Safe
        }

    val statusBackground =
        when (
            deadline.urgency
        ) {
            "urgent" ->
                UrgentSoft

            "soon" ->
                SoonSoft

            else ->
                SafeSoft
        }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(
            17.dp
        ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    Primary
            )
    ) {
        Column(
            modifier = Modifier
                .padding(
                    18.dp
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                8.dp
                            )
                        )
                        .background(
                            White.copy(
                                alpha =
                                    0.12f
                            )
                        )
                        .padding(
                            horizontal =
                                9.dp,
                            vertical =
                                6.dp
                        )
                ) {
                    Text(
                        text =
                            deadline.type
                                .uppercase(),
                        color =
                            Color(
                                0xFFE2C178
                            ),
                        fontSize =
                            8.sp,
                        fontWeight =
                            FontWeight.Bold,
                        letterSpacing =
                            0.7.sp
                    )
                }

                Spacer(
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
                            statusBackground
                        )
                        .padding(
                            horizontal =
                                9.dp,
                            vertical =
                                6.dp
                        )
                ) {
                    Text(
                        text =
                            deadline.timeLeft,
                        color =
                            statusColor,
                        fontSize =
                            8.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(
                    18.dp
                )
            )

            Text(
                text = deadline.title,
                color = White,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight =
                    FontWeight.Bold,
                maxLines = 3,
                overflow =
                    TextOverflow.Ellipsis
            )

            Spacer(
                modifier = Modifier.height(
                    7.dp
                )
            )

            Text(
                text =
                    deadline.courseName,
                color =
                    Color(
                        0xFFC4D2DC
                    ),
                fontSize = 10.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis
            )

            if (
                deadline.courseCode
                    .isNotBlank()
            ) {
                Spacer(
                    modifier =
                        Modifier.height(
                            4.dp
                        )
                )

                Text(
                    text =
                        deadline.courseCode,
                    color =
                        Color(
                            0xFFAFC1CE
                        ),
                    fontSize =
                        9.sp
                )
            }

            Spacer(
                modifier = Modifier.height(
                    17.dp
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
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
                    text =
                        "  ${deadline.deadlineDisplay}",
                    color =
                        Color(
                            0xFFD7E1E8
                        ),
                    fontSize =
                        9.sp,
                    modifier =
                        Modifier.weight(
                            1f
                        )
                )
            }
        }
    }
}

@Composable
private fun EmptyDeadlineCard(
    message: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(
            17.dp
        ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    White
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    19.dp
                )
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            9.dp
                        )
                        .clip(
                            CircleShape
                        )
                        .background(
                            Success
                        )
                )

                Text(
                    text =
                        "  Tidak ada deadline dekat",
                    color =
                        TextPrimary,
                    fontSize =
                        13.sp,
                    fontWeight =
                        FontWeight.SemiBold
                )
            }

            Spacer(
                modifier = Modifier.height(
                    7.dp
                )
            )

            Text(
                text =
                    message.ifBlank {
                        "Tidak ada deadline aktif dari VClass saat ini."
                    },
                color =
                    TextSecondary,
                fontSize =
                    10.sp,
                lineHeight =
                    15.sp
            )
        }
    }
}

@Composable
private fun DeadlineLoadingCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(
            17.dp
        ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    Primary
            )
    ) {
        Column(
            modifier = Modifier
                .padding(
                    18.dp
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(
                        0.3f
                    )
                    .height(
                        10.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            5.dp
                        )
                    )
                    .background(
                        White.copy(
                            alpha =
                                0.15f
                        )
                    )
            )

            Spacer(
                modifier = Modifier.height(
                    19.dp
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(
                        0.78f
                    )
                    .height(
                        14.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            5.dp
                        )
                    )
                    .background(
                        White.copy(
                            alpha =
                                0.15f
                        )
                    )
            )

            Spacer(
                modifier = Modifier.height(
                    9.dp
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(
                        0.52f
                    )
                    .height(
                        10.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            5.dp
                        )
                    )
                    .background(
                        White.copy(
                            alpha =
                                0.10f
                        )
                    )
            )
        }
    }
}

@Composable
private fun CourseCard(
    course: VClassCourse
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(
            16.dp
        ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    White
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation =
                    1.dp
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    16.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
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
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    text =
                        course.code
                            .take(
                                4
                            )
                            .ifBlank {
                                "MK"
                            },
                    color =
                        Primary,
                    fontSize =
                        10.sp,
                    fontWeight =
                        FontWeight.Bold
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
                    course.semester
                        .isNotBlank()
                ) {
                    Text(
                        text =
                            course.semester,
                        color =
                            Accent,
                        fontSize =
                            9.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                4.dp
                            )
                    )
                }

                Text(
                    text =
                        course.name,
                    color =
                        TextPrimary,
                    fontSize =
                        13.sp,
                    lineHeight =
                        17.sp,
                    fontWeight =
                        FontWeight.SemiBold,
                    maxLines = 2,
                    overflow =
                        TextOverflow.Ellipsis
                )

                if (
                    course.code
                        .isNotBlank()
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(
                                5.dp
                            )
                    )

                    Text(
                        text =
                            course.code,
                        color =
                            TextSecondary,
                        fontSize =
                            10.sp
                    )
                }

                if (
                    course.instructor
                        .isNotBlank()
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(
                                3.dp
                            )
                    )

                    Text(
                        text =
                            course.instructor,
                        color =
                            TextSecondary,
                        fontSize =
                            9.sp,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = "›",
                color = Primary,
                fontSize = 23.sp
            )
        }
    }
}

@Composable
private fun LoadingCourseCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(
            16.dp
        ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    White
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    16.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
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
                            0.72f
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
                                0xFFE9ECEE
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

@Composable
private fun EmptyCourseCard(
    message: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(
            16.dp
        ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    White
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    19.dp
                )
        ) {
            Text(
                text = "Belum ada kelas",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight =
                    FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(
                    5.dp
                )
            )

            Text(
                text =
                    message.ifBlank {
                        "Data mata kuliah belum ditemukan."
                    },
                color =
                    TextSecondary,
                fontSize =
                    10.sp,
                lineHeight =
                    15.sp
            )
        }
    }
}

@Composable
private fun SemesterItem(
    title: String,
    count: Int
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
                White
            )
            .padding(
                horizontal =
                    15.dp,
                vertical =
                    13.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(
                    9.dp
                )
                .clip(
                    CircleShape
                )
                .background(
                    Accent
                )
        )

        Text(
            text = title,
            color =
                TextPrimary,
            fontSize =
                12.sp,
            fontWeight =
                FontWeight.SemiBold,
            modifier =
                Modifier
                    .padding(
                        start =
                            11.dp
                    )
                    .weight(
                        1f
                    )
        )

        Text(
            text = "$count kelas",
            color =
                TextSecondary,
            fontSize =
                10.sp
        )
    }
}

@Composable
private fun SummaryItem(
    value: String,
    label: String,
    background: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    13.dp
                )
            )
            .background(
                background
            )
            .padding(
                horizontal =
                    14.dp,
                vertical =
                    14.dp
            )
    ) {
        Text(
            text = value,
            color =
                Primary,
            fontSize =
                22.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(
                5.dp
            )
        )

        Text(
            text = label,
            color =
                TextSecondary,
            fontSize =
                10.sp
        )
    }
}

private fun formatName(
    name: String
): String {
    if (name.isBlank()) {
        return "Mahasiswa"
    }

    return name
        .trim()
        .lowercase()
        .split(
            " "
        )
        .filter {
            it.isNotBlank()
        }
        .joinToString(
            " "
        ) {
            it.replaceFirstChar {
                    char ->

                char.uppercase()
            }
        }
}

private fun getInitials(
    name: String
): String {
    val parts =
        name
            .trim()
            .split(
                " "
            )
            .filter {
                it.isNotBlank()
            }

    if (parts.isEmpty()) {
        return "UG"
    }

    if (parts.size == 1) {
        return parts[0]
            .take(
                2
            )
            .uppercase()
    }

    return (
        parts[0]
            .take(
                1
            ) +
            parts[1]
                .take(
                    1
                )
        )
        .uppercase()
}