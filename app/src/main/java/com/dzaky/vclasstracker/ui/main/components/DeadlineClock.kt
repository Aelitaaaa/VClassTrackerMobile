package com.dzaky.vclasstracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.dzaky.vclasstracker.data.remote.VClassClient
import com.dzaky.vclasstracker.data.remote.VClassDeadline
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
internal fun rememberDeadlineClock(): Long {
    val now by produceState(Instant.now().epochSecond) {
        while (true) {
            value = Instant.now().epochSecond
            delay(60_000)
        }
    }
    return now
}

internal fun activeDeadlines(deadlines: List<VClassDeadline>, now: Long): List<VClassDeadline> =
    deadlines.filter { it.deadlineTimestamp > now }.map { deadline ->
        val time = VClassClient.createDeadlineTimeInfo(deadline.deadlineTimestamp, now)
        deadline.copy(timeLeft = time.timeLeft, urgency = time.urgency)
    }
