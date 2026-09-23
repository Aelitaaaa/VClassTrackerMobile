package com.dzaky.vclasstracker.ui.profile

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dzaky.vclasstracker.ui.components.AppBottomNavigation
import com.dzaky.vclasstracker.worker.VClassSyncWorker

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
private val Danger = Color(0xFFA34848)
private val DangerSoft = Color(0xFFF7E8E8)
private val White = Color.White

@Composable
fun ProfileScreen(
    displayName: String,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenDashboard: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenCourses: () -> Unit = {}
) {
    val context = LocalContext.current

    var syncMessage by remember {
        mutableStateOf("")
    }

    val formattedName =
        formatName(
            displayName
        )

    val initials =
        getInitials(
            displayName
        )

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
                    start = 20.dp,
                    end = 20.dp,
                    top = 48.dp,
                    bottom = 30.dp
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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

                    Spacer(
                        modifier = Modifier
                            .weight(
                                1f
                            )
                    )

                    Text(
                        text = "Profil",
                        color = Primary,
                        fontSize = 18.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier
                            .weight(
                                1f
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(
                                48.dp
                            )
                    )
                }

                Spacer(
                    modifier = Modifier.height(
                        28.dp
                    )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(
                                88.dp
                            )
                            .clip(
                                CircleShape
                            )
                            .background(
                                Primary
                            ),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            color = White,
                            fontSize = 26.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(
                            15.dp
                        )
                    )

                    Text(
                        text = formattedName,
                        color = TextPrimary,
                        fontSize = 21.sp,
                        fontWeight =
                            FontWeight.Bold,
                        textAlign =
                            TextAlign.Center
                    )

                    Spacer(
                        modifier = Modifier.height(
                            7.dp
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
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 20.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {
            SectionTitle(
                title = "Akun"
            )

            ProfileInfoCard(
                title = "Nama pengguna",
                value = formattedName,
                symbol = "P"
            )

            ProfileInfoCard(
                title = "Status VClass",
                value = "Session aktif",
                symbol = "V",
                valueColor = Success
            )

            Spacer(
                modifier = Modifier.height(
                    8.dp
                )
            )

            SectionTitle(
                title = "Sinkronisasi"
            )

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
                    ),
                elevation =
                    CardDefaults.cardElevation(
                        defaultElevation =
                            1.dp
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
                        verticalAlignment =
                            Alignment.CenterVertically
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
                            contentAlignment =
                                Alignment.Center
                        ) {
                            Text(
                                text = "↻",
                                color = Accent,
                                fontSize = 21.sp,
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
                                text = "Sinkronisasi VClass",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                modifier = Modifier.height(
                                    3.dp
                                )
                            )

                            Text(
                                text = "Periksa kelas dan deadline terbaru",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(
                            15.dp
                        )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    12.dp
                                )
                            )
                            .background(
                                Primary
                            )
                            .clickable {
                                VClassSyncWorker.runNow(
                                    context
                                )

                                syncMessage =
                                    "Sinkronisasi dijalankan di background"
                            }
                            .padding(
                                vertical = 12.dp
                            ),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Text(
                            text = "Sinkronkan sekarang",
                            color = White,
                            fontSize = 11.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    if (
                        syncMessage.isNotBlank()
                    ) {
                        Spacer(
                            modifier = Modifier.height(
                                10.dp
                            )
                        )

                        Text(
                            text = syncMessage,
                            color = Success,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .fillMaxWidth(),
                            textAlign =
                                TextAlign.Center
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(
                    8.dp
                )
            )

            SectionTitle(
                title = "Aplikasi"
            )

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
                            17.dp
                        )
                ) {
                    Text(
                        text = "VClass Tracker",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(
                            5.dp
                        )
                    )

                    Text(
                        text = "Aplikasi companion untuk membantu memantau kelas dan deadline VClass.",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(
                        modifier = Modifier.height(
                            14.dp
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    11.dp
                                )
                            )
                            .background(
                                PrimarySoft
                            )
                            .padding(
                                12.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kaede",
                            color = Primary,
                            fontSize = 11.sp,
                            fontWeight =
                                FontWeight.Bold,
                            modifier = Modifier
                                .weight(
                                    1f
                                )
                        )

                        Text(
                            text = "VClass Assistant",
                            color = TextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(
                    8.dp
                )
            )

            SectionTitle(
                title = "Session"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(
                    17.dp
                ),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            DangerSoft
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            17.dp
                        )
                ) {
                    Text(
                        text = "Keluar dari VClass",
                        color = Danger,
                        fontSize = 13.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(
                            5.dp
                        )
                    )

                    Text(
                        text = "Session lokal akan dihapus dan aplikasi kembali ke halaman login.",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )

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
                                    12.dp
                                )
                            )
                            .background(
                                Danger
                            )
                            .clickable {
                                onLogout()
                            }
                            .padding(
                                vertical = 12.dp
                            ),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Text(
                            text = "Logout",
                            color = White,
                            fontSize = 11.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(
                    25.dp
                )
            )

            Text(
                text = "VClass Tracker",
                color = TextSecondary,
                fontSize = 9.sp,
                modifier = Modifier
                    .fillMaxWidth(),
                textAlign =
                    TextAlign.Center
            )

            Text(
                text = "with Kaede",
                color = Accent,
                fontSize = 9.sp,
                fontWeight =
                    FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth(),
                textAlign =
                    TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(
                    15.dp
                )
            )
        }
    }

        AppBottomNavigation(
            currentPage = "profile",
            onOpenDashboard = onOpenDashboard,
            onOpenTasks = onOpenTasks,
            onOpenCourses = onOpenCourses,
            onOpenProfile = {},
            modifier = Modifier
                .align(
                    Alignment.BottomCenter
                )
        )
    }
}

@Composable
private fun SectionTitle(
    title: String
) {
    Text(
        text = title,
        color = TextPrimary,
        fontSize = 15.sp,
        fontWeight =
            FontWeight.Bold,
        modifier = Modifier.padding(
            start = 2.dp,
            bottom = 2.dp
        )
    )
}

@Composable
private fun ProfileInfoCard(
    title: String,
    value: String,
    symbol: String,
    valueColor: Color =
        TextSecondary
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
                        43.dp
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
                    fontSize = 14.sp,
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
                    text = title,
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
                    fontWeight =
                        FontWeight.SemiBold
                )
            }
        }
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
        .split(" ")
        .filter {
            it.isNotBlank()
        }
        .joinToString(" ") {
            it.replaceFirstChar { char ->
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
            .split(" ")
            .filter {
                it.isNotBlank()
            }

    if (parts.isEmpty()) {
        return "UG"
    }

    if (parts.size == 1) {
        return parts[0]
            .take(2)
            .uppercase()
    }

    return (
        parts[0].take(1) +
            parts[1].take(1)
        )
        .uppercase()
}