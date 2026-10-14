package com.dzaky.vclasstracker.ui.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AppBackground = Color(0xFFF3F5F7)
private val HeroBackground = Color(0xFFDCE8F2)
private val HeroSecondary = Color(0xFFC8D9E7)
private val Primary = Color(0xFF183B56)
private val Accent = Color(0xFFD6A34A)
private val TextPrimary = Color(0xFF17212B)
private val TextSecondary = Color(0xFF66727D)
private val White = Color(0xFFFFFFFF)

@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit
) {
    var authenticating by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    if (authenticating) {
        MicrosoftAuthScreen(
            onCancel = { authenticating = false },
            onLoginSuccess = { name -> authenticating = false; onLoginSuccess(name) }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(scrollState)
            .imePadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeroBackground)
                .statusBarsPadding()
                .padding(
                    start = 22.dp,
                    end = 22.dp,
                    top = 18.dp,
                    bottom = 64.dp
                )
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .align(Alignment.TopEnd)
                    .offset(
                        x = 55.dp,
                        y = (-28).dp
                    )
                    .clip(CircleShape)
                    .background(
                        HeroSecondary.copy(
                            alpha = 0.52f
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.CenterEnd)
                    .offset(
                        x = 30.dp,
                        y = 65.dp
                    )
                    .clip(CircleShape)
                    .background(
                        Accent.copy(
                            alpha = 0.16f
                        )
                    )
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VClassLogo(
                        modifier = Modifier.size(48.dp)
                    )

                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f)
                    ) {
                        Text(
                            text = "VClass Tracker",
                            color = Primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4).sp
                        )

                        Text(
                            text = "Gunadarma companion",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    7.dp
                                )
                            )
                            .background(
                                White.copy(
                                    alpha = 0.7f
                                )
                            )
                            .padding(
                                horizontal = 10.dp,
                                vertical = 6.dp
                            )
                    ) {
                        Text(
                            text = "Student",
                            color = Primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(
                        32.dp
                    )
                )

                Text(
                    text = "VClass tanpa ribet.",
                    color = TextPrimary,
                    fontSize = 31.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp
                )

                Spacer(
                    modifier = Modifier.height(
                        8.dp
                    )
                )

                Text(
                    text = "Tugas, kuis, forum, dan mata kuliah dalam satu aplikasi.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth(
                        0.82f
                    )
                )

                Spacer(
                    modifier = Modifier.height(
                        26.dp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        9.dp
                    )
                ) {
                    FeatureItem(
                        number = "01",
                        title = "Deadline",
                        modifier = Modifier.weight(1f)
                    )

                    FeatureItem(
                        number = "02",
                        title = "Courses",
                        modifier = Modifier.weight(1f)
                    )

                    FeatureItem(
                        number = "03",
                        title = "Sync",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .offset(y = (-32).dp),
            shape = RoundedCornerShape(
                20.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = White
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 5.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 22.dp,
                        vertical = 24.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Selamat datang",
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )

                        Spacer(
                            modifier = Modifier.height(
                                4.dp
                            )
                        )

                        Text(
                            text = "Gunakan akun Microsoft mahasiswa UG",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(
                                RoundedCornerShape(
                                    11.dp
                                )
                            )
                            .background(
                                Color(
                                    0xFFF1F4F6
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "UG",
                            color = Primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(
                        24.dp
                    )
                )

                Button(
                    onClick = {
                        authenticating = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(
                        11.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = White,
                        disabledContainerColor = Color(
                            0xFFAAB7C0
                        ),
                        disabledContentColor = White
                    )
                ) {
                    Text(
                        text = "Masuk dengan Microsoft",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(
                    modifier = Modifier.height(
                        20.dp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color(
                            0xFFE6E8EA
                        )
                    )

                    Text(
                        text = "  aman & langsung  ",
                        color = Color(
                            0xFF9A9EA2
                        ),
                        fontSize = 9.sp
                    )

                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color(
                            0xFFE6E8EA
                        )
                    )
                }

                Spacer(
                    modifier = Modifier.height(
                        16.dp
                    )
                )

                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .offset(y = 4.dp)
                            .clip(CircleShape)
                            .background(Accent)
                    )

                    Text(
                        text = "Login dilakukan melalui halaman resmi Microsoft. VClass Tracker tidak menerima atau menyimpan password Microsoft.",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = 30.dp
                )
                .offset(y = (-15).dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "VClass Tracker",
                    color = Color(
                        0xFF979DA2
                    ),
                    fontSize = 10.sp
                )

                Text(
                    text = "Android · v1.0",
                    color = Color(
                        0xFF979DA2
                    ),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun FeatureItem(
    number: String,
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    12.dp
                )
            )
            .background(
                White.copy(
                    alpha = 0.72f
                )
            )
            .padding(
                horizontal = 12.dp,
                vertical = 12.dp
            )
    ) {
        Text(
            text = number,
            color = Accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(
                5.dp
            )
        )

        Text(
            text = title,
            color = Primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun VClassLogo(
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
    ) {
        drawRoundRect(
            color = Primary,
            size = size,
            cornerRadius = CornerRadius(
                size.width * 0.23f,
                size.height * 0.23f
            )
        )

        drawRoundRect(
            color = Accent,
            topLeft = Offset(
                size.width * 0.67f,
                size.height * 0.13f
            ),
            size = Size(
                size.width * 0.18f,
                size.height * 0.18f
            ),
            cornerRadius = CornerRadius(
                size.width * 0.04f,
                size.height * 0.04f
            )
        )

        val path = Path().apply {
            moveTo(
                size.width * 0.22f,
                size.height * 0.30f
            )

            lineTo(
                size.width * 0.42f,
                size.height * 0.72f
            )

            lineTo(
                size.width * 0.78f,
                size.height * 0.30f
            )

            lineTo(
                size.width * 0.61f,
                size.height * 0.30f
            )

            lineTo(
                size.width * 0.43f,
                size.height * 0.53f
            )

            lineTo(
                size.width * 0.33f,
                size.height * 0.30f
            )

            close()
        }

        drawPath(
            path = path,
            color = White
        )
    }
}