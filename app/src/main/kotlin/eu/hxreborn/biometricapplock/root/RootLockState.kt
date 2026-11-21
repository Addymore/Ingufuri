package eu.hxreborn.biometricapplock.root

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.prefs.Prefs
import java.util.concurrent.ConcurrentHashMap

internal const val ROOT_RELOCK_DELAY_NEVER = -1
private const val AWAITING_FOREGROUND_TTL_MS = 10_000L
private const val AUTH_IN_FLIGHT_TTL_MS = 8_000L
private const val SYSTEM_HANDLER_GRANT_TTL_MS = 15_000L

internal data class RootTaskEntry(
    val packageName: String,
    val userId: Int,
)

object RootLockState {
    @Volatile
    var lockedPackages: Set<String> = emptySet()
        private set

    @Volatile
    var globalRelockDelaySeconds: Int = 0
        private set

    @Volatile
    var globalRelockOnScreenOff: Boolean = true
        private set

    @Volatile
    var globalRelockOnTaskRemoved: Boolean = true
        private set

    @Volatile
    var globalBlockScreenshots: Boolean = false
        private set

    @Volatile
    var globalHideNotificationContent: Boolean = false
        private set

    @Volatile
    var globalPreventModuleUninstall: Boolean = false
        private set

    @Volatile
    var globalUseOpaqueUnlockPrompt: Boolean = false
        private set

    @Volatile
    var globalRequireBiometricInstall: Boolean = false
        private set

    @Volatile
    var globalRequireBiometricUninstall: Boolean = false
        private set

    private val unlockedMap = ConcurrentHashMap<String, Long>()
    private val awaitingForeground = ConcurrentHashMap.newKeySet<String>()
    private val taskCache = ConcurrentHashMap<Int, RootTaskEntry>()

    private val appRelockOverrides = ConcurrentHashMap<String, Int>()
    private val appBlockScreenshotsOverrides = ConcurrentHashMap<String, Boolean>()
    private val appListedActivities = ConcurrentHashMap<String, Set<String>>()
    private val appLockListedOnly = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var cachedInstallerPackages: Set<String> = emptySet()

    @Volatile
    private var installerGrantedAt: Long = 0L

    @Volatile
    private var authInFlightKey: String? = null

    @Volatile
    private var authInFlightAt: Long = 0L

    @Volatile
    private var lastForegroundKey: String? = null

    fun packageKey(
        pkg: String,
        userId: Int,
    ): String = "$pkg:$userId"

    fun reloadFromPrefs(
        context: Context,
        prefs: SharedPreferences,
    ) {
        val raw = Prefs.LOCKED_PACKAGES.read(prefs)
        lockedPackages =
            if (raw.isEmpty()) {
                emptySet()
            } else {
                raw.split("|").mapTo(mutableSetOf()) { if (':' in it) it else "$it:0" }
            }
        globalRelockDelaySeconds = Prefs.RELOCK_DELAY_SECONDS.read(prefs)
        globalRelockOnScreenOff = Prefs.RELOCK_ON_SCREEN_OFF.read(prefs)
        globalRelockOnTaskRemoved = Prefs.RELOCK_ON_TASK_REMOVED.read(prefs)
        globalBlockScreenshots = Prefs.BLOCK_SCREENSHOTS.read(prefs)
        globalHideNotificationContent = Prefs.HIDE_NOTIFICATION_CONTENT.read(prefs)
        globalPreventModuleUninstall = Prefs.PREVENT_MODULE_UNINSTALL.read(prefs)
        globalUseOpaqueUnlockPrompt = Prefs.USE_OPAQUE_UNLOCK_PROMPT.read(prefs)
        globalRequireBiometricInstall = Prefs.REQUIRE_BIOMETRIC_INSTALL.read(prefs)
        globalRequireBiometricUninstall = Prefs.REQUIRE_BIOMETRIC_UNINSTALL.read(prefs)

        appRelockOverrides.clear()
        appBlockScreenshotsOverrides.clear()
        appListedActivities.clear()
        appLockListedOnly.clear()

        prefs.all.keys.forEach { key ->
            if (!key.startsWith("app_override:")) return@forEach
            when {
                key.endsWith(":relock_delay_seconds") -> {
                    val pkgKey =
                        key
                            .removePrefix(
                                "app_override:",
                            ).removeSuffix(":relock_delay_seconds")
                    appRelockOverrides[pkgKey] = prefs.getInt(key, 0)
                }

                key.endsWith(":block_screenshots") -> {
                    val pkgKey =
                        key
                            .removePrefix(
                                "app_override:",
                            ).removeSuffix(":block_screenshots")
                    appBlockScreenshotsOverrides[pkgKey] = prefs.getBoolean(key, false)
                }

                key.endsWith(":allowed_activities") -> {
                    val pkgKey =
                        key
                            .removePrefix(
                                "app_override:",
                            ).removeSuffix(":allowed_activities")
                    val activities =
                        prefs
                            .getString(key, "")
                            ?.split('\n')
                            ?.filterTo(mutableSetOf()) { it.isNotBlank() }
                            .orEmpty()
                    if (activities.isNotEmpty()) appListedActivities[pkgKey] = activities
                }

                key.endsWith(":lock_listed_activities") -> {
                    val pkgKey =
                        key.removePrefix("app_override:").removeSuffix(":lock_listed_activities")
                    if (prefs.getBoolean(key, false)) appLockListedOnly += pkgKey
                }
            }
        }
        resolveInstallerPackages(context)
    }

