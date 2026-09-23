package com.dzaky.vclasstracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Primary = Color(0xFF183B56)
private val PrimarySoft = Color(0xFFE8EEF3)
private val TextSecondary = Color(0xFF8F969B)
private val White = Color.White

@Composable
fun AppBottomNavigation(
    currentPage: String,
    onOpenDashboard: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenCourses: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = 14.dp,
                end = 14.dp,
                bottom = 8.dp
            ),
        shape = RoundedCornerShape(
            20.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 7.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 9.dp
                ),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationItem(
                label = "Beranda",
                selected = currentPage == "dashboard",
                onClick = onOpenDashboard
            )

            NavigationItem(
                label = "Tugas",
                selected = currentPage == "tasks",
                onClick = onOpenTasks
            )

            NavigationItem(
                label = "Kelas",
                selected = currentPage == "courses",
                onClick = onOpenCourses
            )

            NavigationItem(
                label = "Profil",
                selected = currentPage == "profile",
                onClick = onOpenProfile
            )
        }
    }
}

@Composable
private fun NavigationItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(
                RoundedCornerShape(
                    10.dp
                )
            )
            .background(
                if (selected) {
                    PrimarySoft
                } else {
                    Color.Transparent
                }
            )
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 16.dp,
                vertical = 9.dp
            )
    ) {
        Text(
            text = label,
            color =
                if (selected) {
                    Primary
                } else {
                    TextSecondary
                },
            fontSize = 9.sp,
            fontWeight =
                if (selected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                }
        )
    }
}