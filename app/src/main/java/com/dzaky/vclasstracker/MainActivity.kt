package com.dzaky.vclasstracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.dzaky.vclasstracker.ui.login.clearWebAuthenticationData
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.lifecycleScope
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

    private var verifyAfterResume = false

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

        NotificationHelper.createNotificationChannel(this)

        setContent {
            MaterialTheme {
                var loggedInUser by remember {
                    mutableStateOf<String?>(null)
                }

                var currentPage by rememberSaveable {
                    mutableStateOf("dashboard")
                }

                var checkingSession by remember {
                    mutableStateOf(true)
                }

                var selectedDeadline by remember {
                    mutableStateOf<VClassDeadline?>(null)
                }

                var savedDeadlineId by rememberSaveable { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                val sessionChange by VClassClient.sessionChanges.collectAsState()
                var restoreAttempt by remember { mutableStateOf(0) }
                var startupError by remember { mutableStateOf("") }

                fun resetAuthenticatedUi() {
                    VClassSyncWorker.cancel(this@MainActivity)
                    VClassSyncWorker.reset(this@MainActivity)
                    NotificationHelper.clearAll(this@MainActivity)
                    clearWebAuthenticationData()
                    selectedDeadline = null
                    savedDeadlineId = null
                    notificationTargetDeadlineId = null
                    notificationTargetPage = null
                    currentPage = "dashboard"
                    loggedInUser = null
                }

                LaunchedEffect(sessionChange) {
                    if (loggedInUser != null && !VClassClient.isSessionCurrent(sessionChange)) {
                        resetAuthenticatedUi()
                    }
                }

                LaunchedEffect(restoreAttempt) {
                    checkingSession = true
                    startupError = ""
                    val result = withContext(Dispatchers.IO) {
                        VClassClient.initialize(this@MainActivity)
                        if (VClassClient.sessionCookies().any { it.name == "MoodleSession" })
                            VClassClient.verifySession() else null
                    }
                    if (result?.success == true) {
                        loggedInUser = result.displayName
                        currentPage = notificationTargetPage ?: currentPage
                        notificationTargetPage = null
                        if (currentPage == "task_detail" && savedDeadlineId != null) {
                            notificationTargetDeadlineId = savedDeadlineId
                            currentPage = "tasks"
                        }
                        VClassSyncWorker.schedule(this@MainActivity)
                        VClassSyncWorker.runNow(this@MainActivity)
                        requestNotificationPermission()
                    } else if (result != null && !result.sessionExpired) {
                        startupError = result.message
                    } else {
                        VClassSyncWorker.cancel(this@MainActivity)
                        VClassSyncWorker.reset(this@MainActivity)
                        NotificationHelper.clearAll(this@MainActivity)
                        clearWebAuthenticationData()
                    }
                    checkingSession = false
                }

                BackHandler(enabled = loggedInUser != null && currentPage != "dashboard") {
                    currentPage = when (currentPage) {
                        "task_detail" -> "tasks"
                        "add_course" -> "courses"
                        else -> "dashboard"
                    }
                    if (currentPage != "task_detail") { selectedDeadline = null; savedDeadlineId = null }
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
                } else if (startupError.isNotBlank()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(startupError)
                            Button(onClick = { restoreAttempt++ }) { Text("Coba lagi") }
                        }
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

                            selectedDeadline = null
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
                                        deadline.id ==
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
                                    selectedDeadline = deadline
                                    savedDeadlineId = deadline.id
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
                                        selectedDeadline = null
                                        savedDeadlineId = null
                                        currentPage = "tasks"
                                    }
                                )
                            } else {
                                LaunchedEffect(Unit) { currentPage = "tasks" }
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
                                    if (!checkingSession) {
                                        checkingSession = true
                                        scope.launch {
                                            withContext(Dispatchers.IO) { VClassClient.clearSession() }
                                            resetAuthenticatedUi()
                                            checkingSession = false
                                        }
                                    }
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

    override fun onPause() {
        verifyAfterResume = true
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (verifyAfterResume && VClassClient.getRestoredDisplayName().isNotBlank()) {
            lifecycleScope.launch { withContext(Dispatchers.IO) { VClassClient.verifySession() } }
        }
        verifyAfterResume = false
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
                intent.getStringExtra("open_deadline_id")

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