    private fun resolveInstallerPackages(context: Context) {
        val handlers =
            mutableSetOf(
                "com.samsung.android.packageinstaller",
                "com.google.android.packageinstaller",
                "com.android.packageinstaller",
            )
        val pm = context.packageManager
        runCatching {
            val install =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.parse("content://placeholder/x.apk"),
                        "application/vnd.android.package-archive",
                    )
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
            pm
                .queryIntentActivities(install, PackageManager.MATCH_SYSTEM_ONLY)
                .forEach { handlers += it.activityInfo.packageName }
        }
        runCatching {
            val uninstall =
                Intent(Intent.ACTION_DELETE, Uri.fromParts("package", "placeholder", null))
            pm
                .queryIntentActivities(uninstall, PackageManager.MATCH_SYSTEM_ONLY)
                .forEach { handlers += it.activityInfo.packageName }
        }
        handlers -= BuildConfig.APPLICATION_ID
        cachedInstallerPackages = handlers
    }

    fun isInstallerPackage(pkg: String): Boolean = pkg in cachedInstallerPackages

    fun shouldProtectInstaller(): Boolean =
        globalRequireBiometricInstall || globalRequireBiometricUninstall ||
            globalPreventModuleUninstall

    fun isInstallerGrantFresh(): Boolean =
        SystemClock.elapsedRealtime() - installerGrantedAt < SYSTEM_HANDLER_GRANT_TTL_MS

    fun grantInstaller() {
        installerGrantedAt = SystemClock.elapsedRealtime()
    }

    fun getEffectiveRelockDelay(
        pkg: String,
        userId: Int,
    ): Int = appRelockOverrides[packageKey(pkg, userId)] ?: globalRelockDelaySeconds

    fun isUnlocked(
        pkg: String,
        userId: Int,
    ): Boolean {
        val key = packageKey(pkg, userId)
        val ts = unlockedMap[key] ?: return false
        val delay = getEffectiveRelockDelay(pkg, userId)
        if (delay == ROOT_RELOCK_DELAY_NEVER) return true
        if (delay == 0) return true
        return SystemClock.elapsedRealtime() - ts < delay * 1000L
    }

    fun addUnlocked(
        pkg: String,
        userId: Int,
    ) {
        val key = packageKey(pkg, userId)
        unlockedMap[key] = SystemClock.elapsedRealtime()
        awaitingForeground += key
        clearAuthInFlight()
    }

    fun markForegrounded(
        pkg: String,
        userId: Int,
    ) {
        val key = packageKey(pkg, userId)
        awaitingForeground.remove(key)
        lastForegroundKey = key
    }

    fun clearUnlocked() {
        unlockedMap.clear()
        awaitingForeground.clear()
        clearAuthInFlight()
    }

    fun recordTask(
        taskId: Int,
        pkg: String,
        userId: Int,
    ) {
        if (taskId > 0) {
            taskCache[taskId] = RootTaskEntry(pkg, userId)
        }
    }

    fun onTaskRemoved(taskId: Int) {
        if (!globalRelockOnTaskRemoved) return
        val entry = taskCache.remove(taskId) ?: return
        val key = packageKey(entry.packageName, entry.userId)
        unlockedMap.remove(key)
        awaitingForeground.remove(key)
    }

    fun relockOtherPackages(
        keepPkg: String,
        userId: Int,
    ) {
        if (keepPkg == BuildConfig.APPLICATION_ID) return
        val keepKey = packageKey(keepPkg, userId)
        val now = SystemClock.elapsedRealtime()
        unlockedMap.entries.removeIf { (key, ts) ->
            if (key == keepKey) return@removeIf false
            val pkg = key.substringBeforeLast(':')
            val uid = key.substringAfterLast(':').toIntOrNull() ?: 0
            val delay = getEffectiveRelockDelay(pkg, uid)
            if (delay == ROOT_RELOCK_DELAY_NEVER) return@removeIf false
            if (key in awaitingForeground &&
                now - ts < AWAITING_FOREGROUND_TTL_MS
            ) {
                return@removeIf false
            }
            awaitingForeground.remove(key)
            if (delay == 0) true else now - ts >= delay * 1000L
        }
    }

    fun isActivityExempt(
        pkg: String,
        userId: Int,
        className: String?,
    ): Boolean {
        val key = packageKey(pkg, userId)
        val listed = appListedActivities[key] ?: return false
        if (className == null) return false
        val inList = className in listed
        return if (key in appLockListedOnly) !inList else inList
    }

    fun tryMarkAuthInFlight(key: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (authInFlightKey == key && now - authInFlightAt < AUTH_IN_FLIGHT_TTL_MS) {
            return false
        }
        authInFlightKey = key
        authInFlightAt = now
        return true
    }

    fun clearAuthInFlight() {
        authInFlightKey = null
        authInFlightAt = 0L
    }
}
