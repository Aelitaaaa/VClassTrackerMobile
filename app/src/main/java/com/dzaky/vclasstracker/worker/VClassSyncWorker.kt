package com.dzaky.vclasstracker.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dzaky.vclasstracker.data.remote.VClassClient
import com.dzaky.vclasstracker.notification.NotificationHelper
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class VClassSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParams
) {

    override suspend fun doWork(): Result = syncMutex.withLock { sync() }

    private suspend fun sync(): Result {
        Log.i(
            TAG,
            "Kaede mulai background sync"
        )

        return try {
            withContext(Dispatchers.IO) { VClassClient.initialize(applicationContext) }
            val version = VClassClient.currentSessionVersion()

            val displayName =
                VClassClient.getRestoredDisplayName()

            if (displayName.isBlank()) {
                Log.i(
                    TAG,
                    "Tidak ada session VClass"
                )

                return Result.success()
            }

            Log.i(
                TAG,
                "Session ditemukan"
            )

            val deadlineResult = withContext(Dispatchers.IO) { VClassClient.getDeadlines() }
            currentCoroutineContext().ensureActive()

            if (!deadlineResult.success) {
                if (deadlineResult.sessionExpired) {
                    VClassClient.withNoSession {
                        cancel(applicationContext)
                        reset(applicationContext)
                        NotificationHelper.clearAll(applicationContext)
                    }
                    return Result.success()
                }
                if (!VClassClient.isSessionCurrent(version)) return Result.success()
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
            if (isStopped || !VClassClient.isSessionCurrent(version)) return Result.success()
            val deadlines =
                deadlineResult.deadlines.distinctBy { it.id }

            Log.i(
                TAG,
                "Deadline ditemukan: ${deadlines.size}"
            )

            val preferences =
                applicationContext.getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

            val initialized = preferences.getBoolean(KEY_INITIALIZED, false) && preferences.getInt("schema", 0) == 2

            val knownIds =
                preferences.getStringSet(
                    KEY_KNOWN_DEADLINES,
                    emptySet()
                )
                    ?.toMutableSet()
                    ?: mutableSetOf()

            val remindedThreeDays =
                preferences.getStringSet(
                    KEY_REMINDER_THREE_DAYS,
                    emptySet()
                )
                    ?.toMutableSet()
                    ?: mutableSetOf()

            val remindedOneDay =
                preferences.getStringSet(
                    KEY_REMINDER_ONE_DAY,
                    emptySet()
                )
                    ?.toMutableSet()
                    ?: mutableSetOf()

            val remindedThreeHours =
                preferences.getStringSet(
                    KEY_REMINDER_THREE_HOURS,
                    emptySet()
                )
                    ?.toMutableSet()
                    ?: mutableSetOf()

            val activeIds = deadlines.map { it.id }.toSet()
            knownIds.retainAll(activeIds)
            remindedThreeDays.retainAll(activeIds)
            remindedOneDay.retainAll(activeIds)
            remindedThreeHours.retainAll(activeIds)
            if (!initialized) {
                VClassClient.withCurrentSession(version) { NotificationHelper.clearAll(applicationContext) }
                knownIds.clear()
                knownIds.addAll(activeIds)
                remindedThreeDays.clear()
                remindedOneDay.clear()
                remindedThreeHours.clear()
            }
            val now =
                System.currentTimeMillis() /
                    1000L

            var newTaskCount =
                0

            var reminderCount =
                0

            for (
                deadline in deadlines
            ) {
                currentCoroutineContext().ensureActive()
                if (isStopped || !VClassClient.isSessionCurrent(version)) return Result.success()
                val deadlineId =
                    deadline.id

                val secondsLeft =
                    deadline.deadlineTimestamp -
                        now

                if (secondsLeft <= 0L) {
                    continue
                }

                if (
                    deadlineId !in knownIds
                ) {
                    val notified =
                        NotificationHelper
                            .showNewTaskNotification(
                                context = applicationContext,
                                displayName = displayName,
                                deadline = deadline,
                                sessionVersion = version
                            )

                    if (notified) {
                        knownIds.add(
                            deadlineId
                        )

                        newTaskCount++

                        markCurrentReminderLevel(
                            deadlineId = deadlineId,
                            secondsLeft = secondsLeft,
                            remindedThreeDays = remindedThreeDays,
                            remindedOneDay = remindedOneDay,
                            remindedThreeHours = remindedThreeHours
                        )
                    }

                    continue
                }

                when {
                    secondsLeft <= THREE_HOURS_SECONDS -> {
                        if (
                            deadlineId !in remindedThreeHours
                        ) {
                            val notified =
                                NotificationHelper
                                    .showDeadlineNotification(
                                        context = applicationContext,
                                        displayName = displayName,
                                        deadline = deadline,
                                        sessionVersion = version
                                    )

                            if (notified) {
                                remindedThreeHours.add(
                                    deadlineId
                                )

                                remindedOneDay.add(
                                    deadlineId
                                )

                                remindedThreeDays.add(
                                    deadlineId
                                )

                                reminderCount++
                            }
                        }
                    }

                    secondsLeft <= ONE_DAY_SECONDS -> {
                        if (
                            deadlineId !in remindedOneDay
                        ) {
                            val notified =
                                NotificationHelper
                                    .showDeadlineNotification(
                                        context = applicationContext,
                                        displayName = displayName,
                                        deadline = deadline,
                                        sessionVersion = version
                                    )

                            if (notified) {
                                remindedOneDay.add(
                                    deadlineId
                                )

                                remindedThreeDays.add(
                                    deadlineId
                                )

                                reminderCount++
                            }
                        }
                    }

                    secondsLeft <= THREE_DAYS_SECONDS -> {
                        if (
                            deadlineId !in remindedThreeDays
                        ) {
                            val notified =
                                NotificationHelper
                                    .showDeadlineNotification(
                                        context = applicationContext,
                                        displayName = displayName,
                                        deadline = deadline,
                                        sessionVersion = version
                                    )

                            if (notified) {
                                remindedThreeDays.add(
                                    deadlineId
                                )

                                reminderCount++
                            }
                        }
                    }
                }
            }

            if (VClassClient.withCurrentSession(version) {
                preferences
                    .edit()
                    .putInt("schema", 2)
                    .putBoolean(KEY_INITIALIZED, true)
                    .putStringSet(
                        KEY_KNOWN_DEADLINES,
                        knownIds
                    )
                    .putStringSet(
                        KEY_REMINDER_THREE_DAYS,
                        remindedThreeDays
                    )
                    .putStringSet(
                        KEY_REMINDER_ONE_DAY,
                        remindedOneDay
                    )
                    .putStringSet(
                        KEY_REMINDER_THREE_HOURS,
                        remindedThreeHours
                    )
                    .putLong(
                        KEY_LAST_SYNC,
                        System.currentTimeMillis()
                    )
                    .apply()

                true
            } != true) return Result.success()

            Log.i(
                TAG,
                "Background sync selesai. Tugas baru: $newTaskCount, reminder: $reminderCount"
            )

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Log.w(TAG, "Background sync belum berhasil")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun markCurrentReminderLevel(
        deadlineId: String,
        secondsLeft: Long,
        remindedThreeDays: MutableSet<String>,
        remindedOneDay: MutableSet<String>,
        remindedThreeHours: MutableSet<String>
    ) {
        when {
            secondsLeft <= THREE_HOURS_SECONDS -> {
                remindedThreeDays.add(
                    deadlineId
                )

                remindedOneDay.add(
                    deadlineId
                )

                remindedThreeHours.add(
                    deadlineId
                )
            }

            secondsLeft <= ONE_DAY_SECONDS -> {
                remindedThreeDays.add(
                    deadlineId
                )

                remindedOneDay.add(
                    deadlineId
                )
            }

            secondsLeft <= THREE_DAYS_SECONDS -> {
                remindedThreeDays.add(
                    deadlineId
                )
            }
        }
    }

    companion object {
        private val syncMutex = Mutex()


        private const val TAG =
            "KaedeSync"

        private const val WORK_NAME =
            "vclass_kaede_background_sync"

        private const val TEST_WORK_NAME =
            "vclass_kaede_test_sync"

        private const val PREFS_NAME =
            "kaede_sync_state"

        private const val KEY_INITIALIZED =
            "initialized"

        private const val KEY_KNOWN_DEADLINES =
            "known_deadline_ids"

        private const val KEY_REMINDER_THREE_DAYS =
            "reminder_three_days"

        private const val KEY_REMINDER_ONE_DAY =
            "reminder_one_day"

        private const val KEY_REMINDER_THREE_HOURS =
            "reminder_three_hours"

        private const val KEY_LAST_SYNC =
            "last_sync"

        private const val THREE_HOURS_SECONDS =
            3L * 60L * 60L

        private const val ONE_DAY_SECONDS =
            24L * 60L * 60L

        private const val THREE_DAYS_SECONDS =
            3L * 24L * 60L * 60L

        fun schedule(
            context: Context
        ) {
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(
                        NetworkType.CONNECTED
                    )
                    .build()

            val workRequest =
                PeriodicWorkRequestBuilder<VClassSyncWorker>(
                    15,
                    TimeUnit.MINUTES
                )
                    .setConstraints(
                        constraints
                    )
                    .build()

            WorkManager
                .getInstance(
                    context
                )
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )

            Log.i(
                TAG,
                "Periodic sync Kaede dijadwalkan"
            )
        }

        fun runNow(
            context: Context
        ) {
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(
                        NetworkType.CONNECTED
                    )
                    .build()

            val request =
                OneTimeWorkRequestBuilder<VClassSyncWorker>()
                    .setConstraints(
                        constraints
                    )
                    .build()

            WorkManager
                .getInstance(
                    context
                )
                .enqueueUniqueWork(
                    TEST_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )

            Log.i(
                TAG,
                "Manual background sync diminta"
            )
        }

        fun cancel(
            context: Context
        ) {
            WorkManager
                .getInstance(
                    context
                )
                .cancelUniqueWork(
                    WORK_NAME
                )

            WorkManager
                .getInstance(
                    context
                )
                .cancelUniqueWork(
                    TEST_WORK_NAME
                )

            Log.i(
                TAG,
                "Sync Kaede dibatalkan"
            )
        }

        fun reset(
            context: Context
        ) {
            context
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .edit()
                .clear()
                .apply()

            Log.i(
                TAG,
                "Data sync Kaede direset"
            )
        }
    }
}
