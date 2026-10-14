package com.dzaky.vclasstracker.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dzaky.vclasstracker.MainActivity
import com.dzaky.vclasstracker.R
import com.dzaky.vclasstracker.assistant.KaedePersona
import com.dzaky.vclasstracker.data.remote.VClassDeadline
import com.dzaky.vclasstracker.data.remote.VClassClient

object NotificationHelper {

    private const val CHANNEL_ID =
        "kaede_deadline_channel"

    private const val CHANNEL_NAME =
        "Kaede Deadline"

    private const val CHANNEL_DESCRIPTION =
        "Pengingat tugas dan deadline VClass dari Kaede"

    fun createNotificationChannel(
        context: Context
    ) {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description =
                        CHANNEL_DESCRIPTION

                    enableVibration(
                        true
                    )

                    vibrationPattern =
                        longArrayOf(
                            0,
                            250,
                            150,
                            250
                        )
                }

            val manager =
                context.getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    @SuppressLint("MissingPermission") // Checked below; also handle a permission revoked before notify.
    fun showDeadlineNotification(
        context: Context,
        displayName: String,
        deadline: VClassDeadline,
        sessionVersion: Long = VClassClient.currentSessionVersion()
    ): Boolean {
        createNotificationChannel(
            context
        )

        if (
            !hasNotificationPermission(
                context
            )
        ) {
            return false
        }

        val pendingIntent =
            createDeadlinePendingIntent(
                context = context,
                deadline = deadline
            )

        val title =
            KaedePersona.notificationTitle(
                deadline
            )

        val message =
            KaedePersona.deadlineReminder(
                displayName = displayName,
                deadline = deadline
            )

        val priority =
            when (
                deadline.urgency
            ) {
                "urgent" ->
                    NotificationCompat.PRIORITY_MAX

                "soon" ->
                    NotificationCompat.PRIORITY_HIGH

                else ->
                    NotificationCompat.PRIORITY_DEFAULT
            }

        val avatarResource =
            if (
                isThreeHoursOrLess(
                    deadline
                )
            ) {
                R.drawable.kaede_angry
            } else {
                R.drawable.kaede_avatar
            }

        val kaedeAvatar =
            getKaedeBitmap(
                context = context,
                resourceId = avatarResource
            )

        val builder =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle(
                    title
                )
                .setContentText(
                    message
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(
                            message
                        )
                        .setBigContentTitle(
                            title
                        )
                        .setSummaryText(
                            deadline.courseName
                        )
                )
                .setSubText(
                    deadline.courseName
                )
                .setContentIntent(
                    pendingIntent
                )
                .setAutoCancel(
                    true
                )
                .setOnlyAlertOnce(false)
                .setTimeoutAfter(maxOf(1000L, deadline.deadlineTimestamp * 1000L - System.currentTimeMillis()))
                .setPriority(
                    priority
                )
                .setCategory(
                    NotificationCompat.CATEGORY_REMINDER
                )
                .setVisibility(
                    NotificationCompat.VISIBILITY_PRIVATE
                )

        if (kaedeAvatar != null) {
            builder.setLargeIcon(
                kaedeAvatar
            )
        }

        val notificationId =
            deadline.id
                .hashCode() and
                0x7fffffff

        return VClassClient.withCurrentSession(sessionVersion) {
            if (deadline.deadlineTimestamp <= System.currentTimeMillis() / 1000L) false
            else try {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
                true
            } catch (_: SecurityException) { false }
        } ?: false
    }

    @SuppressLint("MissingPermission") // Checked below; also handle a permission revoked before notify.
    fun showNewTaskNotification(
        context: Context,
        displayName: String,
        deadline: VClassDeadline,
        sessionVersion: Long = VClassClient.currentSessionVersion()
    ): Boolean {
        createNotificationChannel(
            context
        )

        if (
            !hasNotificationPermission(
                context
            )
        ) {
            return false
        }

        val pendingIntent =
            createDeadlinePendingIntent(
                context = context,
                deadline = deadline
            )

        val title =
            "Kaede · Ada ${deadline.type.lowercase()} baru!"

        val message =
            KaedePersona.newTask(
                displayName = displayName,
                deadline = deadline
            )

        val kaedeAvatar =
            getKaedeBitmap(
                context = context,
                resourceId =
                    R.drawable.kaede_surprised
            )

        val builder =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle(
                    title
                )
                .setContentText(
                    message
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(
                            message
                        )
                        .setBigContentTitle(
                            title
                        )
                        .setSummaryText(
                            deadline.courseName
                        )
                )
                .setSubText(
                    deadline.courseName
                )
                .setContentIntent(
                    pendingIntent
                )
                .setAutoCancel(
                    true
                )
                .setOnlyAlertOnce(false)
                .setTimeoutAfter(maxOf(1000L, deadline.deadlineTimestamp * 1000L - System.currentTimeMillis()))
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setCategory(
                    NotificationCompat.CATEGORY_REMINDER
                )
                .setVisibility(
                    NotificationCompat.VISIBILITY_PRIVATE
                )

        if (kaedeAvatar != null) {
            builder.setLargeIcon(
                kaedeAvatar
            )
        }

        val notificationId = deadline.id.hashCode() and 0x7fffffff

        return VClassClient.withCurrentSession(sessionVersion) {
            if (deadline.deadlineTimestamp <= System.currentTimeMillis() / 1000L) false
            else try {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
                true
            } catch (_: SecurityException) { false }
        } ?: false
    }

    fun clearAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun createDeadlinePendingIntent(
        context: Context,
        deadline: VClassDeadline
    ): PendingIntent {
        val intent =
            Intent(
                context,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

                putExtra(
                    "open_deadline_id",
                    deadline.id
                )
            }

        return PendingIntent.getActivity(
            context,
            deadline.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getKaedeBitmap(
        context: Context,
        resourceId: Int
    ): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resourceId, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
            BitmapFactory.decodeResource(context.resources, resourceId, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) {
            null
        }
    }

    private fun isThreeHoursOrLess(
        deadline: VClassDeadline,
        sessionVersion: Long = VClassClient.currentSessionVersion()
    ): Boolean {
        val now =
            System.currentTimeMillis() /
                1000L

        val secondsLeft =
            deadline.deadlineTimestamp -
                now

        return secondsLeft in
            1..10800
    }

    private fun hasNotificationPermission(
        context: Context
    ): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
        if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            return true
        }

        return context.checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS
        ) ==
            PackageManager.PERMISSION_GRANTED
    }
}