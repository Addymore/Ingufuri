package eu.hxreborn.biometricapplock.root

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.BiometricAuthActivity
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.OpaqueAuthActivity
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.ui.MainActivity
import eu.hxreborn.biometricapplock.util.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface RootServiceState {
    data object Stopped : RootServiceState

    data object Starting : RootServiceState

    data class Running(
        val startedAtMs: Long,
    ) : RootServiceState

    data object NoRoot : RootServiceState
}

class RootLockService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    @Volatile
    private var activeProcess: Process? = null

    private lateinit var prefs: SharedPreferences

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sp, _ ->
            RootLockState.reloadFromPrefs(applicationContext, sp)
            updateNotification()
        }

    private val screenOffReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == Intent.ACTION_SCREEN_OFF &&
                    RootLockState.globalRelockOnScreenOff
                ) {
                    Log.d(TAG, "screen off -> clearing root unlocked set")
                    RootLockState.clearUnlocked()
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(Prefs.GROUP, Context.MODE_PRIVATE)
        RootLockState.reloadFromPrefs(applicationContext, prefs)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (monitorJob?.isActive != true) {
            _serviceState.value = RootServiceState.Starting
            monitorJob = serviceScope.launch { runRootEventLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        runCatching { prefs.unregisterOnSharedPreferenceChangeListener(prefsListener) }
        activeProcess?.destroyForcibly()
        activeProcess = null
        serviceScope.cancel()
        _serviceState.value = RootServiceState.Stopped
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runRootEventLoop() {
        if (!RootShell.isRootGranted()) {
            Log.w(TAG, "root not granted; stopping RootLockService")
            _serviceState.value = RootServiceState.NoRoot
            stopSelf()
            return
        }

        val startedAt = System.currentTimeMillis()
        _serviceState.value = RootServiceState.Running(startedAt)
        Log.i(TAG, "Ingufuri Root Lock Engine running")

        while (serviceScope.isActive) {
            try {
                val cmd =
                    "logcat -b events -v tag -T 1 " +
                        "wm_set_resumed_activity:I wm_resume_activity:I wm_task_removed:I *:S"
                val proc = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
                activeProcess = proc

                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (!serviceScope.isActive) return@forEach
                        handleEventLine(line)
                    }
                }
                proc.waitFor()
            } catch (e: Exception) {
                Log.w(TAG, "root logcat stream interrupted: ${e.message}")
            } finally {
                activeProcess?.destroyForcibly()
                activeProcess = null
            }
            if (serviceScope.isActive) {
                delay(1_500L)
            }
        }
    }

    private fun handleEventLine(line: String) {
        when {
            line.contains("wm_task_removed") -> {
                val match = TASK_REMOVED_REGEX.find(line) ?: return
                val taskId = match.groupValues[1].toIntOrNull() ?: return
                RootLockState.onTaskRemoved(taskId)
            }

            line.contains("wm_resume_activity") -> {
                val match = RESUME_ACTIVITY_REGEX.find(line) ?: return
                val userId = match.groupValues[1].toIntOrNull() ?: 0
                val taskId = match.groupValues[2].toIntOrNull() ?: 0
                val component = match.groupValues[3]
                val (pkg, activity) = parseComponent(component) ?: return
                RootLockState.recordTask(taskId, pkg, userId)
                onActivityForeground(pkg, userId, activity)
            }

            line.contains("wm_set_resumed_activity") -> {
                val match = SET_RESUMED_REGEX.find(line) ?: return
                val userId = match.groupValues[1].toIntOrNull() ?: 0
                val component = match.groupValues[2]
                val (pkg, activity) = parseComponent(component) ?: return
                onActivityForeground(pkg, userId, activity)
            }
        }
    }

    private fun parseComponent(component: String): Pair<String, String>? {
        val slash = component.indexOf('/')
        if (slash <= 0) return null
        val pkg = component.substring(0, slash)
        val rawActivity = component.substring(slash + 1)
        val fullActivity =
            if (rawActivity.startsWith(".")) "$pkg$rawActivity" else rawActivity
        return pkg to fullActivity
    }

    private fun onActivityForeground(
        pkg: String,
        userId: Int,
        activity: String,
    ) {
        if (pkg == BuildConfig.APPLICATION_ID) return
        if (pkg == "com.android.systemui" ||
            pkg == "com.samsung.android.biometrics.app.setting"
        ) {
            return
        }

        RootLockState.relockOtherPackages(pkg, userId)

        // Check system installer / uninstaller protection
        if (RootLockState.shouldProtectInstaller() && RootLockState.isInstallerPackage(pkg)) {
            if (!RootLockState.isInstallerGrantFresh()) {
                val key = "installer:$pkg:$userId"
                if (RootLockState.tryMarkAuthInFlight(key)) {
                    Log.i(TAG, "root intercepting installer pkg=$pkg activity=$activity")
                    launchAuthPrompt(
                        pkg = pkg,
                        userId = userId,
                        activity = activity,
                        isInstaller = true,
                    )
                }
            }
            return
        }

        val key = RootLockState.packageKey(pkg, userId)
        if (key !in RootLockState.lockedPackages) return

        // Record recent activity for per-app activity filtering screen
        runCatching {
            App.from(applicationContext).appOverridesRepository.recordRecentActivity(key, activity)
        }

        if (RootLockState.isActivityExempt(pkg, userId, activity)) {
            return
        }

        if (RootLockState.isUnlocked(pkg, userId)) {
            RootLockState.markForegrounded(pkg, userId)
            return
        }

        if (!RootLockState.tryMarkAuthInFlight(key)) return

        Log.i(TAG, "root gating locked app pkg=$pkg user=$userId activity=$activity")
        launchAuthPrompt(
            pkg = pkg,
            userId = userId,
            activity = activity,
            isInstaller = false,
        )
    }

    private fun launchAuthPrompt(
        pkg: String,
        userId: Int,
        activity: String,
        isInstaller: Boolean,
    ) {
        val activityClass =
            if (RootLockState.globalUseOpaqueUnlockPrompt) {
                OpaqueAuthActivity::class.java.name
            } else {
                BiometricAuthActivity::class.java.name
            }
        val component = "${BuildConfig.APPLICATION_ID}/$activityClass"
        val escapedPkg = pkg.replace("'", "")
        val escapedActivity = activity.replace("'", "")

        // Use root `am start` to bypass Android 14-17 Background Activity Launch (BAL) restrictions
        val cmd =
            buildString {
                append("am start --user 0 -n '$component' ")
                append("-f 0x10810000 ")
                append("--ez '${BiometricAuthActivity.EXTRA_ROOT_MODE}' true ")
                append("--ez '${BiometricAuthActivity.EXTRA_ROOT_INSTALLER}' $isInstaller ")
                append("--es '${BiometricAuthActivity.EXTRA_TARGET_PKG}' '$escapedPkg' ")
                append("--ei '${BiometricAuthActivity.EXTRA_TARGET_USER_ID}' $userId ")
                append("--es '${BiometricAuthActivity.EXTRA_TARGET_ACTIVITY}' '$escapedActivity'")
            }

        val res = RootShell.exec(cmd)
        if (res.code != 0) {
            // Fallback to direct context startActivity
            runCatching {
                val intent =
                    Intent().apply {
                        setClassName(BuildConfig.APPLICATION_ID, activityClass)
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION,
                        )
                        putExtra(BiometricAuthActivity.EXTRA_ROOT_MODE, true)
                        putExtra(BiometricAuthActivity.EXTRA_ROOT_INSTALLER, isInstaller)
                        putExtra(BiometricAuthActivity.EXTRA_TARGET_PKG, pkg)
                        putExtra(BiometricAuthActivity.EXTRA_TARGET_USER_ID, userId)
                        putExtra(BiometricAuthActivity.EXTRA_TARGET_ACTIVITY, activity)
                    }
                startActivity(intent)
            }.onFailure {
                Log.w(TAG, "fallback startActivity failed: ${it.message}")
                RootLockState.clearAuthInFlight()
            }
        }
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.root_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.root_channel_description)
                setShowBadge(false)
            }
        nm.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val openIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val lockedCount = RootLockState.lockedPackages.size
        val contentText =
            if (lockedCount == 0) {
                getString(R.string.root_notif_ready)
            } else {
                resources.getQuantityString(R.plurals.status_locked_count, lockedCount, lockedCount)
            }
        return Notification
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.root_notif_title))
            .setContentText(contentText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val TAG = "IngufuriRoot"
        private const val CHANNEL_ID = "ingufuri_root_engine"
        private const val NOTIFICATION_ID = 1037
        private const val ACTION_STOP = "com.ingufuri.applock.action.STOP_ROOT_SERVICE"

        private val SET_RESUMED_REGEX = Regex("""\[(\d+),([^,\]]+),""")
        private val RESUME_ACTIVITY_REGEX = Regex("""\[(\d+),\d+,(\d+),([^,\]]+)]""")
        private val TASK_REMOVED_REGEX = Regex("""\[(\d+),""")

        private val _serviceState = MutableStateFlow<RootServiceState>(RootServiceState.Stopped)
        val serviceState: StateFlow<RootServiceState> = _serviceState.asStateFlow()

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, RootLockService::class.java))
            }.onFailure {
                Log.w(TAG, "failed to start RootLockService: ${it.message}")
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, RootLockService::class.java).apply { action = ACTION_STOP },
                )
            }
        }
    }
}
