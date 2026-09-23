package com.dzaky.vclasstracker.ui.tasks

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
import androidx.compose.material3.CircularProgressIndicator
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
import com.dzaky.vclasstracker.data.remote.VClassDeadline
import com.dzaky.vclasstracker.ui.components.AppBottomNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AppBackground = Color(0xFFF4F5F6)
private val HeaderBackground = Color(0xFFDCE8F2)
private val Primary = Color(0xFF183B56)
private val Accent = Color(0xFFD6A34A)
private val TextPrimary = Color(0xFF17212B)
private val TextSecondary = Color(0xFF69747D)
private val White = Color.White
private val Urgent = Color(0xFFA34848)
private val UrgentSoft = Color(0xFFF7E8E8)
private val Soon = Color(0xFF9A6C25)
private val SoonSoft = Color(0xFFF7EEDB)
private val Safe = Color(0xFF4D7556)
private val SafeSoft = Color(0xFFE8F1E9)

@Composable
fun TasksScreen(
    onBack: () -> Unit,
    onOpenDashboard: () -> Unit = {},
    onOpenCourses: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenTaskDetail: (VClassDeadline) -> Unit = {}
) {
    var deadlines by remember {
        mutableStateOf<List<VClassDeadline>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var message by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {
        loading = true

        val result =
            withContext(Dispatchers.IO) {
                VClassClient.getDeadlines()
            }

        deadlines =
            result.deadlines
                .sortedBy {
                    it.deadlineTimestamp
                }

        message = result.message
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    bottom = 92.dp
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HeaderBackground)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 48.dp,
                        bottom = 24.dp
                    )
            ) {
                Column {
                    Text(
                        text = "Tugas",
                        color = Primary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    Text(
                        text =
                            if (loading) {
                                "Mengambil deadline dari VClass..."
                            } else {
                                "${deadlines.size} deadline aktif"
                            },
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Card(
                    modifier = Modifier
                        .align(Alignment.CenterEnd),
                    shape = RoundedCornerShape(11.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = White
                    ),
                    onClick = onBack
                ) {
                    Text(
                        text = "←",
                        color = Primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            horizontal = 14.dp,
                            vertical = 8.dp
                        )
                    )
                }
            }

            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Primary
                        )
                    }
                }

                deadlines.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = White
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Safe)
                                )

                                Spacer(
                                    modifier = Modifier.height(12.dp)
                                )

                                Text(
                                    text = "Belum ada tugas aktif",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(
                                    modifier = Modifier.height(6.dp)
                                )

                                Text(
                                    text =
                                        message.ifBlank {
                                            "Tidak ada deadline dari VClass saat ini."
                                        },
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(
                                rememberScrollState()
                            )
                            .padding(
                                horizontal = 18.dp,
                                vertical = 18.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(11.dp)
                    ) {
                        deadlines.forEach { deadline ->
                            TaskCard(
                                deadline = deadline,
                                onClick = {
                                    onOpenTaskDetail(
                                        deadline
                                    )
                                }
                            )
                        }

                        Spacer(
                            modifier = Modifier.height(16.dp)
                        )
                    }
                }
            }
        }

        AppBottomNavigation(
            currentPage = "tasks",
            onOpenDashboard = onOpenDashboard,
            onOpenTasks = {},
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
private fun TaskCard(
    deadline: VClassDeadline,
    onClick: () -> Unit
) {
    val statusColor =
        when (deadline.urgency) {
            "urgent" -> Urgent
            "soon" -> Soon
            else -> Safe
        }

    val statusBackground =
        when (deadline.urgency) {
            "urgent" -> UrgentSoft
            "soon" -> SoonSoft
            else -> SafeSoft
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },
        shape = RoundedCornerShape(17.dp),
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
                .padding(17.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(8.dp)
                        )
                        .background(
                            Color(0xFFE8EEF3)
                        )
                        .padding(
                            horizontal = 9.dp,
                            vertical = 5.dp
                        )
                ) {
                    Text(
                        text = deadline.type.uppercase(),
                        color = Primary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(8.dp)
                        )
                        .background(
                            statusBackground
                        )
                        .padding(
                            horizontal = 9.dp,
                            vertical = 5.dp
                        )
                ) {
                    Text(
                        text = deadline.timeLeft,
                        color = statusColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Text(
                text = deadline.title,
                color = TextPrimary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(
                modifier = Modifier.height(7.dp)
            )

            Text(
                text = deadline.courseName,
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (
                deadline.courseCode.isNotBlank()
            ) {
                Spacer(
                    modifier = Modifier.height(3.dp)
                )

                Text(
                    text = deadline.courseCode,
                    color = Accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )

                Text(
                    text =
                        "  ${deadline.deadlineDisplay}",
                    color = TextSecondary,
                    fontSize = 9.sp
                )
            }
        }
    }
}