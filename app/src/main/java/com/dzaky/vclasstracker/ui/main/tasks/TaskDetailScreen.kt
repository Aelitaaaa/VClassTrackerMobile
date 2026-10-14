package com.dzaky.vclasstracker.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dzaky.vclasstracker.data.remote.VClassDeadline
import com.dzaky.vclasstracker.data.remote.VClassClient
import com.dzaky.vclasstracker.ui.components.rememberDeadlineClock
import com.dzaky.vclasstracker.data.remote.VClassLinks
import com.dzaky.vclasstracker.data.remote.VClassSessionPolicy

private val AppBackground = Color(0xFFF4F5F6)
private val HeaderBackground = Color(0xFFDCE8F2)
private val Primary = Color(0xFF183B56)
private val PrimarySoft = Color(0xFFE8EEF3)
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
fun TaskDetailScreen(
    deadline: VClassDeadline,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val now = rememberDeadlineClock()
    val time = VClassClient.createDeadlineTimeInfo(deadline.deadlineTimestamp, now)
    val displayedDeadline = deadline.copy(urgency = time.urgency, timeLeft = time.timeLeft)

    val statusColor =
        when (displayedDeadline.urgency) {
            "expired" -> Urgent
            "urgent" -> Urgent
            "soon" -> Soon
            else -> Safe
        }

    val statusBackground =
        when (displayedDeadline.urgency) {
            "expired" -> UrgentSoft
            "urgent" -> UrgentSoft
            "soon" -> SoonSoft
            else -> SafeSoft
        }

    val statusText =
        when (displayedDeadline.urgency) {
            "expired" -> "Terlewat"
            "urgent" -> "Mendesak"
            "soon" -> "Segera"
            else -> "Aman"
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                AppBackground
            )
            .verticalScroll(
                rememberScrollState()
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    HeaderBackground
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
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier
                        .clickable {
                            onBack()
                        },
                    shape = RoundedCornerShape(
                        11.dp
                    ),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                White.copy(
                                    alpha = 0.85f
                                )
                        )
                ) {
                    Text(
                        text = "←",
                        color = Primary,
                        fontSize = 20.sp,
                        fontWeight =
                            FontWeight.Bold,
                        modifier = Modifier
                            .padding(
                                horizontal = 14.dp,
                                vertical = 8.dp
                            )
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
                        text = "Detail Tugas",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(
                            3.dp
                        )
                    )

                    Text(
                        text = "Informasi deadline VClass",
                        color = TextSecondary,
                        fontSize = 10.sp
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
                            statusBackground
                        )
                        .padding(
                            horizontal = 10.dp,
                            vertical = 7.dp
                        )
                ) {
                    Text(
                        text = statusText.uppercase(),
                        color = statusColor,
                        fontSize = 8.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 20.dp
                )
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
                            Primary
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
                                        alpha = 0.12f
                                    )
                                )
                                .padding(
                                    horizontal = 9.dp,
                                    vertical = 6.dp
                                )
                        ) {
                            Text(
                                text =
                                    displayedDeadline.type
                                        .uppercase(),
                                color = Color(
                                    0xFFE2C178
                                ),
                                fontSize = 8.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        Spacer(
                            modifier = Modifier
                                .weight(
                                    1f
                                )
                        )

                        Text(
                            text = displayedDeadline.timeLeft,
                            color = White,
                            fontSize = 10.sp,
                            fontWeight =
                                FontWeight.SemiBold
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(
                            18.dp
                        )
                    )

                    Text(
                        text = displayedDeadline.title,
                        color = White,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight =
                            FontWeight.Bold,
                        maxLines = 5,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier = Modifier.height(
                            10.dp
                        )
                    )

                    Text(
                        text =
                            displayedDeadline.courseName
                                .ifBlank {
                                    "Mata kuliah tidak diketahui"
                                },
                        color = Color(
                            0xFFC5D4DF
                        ),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(
                    18.dp
                )
            )

            Text(
                text = "Informasi",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(
                    10.dp
                )
            )

            DetailItem(
                label = "Mata kuliah",
                value =
                    displayedDeadline.courseName
                        .ifBlank {
                            "-"
                        },
                symbol = "MK"
            )

            Spacer(
                modifier = Modifier.height(
                    9.dp
                )
            )

            DetailItem(
                label = "Kode kelas",
                value =
                    displayedDeadline.courseCode
                        .ifBlank {
                            "-"
                        },
                symbol = "#"
            )

            Spacer(
                modifier = Modifier.height(
                    9.dp
                )
            )

            DetailItem(
                label = "Jenis aktivitas",
                value =
                    displayedDeadline.type
                        .ifBlank {
                            "-"
                        },
                symbol = "T"
            )

            Spacer(
                modifier = Modifier.height(
                    9.dp
                )
            )

            DetailItem(
                label = "Deadline",
                value =
                    displayedDeadline.deadlineDisplay
                        .ifBlank {
                            "-"
                        },
                symbol = "D"
            )

            Spacer(
                modifier = Modifier.height(
                    9.dp
                )
            )

            DetailItem(
                label = "Sisa waktu",
                value =
                    displayedDeadline.timeLeft
                        .ifBlank {
                            "-"
                        },
                symbol = "⏱",
                valueColor =
                    statusColor
            )

            Spacer(
                modifier = Modifier.height(
                    9.dp
                )
            )

            DetailItem(
                label = "Status",
                value = statusText,
                symbol = "!",
                valueColor =
                    statusColor,
                background =
                    statusBackground
            )

            Spacer(
                modifier = Modifier.height(
                    24.dp
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            14.dp
                        )
                    )
                    .background(
                        Primary
                    )
                    .clickable(
                        enabled =
                            VClassSessionPolicy.officialUrl(displayedDeadline.url) != null
                    ) {
                        VClassLinks.open(context, displayedDeadline.url)
                    }
                    .padding(
                        vertical = 14.dp
                    ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    text =
                        if (
                            VClassSessionPolicy.officialUrl(displayedDeadline.url) != null
                        ) {
                            "Buka di VClass"
                        } else {
                            "Link VClass tidak tersedia"
                        },
                    color = White,
                    fontSize = 12.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(
                    12.dp
                )
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(
                    16.dp
                ),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            PrimarySoft
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
                        text =
                            "  Detail ini diambil dari data deadline akun VClass.",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .weight(
                                1f
                            )
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(
                    28.dp
                )
            )
        }
    }
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
    symbol: String,
    valueColor: Color = TextPrimary,
    background: Color = White
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(
            15.dp
        ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    background
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
                    15.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(
                        42.dp
                    )
                    .clip(
                        RoundedCornerShape(
                            12.dp
                        )
                    )
                    .background(
                        PrimarySoft
                    ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    text = symbol,
                    color = Primary,
                    fontSize = 11.sp,
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
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 9.sp
                )

                Spacer(
                    modifier = Modifier.height(
                        4.dp
                    )
                )

                Text(
                    text = value,
                    color = valueColor,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight =
                        FontWeight.SemiBold
                )
            }
        }
    }
}
