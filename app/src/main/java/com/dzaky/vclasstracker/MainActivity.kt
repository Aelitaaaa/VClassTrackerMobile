package com.dzaky.vclasstracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.dzaky.vclasstracker.data.remote.VClassClient
import com.dzaky.vclasstracker.data.remote.VClassDeadline
import com.dzaky.vclasstracker.notification.NotificationHelper
import com.dzaky.vclasstracker.ui.courses.AddCourseScreen
import com.dzaky.vclasstracker.ui.courses.CoursesScreen
import com.dzaky.vclasstracker.ui.dashboard.DashboardScreen
import com.dzaky.vclasstracker.ui.login.LoginScreen
import com.dzaky.vclasstracker.ui.profile.ProfileScreen
import com.dzaky.vclasstracker.ui.tasks.TaskDetailScreen
import com.dzaky.vclasstracker.ui.tasks.TasksScreen
import com.dzaky.vclasstracker.worker.VClassSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var notificationTargetPage by mutableStateOf<String?>(null)
    private var notificationTargetDeadlineId by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleNotificationIntent(intent)

        VClassClient.initialize(this)
        NotificationHelper.createNotificationChannel(this)

        setContent {
            MaterialTheme {
                var loggedInUser by remember {
                    mutableStateOf<String?>(null)
                }

                var currentPage by remember {
                    mutableStateOf("dashboard")
                }

                var checkingSession by remember {
                    mutableStateOf(true)
                }

                var selectedDeadline by remember {
                    mutableStateOf<VClassDeadline?>(null)
                }

                LaunchedEffect(Unit) {
                    val restoredName =
                        VClassClient.getRestoredDisplayName()

                    if (restoredName.isNotBlank()) {
                        val sessionResult =
                            withContext(Dispatchers.IO) {
                                VClassClient.getMyCourses()
                            }

                        if (sessionResult.success) {
                            loggedInUser =
                                restoredName

                            currentPage =
                                notificationTargetPage
                                    ?: "dashboard"

                            notificationTargetPage =
                                null

                            VClassSyncWorker.schedule(
                                this@MainActivity
                            )

                            VClassSyncWorker.runNow(
                                this@MainActivity
                            )

                            requestNotificationPermission()
                        } else {
                            VClassSyncWorker.cancel(
                                this@MainActivity
                            )

                            VClassSyncWorker.reset(
                                this@MainActivity
                            )

                            VClassClient.clearSession()
                        }
                    }

                    checkingSession =
                        false
                }

                if (checkingSession) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (loggedInUser == null) {
                    LoginScreen(
                        onLoginSuccess = { displayName ->
                            loggedInUser =
                                displayName

                            currentPage =
                                notificationTargetPage
                                    ?: "dashboard"

                            notificationTargetPage =
                                null

                            VClassSyncWorker.reset(
                                this@MainActivity
                            )

                            VClassSyncWorker.schedule(
                                this@MainActivity
                            )

                            VClassSyncWorker.runNow(
                                this@MainActivity
                            )

                            requestNotificationPermission()
                        }
                    )
                } else {
                    LaunchedEffect(notificationTargetPage) {
                        if (
                            notificationTargetPage != null
                        ) {
                            currentPage =
                                notificationTargetPage!!

                            notificationTargetPage =
                                null
                        }
                    }

                    LaunchedEffect(notificationTargetDeadlineId) {
                        val targetDeadlineId =
                            notificationTargetDeadlineId

                        if (
                            targetDeadlineId != null
                        ) {
                            val deadlineResult =
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    VClassClient.getDeadlines()
                                }

                            val matchedDeadline =
                                deadlineResult.deadlines
                                    .firstOrNull { deadline ->
                                        deadline.id.toString() ==
                                            targetDeadlineId
                                    }

                            if (
                                matchedDeadline != null
                            ) {
                                selectedDeadline =
                                    matchedDeadline

                                currentPage =
                                    "task_detail"
                            } else {
                                currentPage =
                                    "tasks"
                            }

                            notificationTargetDeadlineId =
                                null
                        }
                    }

                    when (currentPage) {
                        "tasks" -> {
                            TasksScreen(
                                onBack = {
                                    currentPage =
                                        "dashboard"
                                },
                                onOpenDashboard = {
                                    currentPage =
                                        "dashboard"
                                },
                                onOpenCourses = {
                                    currentPage =
                                        "courses"
                                },
                                onOpenProfile = {
                                    currentPage =
                                        "profile"
                                },
                                onOpenTaskDetail = { deadline ->
                                    selectedDeadline =
                                        deadline

                                    currentPage =
                                        "task_detail"
                                }
                            )
                        }

                        "task_detail" -> {
                            val deadline =
                                selectedDeadline

                            if (deadline != null) {
                                TaskDetailScreen(
                                    deadline = deadline,
                                    onBack = {
                                        currentPage =
                                            "tasks"
                                    }
                                )
                            } else {
                                currentPage =
                                    "tasks"
                            }
                        }

                        "courses" -> {
                            CoursesScreen(
                                onBack = {
                                    currentPage =
                                        "dashboard"
                                },
                                onAddCourse = {
                                    currentPage =
                                        "add_course"
                                },
                                onOpenDashboard = {
                                    currentPage =
                                        "dashboard"
                                },
                                onOpenTasks = {
                                    currentPage =
                                        "tasks"
                                },
                                onOpenProfile = {
                                    currentPage =
                                        "profile"
                                }
                            )
                        }

                        "add_course" -> {
                            AddCourseScreen(
                                onBack = {
                                    currentPage =
                                        "courses"
                                },
                                onCourseSelected = {
                                    currentPage =
                                        "courses"
                                }
                            )
                        }

                        "profile" -> {
                            ProfileScreen(
                                displayName =
                                    loggedInUser!!,
                                onBack = {
                                    currentPage =
                                        "dashboard"
                                },
                                onLogout = {
                                    VClassSyncWorker.cancel(
                                        this@MainActivity
                                    )

                                    VClassSyncWorker.reset(
                                        this@MainActivity
                                    )

                                    VClassClient.clearSession()

                                    currentPage =
                                        "dashboard"

                                    loggedInUser =
                                        null
                                },
                                onOpenDashboard = {
                                    currentPage =
                                        "dashboard"
                                },
                                onOpenTasks = {
                                    currentPage =
                                        "tasks"
                                },
                                onOpenCourses = {
                                    currentPage =
                                        "courses"
                                }
                            )
                        }

                        else -> {
                            DashboardScreen(
                                displayName =
                                    loggedInUser!!,
                                onOpenCourses = {
                                    currentPage =
                                        "courses"
                                },
                                onOpenTasks = {
                                    currentPage =
                                        "tasks"
                                },
                                onOpenProfile = {
                                    currentPage =
                                        "profile"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (
            intent?.hasExtra(
                "open_deadline_id"
            ) == true
        ) {
            notificationTargetDeadlineId =
                intent.extras
                    ?.get(
                        "open_deadline_id"
                    )
                    ?.toString()

            notificationTargetPage =
                "tasks"

            intent.removeExtra(
                "open_deadline_id"
            )
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            val permissionStatus =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                )

            if (
                permissionStatus !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }
}