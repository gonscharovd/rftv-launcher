package com.rftv.launcher

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.pm.PackageInstaller
import android.os.Build
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import rikka.shizuku.Shizuku

/**
 * RFTV Launcher visual prototype.
 * Target device: Android 9 / API 28 / ARMv7 / low-memory Android TV.
 *
 * Glass is simulated with translucent fills and subtle strokes instead of
 * real-time blur to keep rendering affordable on MediaTek MT9632-class hardware.
 */
class MainActivity : Activity() {

    private data class TvApp(
        val label: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var dock: LinearLayout
    private lateinit var dockContent: LinearLayout
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var wallpaperView: ImageView
    private var lastAppliedWallpaperKey: String? = null
    @Volatile private var tvManagerShellService: ITvManagerShellService? = null
    @Volatile private var tvManagerShellUid: Int = -1
    private val tvManagerShizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == TvManagerShizukuBridge.REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    bindTvManagerShizukuService()
                } else {
                    Toast.makeText(this, "Доступ Shizuku не предоставлен", Toast.LENGTH_LONG).show()
                    if (page == "panel" && activePanelTitle == "ADB и разрешения") showTvAdb()
                }
            }
        }
    private val tvManagerShizukuConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val remote = ITvManagerShellService.Stub.asInterface(service)
            tvManagerShellService = remote
            tvManagerShellUid = try { remote?.uid ?: -1 } catch (_: RemoteException) { -1 }
            if (tvManagerShellUid != 0 && tvManagerShellUid != 2000) {
                tvManagerShellService = null
            }
            handler.post {
                if (page == "panel" && activePanelTitle == "ADB и разрешения") showTvAdb()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            tvManagerShellService = null
            tvManagerShellUid = -1
            handler.post {
                if (page == "panel" && activePanelTitle == "ADB и разрешения") showTvAdb()
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val wallpaperExecutor = Executors.newSingleThreadExecutor()
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val tvManagerExecutor = Executors.newSingleThreadExecutor()
    private val tvManagerController by lazy {
        TvManagerPackageController(
            this,
            serviceProvider = { tvManagerShellService },
            serviceUidProvider = { tvManagerShellUid }
        )
    }
    private val packageRefreshTask = Runnable { refreshInstalledAppsAfterPackageChange() }
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val changedPackage = intent?.data?.schemeSpecificPart
            if (!PackageChangePolicy.shouldRefresh(
                    intent?.action,
                    changedPackage,
                    packageName,
                    intent?.getBooleanExtra(Intent.EXTRA_REPLACING, false) == true
                )) return
            handler.removeCallbacks(packageRefreshTask)
            // Package replacement emits multiple broadcasts; coalesce them so
            // the catalog is rebuilt once after PackageManager settles.
            handler.postDelayed(packageRefreshTask, 350L)
        }
    }
    // One failed URL is not retried on every clock tick; retry is user-initiated.
    private val wallpaperLoadingKeys = ConcurrentHashMap.newKeySet<String>()
    private val wallpaperFailedKeys = ConcurrentHashMap.newKeySet<String>()
    private val installedApps = mutableListOf<TvApp>()
    private var dockExpanded = false
    private var page = "home"
    private var activePanelTitle = ""
    private var settingsNavigationActive = false
    private var settingsHubVisible = false
    private var settingsSelectedIndex = 0
    private var packageReceiverRegistered = false
    private var themeMode = 2 // 0 light, 1 dark, 2 automatic
    private var wallpaperLight = 0
    private var wallpaperDark = 0
    private var lightCarousel = false
    private var darkCarousel = false
    private var carouselIntervalMinutes = 5
    private var dockWidthPercent = 88
    private var dockHeightDp = 154
    private var screensaverTimeoutMinutes = 15
    private val idleScreensaverTask = object : Runnable {
        override fun run() {
            if (page == "home" && screensaverTimeoutMinutes > 0 && root.findViewWithTag<View>("rftv-control-center") == null) showScreensaver()
        }
    }
    private var otaStatus = "Обновления ещё не проверялись"
    private var otaProgress = 0
    private var otaInProgress = false
    private val favoritePackages = mutableListOf<String>()
    private val hiddenPackages = mutableSetOf<String>()


    private data class WallpaperPhoto(val title: String, val author: String, val pageUrl: String, val imageUrl: String)

    // Unsplash photographs are cached in app-private storage on first use.
    private val lightPhotos = listOf(
        WallpaperPhoto("Emerald Lake at sunrise", "Philipp", "https://unsplash.com/photos/a-large-body-of-water-surrounded-by-mountains-OvfI1PiuTpE", "https://images.unsplash.com/photo-1724169913051-49f6ff76a070?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Calm alpine lake", "Shai Pal", "https://unsplash.com/photos/calm-lake-between-mountains-at-sunrise-WntO4gmkp7E", "https://images.unsplash.com/photo-1777573087870-2cf5cab4421d?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Misty forest valley", "Roberto Shumski", "https://unsplash.com/photos/misty-forest-valley-with-mountains-in-background-oYEGPZebzGw", "https://images.unsplash.com/photo-1765871319901-0aaafe3f1a2a?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Japanese Alps river", "Joris Beugels", "https://unsplash.com/photos/scenic-landscape-with-a-lake-and-a-mountain-qy5VLB3bkjE", "https://images.unsplash.com/photo-1754390032251-953abfedc34c?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Moraine Lake", "Brandon Jean", "https://unsplash.com/photos/lake-mountains-and-trees-w79UXGGlvhQ", "https://images.unsplash.com/photo-1533579436102-fcc5a403c554?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Cadillac Mountain sunrise", "Eugene Krasnaok", "https://unsplash.com/photos/a-scenic-view-of-a-lake-and-mountains-in-the-distance-M4JnO3-hhIQ", "https://images.unsplash.com/photo-1706733839515-c3e809d6b1ed?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Peyto Lake", "Mario Häfliger", "https://unsplash.com/photos/sunlight-shines-over-a-mountain-lake-at-sunrise-Svnrlh3lXZ0", "https://images.unsplash.com/photo-1660162129606-c12ece87e967?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Forest lake morning", "Shai Pal", "https://unsplash.com/photos/sunrise-over-a-tranquil-lake-with-forested-mountains-6CHmP0msPDw", "https://images.unsplash.com/photo-1777573087858-cd1fe80db56a?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Misty green hills", "Andrea Caramello", "https://unsplash.com/photos/misty-forest-landscape-with-rolling-green-hills-ZqlVkilbzhg", "https://images.unsplash.com/photo-1778004494946-ce539160c979?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Golden mist forest", "Fabrice Villard", "https://unsplash.com/photos/misty-forest-landscape-with-soft-golden-light-tkoA1sWwHUg", "https://images.unsplash.com/photo-1768808520785-a4ce8c12c335?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080")
    )

    private val darkPhotos = listOf(
        WallpaperPhoto("Snowy mountain night", "Florian Schindler", "https://unsplash.com/photos/starry-night-sky-over-a-snowy-mountain-landscape-I3VWe-dSHKY", "https://images.unsplash.com/photo-1768590238594-625fe0d913aa?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Dolomites under stars", "Marek Piwnicki", "https://unsplash.com/photos/snowy-mountains-under-a-starry-night-sky-SumjjLhysZM", "https://images.unsplash.com/photo-1765476608471-c30c09564def?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Aurora over a lake", "Niklas Jonasson", "https://unsplash.com/photos/green-aurora-borealis-over-a-calm-lake-at-night-ZPbfvIN4NXs", "https://images.unsplash.com/photo-1768813282031-2aec62eee8b7?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Northern lights reflection", "Scott Goodwill", "https://unsplash.com/photos/aurora-borealis-over-a-calm-lake-at-night-aZ5EiV2-afc", "https://images.unsplash.com/photo-1775663880435-8edb7efe3ecf?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Aurora over dark hills", "Lucas Gallone", "https://unsplash.com/photos/aurora-borealis-glows-over-a-silhouetted-hillside-DzYVUP8cIKw", "https://images.unsplash.com/photo-1769038856791-dab2060b9497?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Aurora over Norwegian fjord", "Angela Compagnone", "https://unsplash.com/photos/aurora-borealis-above-mountain-and-body-of-water-l9cneQNE03Y", "https://images.unsplash.com/photo-1550656722-8099c82ab00c?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Aurora over snowy forest", "Wolfgang Hasselmann", "https://unsplash.com/photos/aurora-borealis-over-snow-covered-trees-at-night-tlBWf1BZLzs", "https://images.unsplash.com/photo-1772136440778-a51aee48088c?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Northern lights", "Mats-Peter Forss", "https://unsplash.com/photos/aurora-borealis-m6hpb2rWP7Q", "https://images.unsplash.com/photo-1426840963626-ffdf2d7ef80b?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Iceland aurora", "Anton Nikolov", "https://unsplash.com/photos/aurora-borealis-on-night-sky-JKeIu3jjJFw", "https://images.unsplash.com/photo-1460751398360-a8d6be9b8208?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"),
        WallpaperPhoto("Green and purple aurora", "Troy Olson", "https://unsplash.com/photos/a-night-sky-filled-with-stars-and-a-green-and-purple-aurora-tNNVfyZ_VbQ", "https://images.unsplash.com/photo-1728279320747-e29867b7e455?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080")
    )

    // Fallback palettes remain available while the first photograph is being cached.
    private val lightWallpapers = listOf(
        intArrayOf(Color.rgb(104,184,215), Color.rgb(60,145,166), Color.rgb(237,204,151)),
        intArrayOf(Color.rgb(110,177,211), Color.rgb(126,190,157), Color.rgb(242,219,165)),
        intArrayOf(Color.rgb(194,213,226), Color.rgb(95,164,195), Color.rgb(238,198,153)),
        intArrayOf(Color.rgb(247,190,132), Color.rgb(221,132,111), Color.rgb(106,133,165)),
        intArrayOf(Color.rgb(158,205,220), Color.rgb(103,169,183), Color.rgb(214,224,193)),
        intArrayOf(Color.rgb(229,211,177), Color.rgb(172,188,169), Color.rgb(94,143,157)),
        intArrayOf(Color.rgb(185,215,235), Color.rgb(116,169,204), Color.rgb(231,222,196)),
        intArrayOf(Color.rgb(242,204,168), Color.rgb(205,158,145), Color.rgb(125,150,164)),
        intArrayOf(Color.rgb(177,216,192), Color.rgb(102,171,153), Color.rgb(231,219,177)),
        intArrayOf(Color.rgb(216,222,228), Color.rgb(147,183,202), Color.rgb(233,205,166))
    )

    private val darkWallpapers = listOf(
        intArrayOf(Color.rgb(8,20,48), Color.rgb(34,54,94), Color.rgb(111,78,119)),
        intArrayOf(Color.rgb(9,25,38), Color.rgb(24,67,82), Color.rgb(73,94,116)),
        intArrayOf(Color.rgb(24,23,48), Color.rgb(57,48,92), Color.rgb(135,74,103)),
        intArrayOf(Color.rgb(8,18,34), Color.rgb(27,52,77), Color.rgb(77,92,117)),
        intArrayOf(Color.rgb(24,19,42), Color.rgb(62,42,79), Color.rgb(124,67,94)),
        intArrayOf(Color.rgb(5,29,39), Color.rgb(15,62,72), Color.rgb(48,92,99)),
        intArrayOf(Color.rgb(17,24,57), Color.rgb(39,53,104), Color.rgb(87,83,132)),
        intArrayOf(Color.rgb(34,24,28), Color.rgb(79,43,52), Color.rgb(139,77,78)),
        intArrayOf(Color.rgb(10,30,27), Color.rgb(26,65,54), Color.rgb(76,104,79)),
        intArrayOf(Color.rgb(19,27,44), Color.rgb(47,65,88), Color.rgb(101,105,122))
    )

    private val carouselTask = object : Runnable {
        override fun run() {
            if (page == "home") {
                if (isDarkTheme() && darkCarousel) {
                    wallpaperDark = (wallpaperDark + 1) % darkWallpapers.size
                    prefs.edit().putInt("wallpaper_dark", wallpaperDark).apply()
                    applyWallpaper()
                } else if (!isDarkTheme() && lightCarousel) {
                    wallpaperLight = (wallpaperLight + 1) % lightWallpapers.size
                    prefs.edit().putInt("wallpaper_light", wallpaperLight).apply()
                    applyWallpaper()
                }
            }
            handler.postDelayed(this, carouselIntervalMinutes * 60_000L)
        }
    }

    private val clockTask = object : Runnable {
        override fun run() {
            if (page == "home" && ::clockText.isInitialized) {
                val now = Date()
                clockText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                dateText.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
                    .format(now).replaceFirstChar { it.uppercase() }
                // Automatic day/night mode is checked here, but unchanged wallpaper
                // selections do not recreate drawables or touch the image pipeline.
                applyWallpaper()
            }
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(tvManagerShizukuPermissionListener)
        prefs = getSharedPreferences("rftv_preferences", MODE_PRIVATE)
        themeMode = prefs.getInt("theme_mode", 2)
        wallpaperLight = prefs.getInt("wallpaper_light", 0)
        wallpaperDark = prefs.getInt("wallpaper_dark", 0)
        lightCarousel = prefs.getBoolean("wallpaper_light_carousel", false)
        darkCarousel = prefs.getBoolean("wallpaper_dark_carousel", false)
        carouselIntervalMinutes = prefs.getInt("wallpaper_interval", 5).coerceIn(1, 60)
        dockWidthPercent = prefs.getInt("dock_width", 88)
        dockHeightDp = prefs.getInt("dock_height", 154)
        screensaverTimeoutMinutes = prefs.getInt("screensaver_timeout_minutes", 15).coerceIn(0, 60)
        favoritePackages.addAll(prefs.getString("favorite_packages_order", "")!!.split("|").filter { it.isNotBlank() })
        hiddenPackages.addAll(prefs.getStringSet("hidden_packages", emptySet()) ?: emptySet())

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        loadInstalledApps()
        showHome()
        registerPackageChangeReceiver()
    }

    private fun registerPackageChangeReceiver() {
        if (packageReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction("android.intent.action.PACKAGE_FULLY_REMOVED")
            addDataScheme("package")
        }
        registerReceiver(packageChangeReceiver, filter)
        packageReceiverRegistered = true
    }

    private fun refreshInstalledAppsAfterPackageChange() {
        val focusedView = currentFocus
        val previouslyFocusedPackage = focusedView?.tag as? String
        val wasHome = page == "home"
        val focusWasInDock = wasHome && focusedView != null &&
            ::dockContent.isInitialized && isDescendantOf(focusedView, dockContent)
        loadInstalledApps()
        if (wasHome && ::dockContent.isInitialized) {
            buildDockContent()
            if (focusWasInDock) {
                dockContent.post {
                    val target = previouslyFocusedPackage?.let { dockContent.findViewWithTag<View>(it) }
                    if (target?.requestFocus() != true) focusFirstApp()
                }
            }
        } else if (page == "panel" && activePanelTitle == "Каталог приложений") {
            showAppCatalog()
        } else if (page == "panel" && activePanelTitle == "Скрытые приложения") {
            showHiddenAppsPage()
        }
    }

    override fun onResume() {
        super.onResume()
        if (page == "home") { startClock(); resetIdleScreensaverTimer() }
        if (prefs.getBoolean("ota_install_pending_permission", false)) {
            val apk = pendingOtaApk()
            if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) {
                prefs.edit().remove("ota_install_pending_permission").apply()
                handler.post {
                    if (apk.isFile && apk.length() > 100_000L) {
                        otaStatus = "Разрешение получено · продолжаем установку"
                        installOta(apk)
                    } else {
                        otaStatus = "Проверенный APK не найден. Запусти загрузку обновления повторно."
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockTask)
        handler.removeCallbacks(carouselTask)
        handler.removeCallbacks(idleScreensaverTask)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Shizuku.removeRequestPermissionResultListener(tvManagerShizukuPermissionListener)
        TvManagerShizukuBridge.unbind(this, tvManagerShizukuConnection)
        tvManagerShellService = null
        if (packageReceiverRegistered) {
            try { unregisterReceiver(packageChangeReceiver) } catch (_: IllegalArgumentException) { }
            packageReceiverRegistered = false
        }
        wallpaperExecutor.shutdownNow()
        networkExecutor.shutdownNow()
        tvManagerExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetIdleScreensaverTimer()
    }

    private fun resetIdleScreensaverTimer() {
        handler.removeCallbacks(idleScreensaverTask)
        if (screensaverTimeoutMinutes > 0 && page == "home" && !isFinishing) handler.postDelayed(idleScreensaverTask, screensaverTimeoutMinutes * 60_000L)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (page == "home") {
                    val controlCenter = root.findViewWithTag<View>("rftv-control-center")
                    if (controlCenter != null) closeControlCenter()
                    // A launcher should remain on its home screen instead of finishing.
                    return true
                }
                if (page == "panel") {
                    if (settingsNavigationActive && !settingsHubVisible) showLauncherSettings()
                    else showHome()
                    return true
                }
            }

            val direction = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
                KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
                else -> View.NO_ID
            }

            if (direction != View.NO_ID) {
                if (page == "home") {
                    val controlCenter = root.findViewWithTag<View>("rftv-control-center")
                    if (controlCenter != null) {
                        moveFocusWithin(controlCenter, direction)
                        return true
                    }
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> if (!dockExpanded) {
                            setDockExpanded(true)
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> if (dockExpanded) {
                            setDockExpanded(false)
                            return true
                        }
                    }
                } else if (page == "panel") {
                    moveFocusWithin(root, direction)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun moveFocusWithin(container: View, direction: Int) {
        val focused = currentFocus
        if (focused == null || !isDescendantOf(focused, container)) {
            requestFirstFocusable(container)
            return
        }
        val next = focused.focusSearch(direction)
        if (next != null && isDescendantOf(next, container)) next.requestFocus()
        // Consume boundary movement so focus cannot escape the active surface.
    }

    private fun isDescendantOf(view: View, container: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === container) return true
            current = current.parent as? View
        }
        return false
    }

    private fun requestFirstFocusable(container: View): Boolean {
        if (container.visibility != View.VISIBLE || !container.isEnabled) return false
        if (container.isFocusable && container.requestFocus()) return true
        if (container is ViewGroup) {
            for (index in 0 until container.childCount) {
                if (requestFirstFocusable(container.getChildAt(index))) return true
            }
        }
        return false
    }

    private fun loadInstalledApps() {
        installedApps.clear()
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName }
            .mapNotNull { info ->
                val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(info.packageName)
                    ?: packageManager.getLaunchIntentForPackage(info.packageName)
                    ?: return@mapNotNull null
                TvApp(
                    packageManager.getApplicationLabel(info).toString(),
                    info.packageName,
                    packageManager.getApplicationIcon(info)
                )
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .forEach { installedApps.add(it) }
        val available = installedApps.map { it.packageName }.toSet()
        favoritePackages.removeAll { it !in available || it in hiddenPackages }
        hiddenPackages.retainAll(available)
        if (AppCatalogPolicy.shouldSeedDefaultFavorites(
                favoritesEmpty = favoritePackages.isEmpty(),
                initialized = prefs.getBoolean("favorites_initialized", false)
            )) {
            installedApps.filter { it.packageName !in hiddenPackages }.take(7)
                .forEach { favoritePackages.add(it.packageName) }
        }
        persistAppLists()
    }

    private fun isDarkTheme(): Boolean {
        if (themeMode == 1) return true
        if (themeMode == 0) return false
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return hour >= 19 || hour < 7
    }

    private fun currentWallpaper(): IntArray {
        val list = if (isDarkTheme()) darkWallpapers else lightWallpapers
        val index = if (isDarkTheme()) wallpaperDark else wallpaperLight
        return list[index % list.size]
    }

    private fun applyWallpaper() {
        if (!::wallpaperView.isInitialized) return
        val dark = isDarkTheme()
        val palette = if (dark) darkWallpapers else lightWallpapers
        val index = (if (dark) wallpaperDark else wallpaperLight).coerceIn(0, palette.lastIndex)
        val selectionKey = WallpaperImageUrls.selectionKey(dark, index)
        if (selectionKey == lastAppliedWallpaperKey) return

        lastAppliedWallpaperKey = selectionKey
        val colors = palette[index]
        wallpaperView.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(colors[0], colors[1], colors[2])
        )
        loadPhoto(index, dark, wallpaperView)
    }

    private fun loadPhoto(index: Int, dark: Boolean, target: ImageView, thumbnail: Boolean = false) {
        val photos = if (dark) darkPhotos else lightPhotos
        val safeIndex = index.coerceIn(0, photos.lastIndex)
        val photo = photos[safeIndex]
        val tag = WallpaperImageUrls.cacheKey(dark, safeIndex, thumbnail)
        if (target.tag == tag && target.drawable != null) return
        if (target.tag != tag) target.setImageDrawable(null)
        target.tag = tag

        // Do not enqueue the same failed request every 30 seconds from clockTask.
        if (tag in wallpaperFailedKeys) return
        val requestKey = "$tag@${System.identityHashCode(target)}"
        if (!wallpaperLoadingKeys.add(requestKey)) return

        val directory = File(filesDir, "wallpapers").apply { mkdirs() }
        // Picker previews use their own small cache and never downgrade the
        // full-resolution wallpaper kept for the home screen.
        val file = File(directory, WallpaperImageUrls.cacheFileName(dark, safeIndex, thumbnail))
        val imageUrl = if (thumbnail) WallpaperImageUrls.thumbnailUrl(photo.imageUrl) else photo.imageUrl

        fun decode(): Bitmap? {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = if (thumbnail) 4 else 1
            }
            return BitmapFactory.decodeFile(file.absolutePath, options)
        }

        fun finish(bitmap: Bitmap?) {
            handler.post {
                wallpaperLoadingKeys.remove(requestKey)
                if (target.tag != tag) {
                    bitmap?.recycle()
                    return@post
                }
                if (bitmap != null) {
                    wallpaperFailedKeys.remove(tag)
                    if (!thumbnail && target === wallpaperView) {
                        target.animate().cancel()
                        target.alpha = 0f
                        target.setImageBitmap(bitmap)
                        target.animate().alpha(1f)
                            .setDuration(RftvDesignSystem.Motion.HOME_FADE_DURATION_MS)
                            .start()
                    } else {
                        target.setImageBitmap(bitmap)
                    }
                    target.contentDescription = "${photo.title} — ${photo.author}"
                } else {
                    wallpaperFailedKeys.add(tag)
                    target.contentDescription = "Не удалось загрузить обои: ${photo.title}"
                    if (target === wallpaperView && page == "home") {
                        Toast.makeText(this, "Не удалось загрузить фотографию. Удерживай кнопку «Обои» для повтора.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        wallpaperExecutor.execute {
            var connection: HttpURLConnection? = null
            var temp: File? = null
            try {
                if (file.exists() && file.length() > 10_000L) {
                    val cached = decode()
                    if (cached != null) {
                        finish(cached)
                        return@execute
                    }
                    // A truncated/corrupt cache must not permanently block this wallpaper.
                    file.delete()
                }

                connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 25000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "RFTV-Launcher/0.1")
                }
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("HTTP ${connection.responseCode}")
                }
                val tempFile = File(directory, file.name + ".part")
                temp = tempFile
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 24L * 1024L * 1024L) {
                                throw IllegalStateException("Изображение превышает 24 МБ")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (tempFile.length() <= 10_000L) throw IllegalStateException("Пустой или неполный файл")
                if (file.exists()) file.delete()
                if (!tempFile.renameTo(file)) throw IllegalStateException("Не удалось сохранить обои в кеш")
                val downloaded = decode() ?: throw IllegalStateException("Файл не является читаемым изображением")
                finish(downloaded)
            } catch (_: Exception) {
                temp?.delete()
                finish(null)
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun retryCurrentWallpaper() {
        if (!::wallpaperView.isInitialized || page != "home") return
        val dark = isDarkTheme()
        val index = if (dark) wallpaperDark else wallpaperLight
        val key = "${if (dark) "night" else "day"}-${index.coerceIn(0, 9)}"
        wallpaperFailedKeys.remove(key)
        wallpaperView.tag = null
        wallpaperView.setImageDrawable(null)
        lastAppliedWallpaperKey = null
        Toast.makeText(this, "Повторная загрузка обоев…", Toast.LENGTH_SHORT).show()
        applyWallpaper()
    }

    private fun showHome() {
        settingsNavigationActive = false
        settingsHubVisible = false
        settingsSelectedIndex = 0
        page = "home"
        activePanelTitle = ""
        dockExpanded = false
        root = FrameLayout(this).apply {
            setBackgroundColor(RftvDesignSystem.ColorTokens.HOME_BACKDROP)
            clipChildren = true
            clipToPadding = true
            isFocusableInTouchMode = true
        }

        wallpaperView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(RftvDesignSystem.ColorTokens.WALLPAPER_FALLBACK)
            contentDescription = "Фотографические обои RFTV"
        }
        lastAppliedWallpaperKey = null
        root.addView(wallpaperView, FrameLayout.LayoutParams(-1, -1))
        applyWallpaper()

        val overlay = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(RftvDesignSystem.ColorTokens.OVERLAY_TOP, RftvDesignSystem.ColorTokens.OVERLAY_BOTTOM)
            )
        }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP or Gravity.CENTER_VERTICAL
            setPadding(dp(RftvDesignSystem.Spacing.XXL), dp(RftvDesignSystem.Spacing.HOME_TOP_INSET), dp(RftvDesignSystem.Spacing.XXL), 0)
        }

        val timeColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        clockText = TextView(this).apply {
            text = "09:41"
            letterSpacing = RftvDesignSystem.Size.CLOCK_LETTER_SPACING
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        dateText = TextView(this).apply {
            setTextColor(Color.argb(RftvDesignSystem.GlassAlpha.HOME_DATE_TEXT, 255, 255, 255))
            setPadding(dp(RftvDesignSystem.Size.HOME_DATE_LEFT_PADDING), 0, 0, 0)
        }
        timeColumn.addView(clockText)
        timeColumn.addView(dateText)
        timeColumn.gravity = Gravity.CENTER
        clockText.textSize = RftvDesignSystem.Size.CLOCK_TEXT_SP
        clockText.setShadowLayer(RftvDesignSystem.Size.CLOCK_SHADOW_RADIUS, 0f, RftvDesignSystem.Size.CLOCK_SHADOW_DY, RftvDesignSystem.ColorTokens.CLOCK_SHADOW)
        dateText.textSize = RftvDesignSystem.Size.DATE_TEXT_SP
        dateText.setShadowLayer(RftvDesignSystem.Size.DATE_SHADOW_RADIUS, 0f, RftvDesignSystem.Size.DATE_SHADOW_DY, Color.argb(RftvDesignSystem.GlassAlpha.HOME_DATE_SHADOW, 0, 0, 0))
        root.addView(timeColumn, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val weather = glassButton("☀   ПОГОДА") { showWeather() }
        top.addView(weather, LinearLayout.LayoutParams(-2, dp(RftvDesignSystem.Size.TOP_ACTION_HEIGHT)).apply {
            rightMargin = dp(RftvDesignSystem.Spacing.HOME_ACTION_GAP)
        })
        top.addView(glassButton("⚙") { showControlCenter() }.apply {
            tag = "rftv-control-center-trigger"
        }, LinearLayout.LayoutParams(dp(RftvDesignSystem.Size.TOP_SETTINGS_WIDTH), dp(RftvDesignSystem.Size.TOP_ACTION_HEIGHT)))
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = RftvDesignSystem.dockSurface(this@MainActivity)
            setPadding(dp(RftvDesignSystem.Spacing.SM), dp(RftvDesignSystem.Spacing.DOCK_CONTENT_PADDING), dp(RftvDesignSystem.Spacing.SM), dp(RftvDesignSystem.Spacing.DOCK_CONTENT_PADDING))
            clipChildren = true
            clipToPadding = true
            elevation = dp(RftvDesignSystem.Elevation.DOCK_DP).toFloat()
        }
        dockContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        buildDockContent()
        dock.addView(dockContent, LinearLayout.LayoutParams(-1, -2))

        val dockParams = FrameLayout.LayoutParams(
            -1,
            dp(dockHeightDp),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            leftMargin = dp(RftvDesignSystem.Spacing.HOME_DOCK_HORIZONTAL_INSET)
            rightMargin = dp(RftvDesignSystem.Spacing.HOME_DOCK_HORIZONTAL_INSET)
            bottomMargin = dp(RftvDesignSystem.Spacing.DOCK_BOTTOM_INSET)
            width = (resources.displayMetrics.widthPixels * dockWidthPercent / 100f).toInt()
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        root.addView(dock, dockParams)

        val wallpaperButton = glassButton("Обои  ◀   ▶") { nextWallpaper() }.apply {
            tag = "rftv-wallpaper-button"
            setOnLongClickListener {
                retryCurrentWallpaper()
                true
            }
        }
        val wallpaperParams = FrameLayout.LayoutParams(-2, dp(RftvDesignSystem.Size.HOME_WALLPAPER_BUTTON_HEIGHT), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(RftvDesignSystem.Spacing.HOME_WALLPAPER_RIGHT_INSET)
            bottomMargin = dp(dockHeightDp + RftvDesignSystem.Spacing.HOME_WALLPAPER_DOCK_GAP)
        }
        root.addView(wallpaperButton, wallpaperParams)

        setContentView(root)
        startClock()
        focusFirstApp()
    }

    private fun startClock() {
        handler.removeCallbacks(clockTask)
        handler.removeCallbacks(carouselTask)
        clockTask.run()
        handler.postDelayed(carouselTask, carouselIntervalMinutes * 60_000L)
    }

    private fun buildDockContent() {
        dockContent.removeAllViews()
        val visibleApps = installedApps.filter { it.packageName !in hiddenPackages }
        val favorites = visibleApps.filter { it.packageName in favoritePackages }
        // If the user removes every favorite, keep the launcher usable by
        // showing the complete launchable app list in the first Dock row.
        val firstRow = favorites.ifEmpty { visibleApps }
        dockContent.addView(buildAppRow(firstRow), LinearLayout.LayoutParams(-1, dp(RftvDesignSystem.Size.DOCK_TILE_HEIGHT)))
        if (dockExpanded) {
            val firstRowPackages = firstRow.map { it.packageName }.toSet()
            val allApps = visibleApps.filter { it.packageName !in firstRowPackages }
            dockContent.addView(buildAppRow(allApps), LinearLayout.LayoutParams(-1, dp(RftvDesignSystem.Size.DOCK_TILE_HEIGHT)))
        }
        if (firstRow.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Приложения появятся здесь после установки"
                textSize = RftvDesignSystem.Size.EMPTY_DOCK_TEXT_SP
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }
            dockContent.addView(empty, LinearLayout.LayoutParams(-1, dp(RftvDesignSystem.Size.EMPTY_DOCK_HEIGHT)))
        }
    }

    private fun buildAppRow(items: List<TvApp>): HorizontalScrollView {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        items.forEach { app ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                tag = app.packageName
                isFocusable = true
                isFocusableInTouchMode = true
                background = RftvDesignSystem.focusSurface(this@MainActivity, false, RftvDesignSystem.Radius.DOCK_TILE, restingAlpha = RftvDesignSystem.GlassAlpha.DOCK_TILE_RESTING, focusedAlpha = RftvDesignSystem.GlassAlpha.DOCK_TILE_FOCUSED)
                setPadding(dp(RftvDesignSystem.Size.DOCK_TILE_PADDING_HORIZONTAL), dp(RftvDesignSystem.Size.DOCK_TILE_PADDING_TOP), dp(RftvDesignSystem.Size.DOCK_TILE_PADDING_HORIZONTAL), dp(RftvDesignSystem.Size.DOCK_TILE_PADDING_BOTTOM))
                contentDescription = app.label
                setOnFocusChangeListener { view, focused ->
                    view.background = RftvDesignSystem.focusSurface(
                        this@MainActivity, focused, RftvDesignSystem.Radius.DOCK_TILE, restingAlpha = RftvDesignSystem.GlassAlpha.DOCK_TILE_RESTING, focusedAlpha = RftvDesignSystem.GlassAlpha.DOCK_TILE_FOCUSED
                    )
                    RftvDesignSystem.applyFocusScale(view, focused, RftvDesignSystem.Size.DOCK_FOCUS_SCALE)
                    view.elevation = if (focused) dp(RftvDesignSystem.Elevation.FOCUSED_TILE_DP).toFloat() else 0f
                }
                setOnClickListener { launchApp(app.packageName) }
                setOnLongClickListener { showAppActions(app); true }
            }
            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            tile.addView(icon, LinearLayout.LayoutParams(dp(RftvDesignSystem.Size.DOCK_ICON), dp(RftvDesignSystem.Size.DOCK_ICON)))
            val label = TextView(this).apply {
                text = app.label
                textSize = RftvDesignSystem.Size.DOCK_LABEL_TEXT_SP
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, dp(RftvDesignSystem.Size.DOCK_LABEL_TOP_PADDING), 0, 0)
            }
            tile.addView(label, LinearLayout.LayoutParams(dp(RftvDesignSystem.Size.DOCK_LABEL_WIDTH), dp(RftvDesignSystem.Size.DOCK_LABEL_HEIGHT)))
            row.addView(tile, LinearLayout.LayoutParams(dp(RftvDesignSystem.Size.DOCK_TILE_WIDTH), dp(RftvDesignSystem.Size.DOCK_TILE_HEIGHT)).apply {
                leftMargin = dp(RftvDesignSystem.Spacing.DOCK_TILE_HORIZONTAL_MARGIN)
                rightMargin = dp(RftvDesignSystem.Spacing.DOCK_TILE_HORIZONTAL_MARGIN)
            })
        }
        scroll.addView(row)
        return scroll
    }

    private fun focusFirstApp() {
        if (!::dockContent.isInitialized) return
        val rowScroll = dockContent.getChildAt(0) as? HorizontalScrollView ?: return
        val row = rowScroll.getChildAt(0) as? LinearLayout ?: return
        row.getChildAt(0)?.requestFocus()
    }

    private fun setDockExpanded(expanded: Boolean) {
        if (!::dock.isInitialized || dockExpanded == expanded) return
        val previouslyFocusedPackage = currentFocus?.tag as? String
        dockExpanded = expanded
        val params = dock.layoutParams as FrameLayout.LayoutParams
        val expandedHeight = if (expanded) max(270, dockHeightDp + 112) else dockHeightDp
        params.height = dp(expandedHeight)
        dock.layoutParams = params
        updateWallpaperButtonPosition(expandedHeight)
        buildDockContent()
        dock.animate().alpha(1f).setDuration(RftvDesignSystem.Motion.DOCK_REVEAL_DURATION_MS).start()
        if (expanded) {
            val second = dockContent.getChildAt(1)
            second?.alpha = 0f
            second?.animate()?.alpha(1f)?.setDuration(RftvDesignSystem.Motion.SECONDARY_REVEAL_DURATION_MS)?.start()
        }
        // Rebuilding rows removes the previously focused View. Restore focus
        // explicitly so DPAD navigation never leaves the remote without a target.
        dockContent.post {
            val target = if (expanded) {
                val secondRow = dockContent.getChildAt(1) as? HorizontalScrollView
                val secondItems = secondRow?.getChildAt(0) as? LinearLayout
                secondItems?.getChildAt(0)
                    ?: (dockContent.getChildAt(0) as? HorizontalScrollView)
                        ?.let { it.getChildAt(0) as? LinearLayout }
                        ?.getChildAt(0)
            } else {
                val firstRow = (dockContent.getChildAt(0) as? HorizontalScrollView)
                    ?.getChildAt(0) as? LinearLayout
                previouslyFocusedPackage?.let { dockContent.findViewWithTag<View>(it) }
                    ?: firstRow?.getChildAt(0)
            }
            if (target?.requestFocus() != true) focusFirstApp()
        }
    }

    private fun nextWallpaper() {
        if (isDarkTheme()) {
            wallpaperDark = (wallpaperDark + 1) % darkWallpapers.size
            prefs.edit().putInt("wallpaper_dark", wallpaperDark).apply()
        } else {
            wallpaperLight = (wallpaperLight + 1) % lightWallpapers.size
            prefs.edit().putInt("wallpaper_light", wallpaperLight).apply()
        }
        applyWallpaper()
    }

    private fun readHttpText(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "RFTV-Launcher")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun showWeather() {
        showPanel("Погода") { body ->
            val liveWeather = TextView(this).apply {
                text = "Загружаем текущую погоду для Челябинска…"
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(dp(18), dp(16), dp(18), dp(16))
                background = RftvDesignSystem.cardSurface(this@MainActivity)
            }
            body.addView(liveWeather, LinearLayout.LayoutParams(-1, -2))
            networkExecutor.execute {
                val result = try {
                    val address = "https://api.open-meteo.com/v1/forecast?latitude=55.1599&longitude=61.4026&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&timezone=Asia%2FYekaterinburg"
                    val current = org.json.JSONObject(readHttpText(address))
                        .getJSONObject("current")
                    val temp = current.getDouble("temperature_2m").toInt()
                    val feels = current.getDouble("apparent_temperature").toInt()
                    val humidity = current.getInt("relative_humidity_2m")
                    val wind = current.getDouble("wind_speed_10m").toInt()
                    val code = current.getInt("weather_code")
                    val condition = when (code) {
                        0 -> "Ясно"
                        1, 2 -> "Переменная облачность"
                        3 -> "Облачно"
                        45, 48 -> "Туман"
                        in 51..67 -> "Морось или дождь"
                        in 71..77 -> "Снег"
                        in 80..82 -> "Ливень"
                        in 85..86 -> "Снегопад"
                        95, 96, 99 -> "Гроза"
                        else -> "Погодные условия"
                    }
                    "Челябинск · $condition\n\n$temp°C  ·  ощущается как $feels°C\nВлажность $humidity%  ·  ветер $wind км/ч\n\nИсточник: Open-Meteo"
                } catch (_: Exception) {
                    "Не удалось загрузить погоду. Проверь подключение к интернету."
                }
                handler.post {
                    if (!isFinishing && page == "panel" && activePanelTitle == "Погода" && liveWeather.parent != null) {
                        liveWeather.text = result
                    }
                }
            }
            body.addView(infoCard("КУРСЫ ВАЛЮТ", "Банк России",
                "На экране ожидания показываются курсы доллара и евро к рублю из официального источника ЦБ РФ."))
            body.addView(infoCard("НОВОСТИ", "Россия и мир",
                "На экране ожидания выводятся заголовки RSS-лент. Источники указываются внизу экрана."))
            body.addView(glassButton("Настроить экран ожидания") {
                openSettings("android.settings.DREAM_SETTINGS")
            }, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(12) })
            body.addView(glassButton("Закрыть") { showHome() }, LinearLayout.LayoutParams(-2, dp(48)).apply {
                topMargin = dp(8)
            })
        }
    }


    private fun persistAppLists() {
        prefs.edit()
            .putString("favorite_packages_order", favoritePackages.joinToString("|"))
            .putStringSet("hidden_packages", hiddenPackages.toSet())
            .putBoolean("favorites_initialized", true)
            .apply()
    }

    private fun showAppActions(app: TvApp) {
        val returnToPanel = page == "panel"
        val previouslyFocusedPackage = currentFocus?.tag as? String
        val isFavorite = app.packageName in favoritePackages
        val actions = if (isFavorite) {
            arrayOf("Переместить левее", "Переместить правее", "Убрать из избранного", "Скрыть приложение")
        } else {
            arrayOf("Добавить в избранное", "Скрыть приложение")
        }
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(actions) { _, which ->
                if (isFavorite) {
                    val position = favoritePackages.indexOf(app.packageName)
                    when (which) {
                        0 -> if (position > 0) {
                            favoritePackages.removeAt(position)
                            favoritePackages.add(position - 1, app.packageName)
                        }
                        1 -> if (position >= 0 && position < favoritePackages.lastIndex) {
                            favoritePackages.removeAt(position)
                            favoritePackages.add(position + 1, app.packageName)
                        }
                        2 -> favoritePackages.remove(app.packageName)
                        3 -> {
                            favoritePackages.remove(app.packageName)
                            hiddenPackages.add(app.packageName)
                        }
                    }
                } else {
                    when (which) {
                        0 -> favoritePackages.add(app.packageName)
                        1 -> hiddenPackages.add(app.packageName)
                    }
                }
                persistAppLists()
                if (returnToPanel) {
                    // Refresh the catalog so hidden/favorite state is immediately visible.
                    showAppCatalog()
                } else {
                    buildDockContent()
                    dockContent.post {
                        val target = previouslyFocusedPackage?.let {
                            dockContent.findViewWithTag<View>(it)
                        } ?: dockContent.findViewWithTag<View>(app.packageName)
                        if (target?.requestFocus() != true) focusFirstApp()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showHiddenApps(body: LinearLayout) {
        val hidden = installedApps.filter { it.packageName in hiddenPackages }
        if (hidden.isEmpty()) {
            body.addView(infoCard("Скрытых приложений нет", "Список пуст", "Скрытые приложения можно будет восстановить здесь."))
            return
        }
        hidden.forEach { app ->
            body.addView(controlTile(app.label, "Нажми, чтобы вернуть приложение") {
                hiddenPackages.remove(app.packageName)
                persistAppLists()
                showHiddenAppsPage()
            }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })
        }
    }

    private fun showHiddenAppsPage() {
        showPanel("Скрытые приложения") { body -> showHiddenApps(body) }
    }

    private fun showControlCenter() {
        if (page != "home") return
        root.findViewWithTag<View>("rftv-control-center")?.let { root.removeView(it) }

        val overlay = FrameLayout(this).apply {
            tag = "rftv-control-center"
            setBackgroundColor(Color.argb(112, 3, 8, 24))
            isFocusable = false
            isFocusableInTouchMode = false
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(224, 35, 75, 132), Color.argb(238, 7, 19, 39), Color.argb(232, 10, 28, 43))
            ).apply {
                cornerRadius = dp(34).toFloat()
                setStroke(dp(1), Color.argb(125, 190, 226, 255))
            }
            setPadding(dp(18), dp(18), dp(18), dp(18))
            elevation = dp(28).toFloat()
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        val header = TextView(this).apply {
            text = "Пункт управления"
            textSize = 23f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, 0, dp(12))
        }
        card.addView(header, LinearLayout.LayoutParams(-1, dp(48)))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            isVerticalScrollBarEnabled = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        fun makeTile(icon: String, title: String, subtitle: String, action: () -> Unit): LinearLayout {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(10), dp(18), dp(10))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.argb(92, 83, 157, 224), Color.argb(72, 8, 25, 43))
                ).apply {
                    cornerRadius = dp(32).toFloat()
                    setStroke(dp(1), Color.argb(115, 190, 226, 255))
                }
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                setOnFocusChangeListener { view, focused ->
                    view.background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        if (focused) intArrayOf(Color.rgb(91, 185, 247), Color.rgb(73, 145, 225))
                        else intArrayOf(Color.argb(92, 83, 157, 224), Color.argb(72, 8, 25, 43))
                    ).apply {
                        cornerRadius = dp(32).toFloat()
                        setStroke(dp(2), if (focused) Color.WHITE else Color.argb(115, 190, 226, 255))
                    }
                    val label = (view as LinearLayout).getChildAt(1) as LinearLayout
                    for (i in 0 until label.childCount) {
                        (label.getChildAt(i) as? TextView)?.setTextColor(if (focused) Color.rgb(8, 34, 63) else Color.WHITE)
                    }
                    RftvDesignSystem.applyFocusScale(view, focused, 1.025f)
                }
                setOnClickListener { action() }
            }
            tile.addView(TextView(this).apply {
                text = icon
                textSize = 29f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(95, 190, 255))
            }, LinearLayout.LayoutParams(dp(56), dp(54)))
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            labels.addView(TextView(this).apply {
                text = title
                textSize = 18f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(Color.WHITE)
                maxLines = 1
            })
            labels.addView(TextView(this).apply {
                text = subtitle
                textSize = 14f
                setTextColor(Color.rgb(195, 219, 243))
                maxLines = 1
            })
            tile.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            return tile
        }

        val wifi = makeTile("◉", "Wi‑Fi", "Открыть настройки сети") {
            openSettings(Settings.ACTION_WIFI_SETTINGS)
        }
        val bluetooth = makeTile("ᛒ", "Bluetooth", "Подключение устройств") {
            openBluetoothPairing()
        }
        val themeLabels = arrayOf("Светлая", "Тёмная", "Авто")
        val theme = makeTile("◐", "Тема", themeLabels[themeMode.coerceIn(0, 2)]) {
            themeMode = (themeMode + 1) % 3
            prefs.edit().putInt("theme_mode", themeMode).apply()
            applyWallpaper()
            showControlCenter()
        }
        val ramInfo = readRamUsage()
        val ram = makeTile("▥", "RAM · ${ramInfo.first}%", "Используется из ${ramInfo.second}") {
            System.runFinalization()
            Runtime.getRuntime().gc()
            handler.postDelayed({
                if (root.findViewWithTag<View>("rftv-control-center") != null) showControlCenter()
            }, 500)
        }
        val saver = makeTile("◷", "Запуск заставки", "Погода · курсы валют · новости") {
            showScreensaver()
        }
        val launcherSettings = makeTile("⚙", "Настройки лаунчера", "Оформление · Dock · заставка") {
            showLauncherSettings()
        }
        val tvSettings = makeTile("▣", "Настройки ТВ", "Системные настройки устройства") {
            openSettings(Settings.ACTION_SETTINGS)
        }

        fun addFull(tile: View) {
            content.addView(tile, LinearLayout.LayoutParams(-1, dp(76)).apply { bottomMargin = dp(10) })
        }
        fun addPair(left: View, right: View) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(left, LinearLayout.LayoutParams(0, dp(76), 1f).apply { rightMargin = dp(6) })
            row.addView(right, LinearLayout.LayoutParams(0, dp(76), 1f).apply { leftMargin = dp(6) })
            content.addView(row, LinearLayout.LayoutParams(-1, dp(76)).apply { bottomMargin = dp(10) })
        }

        addFull(wifi)
        addFull(bluetooth)
        addPair(theme, ram)
        addFull(saver)
        addPair(launcherSettings, tvSettings)

        val focusRows = listOf(
            listOf(wifi),
            listOf(bluetooth),
            listOf(theme, ram),
            listOf(saver),
            listOf(launcherSettings, tvSettings)
        )
        focusRows.flatten().forEach { it.id = View.generateViewId() }
        focusRows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { columnIndex, view ->
                val sameRowNeighbor = row.getOrNull(if (columnIndex == 0) 1 else columnIndex - 1)
                val upperRow = focusRows.getOrNull(rowIndex - 1)
                val lowerRow = focusRows.getOrNull(rowIndex + 1)
                view.nextFocusLeftId = if (columnIndex > 0) row[columnIndex - 1].id else view.id
                view.nextFocusRightId = if (columnIndex < row.lastIndex) row[columnIndex + 1].id else view.id
                view.nextFocusUpId = upperRow?.getOrNull(columnIndex.coerceAtMost((upperRow.size - 1).coerceAtLeast(0)))?.id ?: view.id
                view.nextFocusDownId = lowerRow?.getOrNull(columnIndex.coerceAtMost((lowerRow.size - 1).coerceAtLeast(0)))?.id ?: view.id
            }
        }

        scroll.addView(content)
        card.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        overlay.addView(card, FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * .43f).toInt(),
            (resources.displayMetrics.heightPixels * .91f).toInt(),
            Gravity.TOP or Gravity.RIGHT
        ).apply { rightMargin = dp(22); topMargin = dp(18) })
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        overlay.post { wifi.requestFocus() }
    }

    private fun readRamUsage(): Pair<Int, String> {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        val total = memory.totalMem.coerceAtLeast(1L)
        val used = (total - memory.availMem).coerceAtLeast(0L)
        val percent = ((used * 100L) / total).toInt().coerceIn(0, 100)
        val usedGb = String.format(Locale.getDefault(), "%.1f", used / 1024.0 / 1024.0 / 1024.0)
        val totalGb = String.format(Locale.getDefault(), "%.1f", total / 1024.0 / 1024.0 / 1024.0)
        return percent to "$usedGb / $totalGb ГБ"
    }

    private fun openBluetoothPairing() {
        try {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Настройки Bluetooth недоступны", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showScreensaver() {
        ScreensaverActivity.launch(this)
    }

    private fun closeControlCenter() {
        val overlay = root.findViewWithTag<View>("rftv-control-center") ?: return
        root.removeView(overlay)
        root.findViewWithTag<View>("rftv-control-center-trigger")?.requestFocus()
    }


    private fun showAboutSystem() {
        showPanel("О системе") { body ->
            val installedInfo = packageManager.getPackageInfo(packageName, 0)
            val installedVersion = installedInfo.versionName ?: "неизвестно"
            val installedCode = if (Build.VERSION.SDK_INT >= 28) installedInfo.longVersionCode else installedInfo.versionCode.toLong()
            body.addView(infoCard("RFTV LAUNCHER", "Версия $installedVersion · build $installedCode", "Android TV · package $packageName"))
            val status = TextView(this).apply {
                text = otaStatus
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }
            body.addView(status)
            val changelog = prefs.getString("ota_changelog", null)
            if (!changelog.isNullOrBlank()) {
                body.addView(infoCard("ИЗМЕНЕНИЯ ПОСЛЕДНЕГО РЕЛИЗА",
                    prefs.getString("ota_tag", "Последний релиз") ?: "Последний релиз", changelog))
            }
            val progress = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                this.progress = otaProgress
            }
            body.addView(progress, LinearLayout.LayoutParams(-1, dp(12)).apply { bottomMargin = dp(12) })
            body.addView(glassButton("Скачать и установить обновление") {
                downloadLatestAndInstall(status, progress)
            }, LinearLayout.LayoutParams(-1, dp(54)))
            body.addView(glassButton("Открыть релизы GitHub") {
                val url = prefs.getString("ota_release_url", "https://github.com/gonscharovd/rftv-launcher/releases")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })
            body.addView(infoCard("ПРОВЕРКИ ПЕРЕД УСТАНОВКОЙ", "SHA-256 · пакет · версия · подпись",
                "Одна команда: поиск последнего стабильного релиза, загрузка APK, проверка SHA-256, package name, versionCode, ABI и сертификата. Затем Android покажет штатное подтверждение установки."))
            body.addView(glassButton("Назад к настройкам") { showLauncherSettings() },
                LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        }
    }

    private fun downloadLatestAndInstall(
        statusView: TextView,
        progressView: android.widget.ProgressBar
    ) {
        if (otaInProgress) {
            statusView.text = "Обновление уже выполняется."
            return
        }
        otaInProgress = true
        otaProgress = 0
        otaStatus = "Ищем последний стабильный релиз GitHub…"
        statusView.text = otaStatus
        progressView.progress = 0

        Thread {
            var connection: java.net.HttpURLConnection? = null
            try {
                connection = (URL("https://api.github.com/repos/gonscharovd/rftv-launcher/releases/latest")
                    .openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "RFTV-Launcher")
                }
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("GitHub HTTP ${connection.responseCode}")
                }
                val release = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                if (release.optBoolean("draft", true) || release.optBoolean("prerelease", false)) {
                    throw IllegalStateException("GitHub не вернул стабильный релиз")
                }
                val tag = release.optString("tag_name", "неизвестно")
                val releaseUrl = release.optString("html_url",
                    "https://github.com/gonscharovd/rftv-launcher/releases")
                val notes = release.optString("body", "").take(3000)
                val assets = release.optJSONArray("assets")
                    ?: throw IllegalStateException("В релизе отсутствует список APK")
                var apkUrl: String? = null
                var digest: String? = null
                var assetName: String? = null
                for (index in 0 until assets.length()) {
                    val asset = assets.getJSONObject(index)
                    val name = asset.optString("name")
                    if (name.equals("app-release.apk", true)) {
                        apkUrl = asset.optString("browser_download_url")
                            .takeIf { it.startsWith("https://") }
                        digest = asset.optString("digest").removePrefix("sha256:")
                            .lowercase(Locale.ROOT)
                            .takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                        assetName = name
                        break
                    }
                }
                val verifiedApkUrl = apkUrl?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("В стабильном релизе нет app-release.apk")
                val verifiedDigest = digest?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("GitHub не предоставил SHA-256 APK")
                prefs.edit()
                    .putString("ota_url", verifiedApkUrl)
                    .putString("ota_sha256", verifiedDigest)
                    .putString("ota_asset_name", assetName)
                    .putString("ota_release_url", releaseUrl)
                    .putString("ota_tag", tag)
                    .putString("ota_changelog", notes)
                    .apply()
                handler.post {
                    if (isFinishing || isDestroyed) {
                        otaInProgress = false
                        return@post
                    }
                    otaStatus = "Найден стабильный релиз $tag · загружаем APK…"
                    statusView.text = otaStatus
                    downloadOta(verifiedApkUrl, verifiedDigest) { value, message ->
                        otaProgress = value
                        otaStatus = message
                        statusView.text = message
                        progressView.progress = value
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    otaInProgress = false
                    otaStatus = "Не удалось начать обновление: ${e.message ?: "ошибка сети"}"
                    statusView.text = otaStatus
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun compareVersionStrings(first: String, second: String): Int {
        val a = Regex("\\d+").findAll(first).map { it.value.toIntOrNull() ?: 0 }.toList()
        val b = Regex("\\d+").findAll(second).map { it.value.toIntOrNull() ?: 0 }.toList()
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun verifyDownloadedApk(apk: File) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw IllegalStateException("APK повреждён или не содержит корректный Android manifest")
        if (candidate.packageName != packageName) {
            throw IllegalStateException("Неверный package name: ${candidate.packageName}")
        }
        val installed = packageManager.getPackageInfo(packageName, flags)
        val candidateCode = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
        val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        if (candidateCode <= installedCode) {
            throw IllegalStateException("versionCode обновления ($candidateCode) должен быть больше установленного ($installedCode)")
        }

        fun certificateDigests(info: android.content.pm.PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.apkContentsSigners
            } else {
                info.signatures
            } ?: throw IllegalStateException("Не удалось прочитать сертификат APK")
            if (signatures.isEmpty()) throw IllegalStateException("APK не имеет сертификата подписи")
            return signatures.map {
                java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }.toSet()
        }
        if (certificateDigests(candidate) != certificateDigests(installed)) {
            throw IllegalStateException("Сертификат APK не совпадает с установленным приложением. Обновление отменено.")
        }

        val zip = java.util.zip.ZipFile(apk)
        try {
            val entries = zip.entries()
            var hasNativeLibraries = false
            var hasArmV7 = false
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (name.startsWith("lib/") && name.endsWith(".so")) {
                    hasNativeLibraries = true
                    if (name.startsWith("lib/armeabi-v7a/")) hasArmV7 = true
                }
            }
            if (hasNativeLibraries && !hasArmV7) {
                throw IllegalStateException("APK содержит native-библиотеки, но не содержит ARMv7 (armeabi-v7a)")
            }
        } finally {
            zip.close()
        }
    }

    private fun downloadOta(address: String, expectedSha256: String, update: (Int, String) -> Unit) {
        otaStatus = "Загрузка обновления…"
        otaProgress = 0
        Thread {
            var connection: java.net.HttpURLConnection? = null
            val apk = pendingOtaApk()
            val directory = apk.parentFile ?: filesDir
            val temporary = File(directory, "rftv-update.apk.part")
            try {
                apk.delete()
                temporary.delete()
                connection = (URL(address).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "RFTV-Launcher")
                }
                connection.connect()
                if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
                val total = connection.contentLength
                BufferedInputStream(connection.inputStream).use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var received = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            received += count
                            if (received > 250L * 1024L * 1024L) throw IllegalStateException("APK превышает допустимый размер 250 МБ")
                            output.write(buffer, 0, count)
                            val percent = if (total > 0) (received * 100 / total).toInt().coerceIn(0, 99) else 0
                            handler.post { update(percent, "Загрузка OTA: ${if (percent > 0) "$percent%" else "получено ${received / 1024} КБ"}") }
                        }
                    }
                }
                if (temporary.length() < 100_000L) throw IllegalStateException("Загруженный файл слишком мал")
                val actualSha256 = sha256(temporary)
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    throw IllegalStateException("SHA-256 не совпадает с GitHub Release. Файл удалён.")
                }
                verifyDownloadedApk(temporary)
                if (!temporary.renameTo(apk)) throw IllegalStateException("Не удалось сохранить проверенный APK")
                handler.post {
                    otaProgress = 100
                    otaStatus = "Проверки пройдены · запускаем системный установщик"
                    update(100, otaStatus)
                    installOta(apk)
                }
            } catch (e: Exception) {
                temporary.delete()
                apk.delete()
                handler.post {
                    otaStatus = "OTA отменено: ${e.message ?: "проверка или загрузка не удалась"}"
                    update(otaProgress, otaStatus)
                }
            } finally {
                connection?.disconnect()
                handler.post { otaInProgress = false }
            }
        }.start()
    }

    private fun pendingOtaApk(): File =
        File(getExternalFilesDir(null) ?: filesDir, "rftv-update.apk")

    private fun installOta(apk: File) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                prefs.edit().putBoolean("ota_install_pending_permission", true).apply()
                otaStatus = "Разреши установку неизвестных приложений для RFTV. После возврата установка продолжится автоматически."
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")))
                return
            }
            prefs.edit().remove("ota_install_pending_permission").apply()
            val installer = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(packageName)
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("rftv-update", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback = Intent(this, OtaInstallReceiver::class.java)
                val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(this, sessionId, callback, pendingFlags)
                session.commit(pending.intentSender)
            }
            otaStatus = "Пакет передан установщику Android"
        } catch (e: Exception) {
            otaStatus = "Не удалось запустить установку: ${e.message ?: "ошибка"}"
        }
        handler.post { if (!isFinishing && page == "panel") showAboutSystem() }
    }

    private fun showLauncherSettings() {
        settingsNavigationActive = true
        settingsHubVisible = true
        page = "panel"
        activePanelTitle = "Настройки RFTV"
        handler.removeCallbacks(clockTask)
        handler.removeCallbacks(carouselTask)
        root = FrameLayout(this).apply { setBackgroundColor(RftvDesignSystem.ColorTokens.PANEL_SCRIM) }
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = RftvDesignSystem.panelSurface(this@MainActivity)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            elevation = dp(RftvDesignSystem.Elevation.PANEL_DP).toFloat()
        }
        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedGlass(Color.argb(30, 255, 255, 255), RftvDesignSystem.Radius.CARD)
            setPadding(dp(14), dp(18), dp(14), dp(14))
        }
        sidebar.addView(TextView(this).apply {
            text = "RFTV"; textSize = 23f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); setPadding(dp(10), dp(4), dp(10), dp(2))
        })
        sidebar.addView(TextView(this).apply {
            text = "НАСТРОЙКИ"; textSize = 10f; letterSpacing = .14f
            setTextColor(Color.LTGRAY); setPadding(dp(10), 0, dp(10), dp(18))
        })
        val contentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(4), dp(4))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "Настройки"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        header.addView(glassButton("Закрыть  ✕") { showHome() }, LinearLayout.LayoutParams(-2, dp(46)))
        contentColumn.addView(header)
        val detailScroll = ScrollView(this).apply { clipToPadding = false }
        val detail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(14))
        }
        detailScroll.addView(detail)
        contentColumn.addView(detailScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val sections = listOf(
            Triple("Обзор", "Быстрый доступ к настройкам", { showLauncherSettings() }),
            Triple("Внешний вид", "Обои и визуальное оформление", { showAppearanceSettings() }),
            Triple("Dock", "Размер и пропорции панели", { showDockSettings() }),
            Triple("Заставка", "Таймер и экран ожидания", { showScreensaverSettings() }),
            Triple("Приложения", "Каталог, избранное и скрытые", { showAppSettings() }),
            Triple("TV Manager", "Устройство и анализ пакетов", { showTvManager() }),
            Triple("О системе", "Версия и OTA-обновления", { showAboutSystem() })
        )
        val navItems = mutableListOf<TextView>()
        var selectedIndex = settingsSelectedIndex
        fun findFirstFocusable(view: View): View? {
            if (view.isFocusable && view.isEnabled && view.visibility == View.VISIBLE) return view
            if (view is ViewGroup) {
                for (childIndex in 0 until view.childCount) {
                    val found = findFirstFocusable(view.getChildAt(childIndex))
                    if (found != null) return found
                }
            }
            return null
        }

        fun refreshNavigation() {
            navItems.forEachIndexed { index, item ->
                val focused = item.hasFocus()
                item.background = RftvDesignSystem.focusSurface(
                    this, focused, RftvDesignSystem.Radius.CONTROL, 28, 105, index == selectedIndex
                )
                item.setTextColor(if (index == selectedIndex) Color.WHITE else Color.LTGRAY)
            }
        }
        fun renderSection(index: Int) {
            selectedIndex = index
            settingsSelectedIndex = index
            refreshNavigation()
            detail.removeAllViews()
            val section = sections[index]
            val label = when (index) {
                0 -> "ЦЕНТР НАСТРОЕК"; 1 -> "ВНЕШНИЙ ВИД"; 2 -> "ПАНЕЛЬ ПРИЛОЖЕНИЙ"
                3 -> "ЭКРАН ОЖИДАНИЯ"; 4 -> "УПРАВЛЕНИЕ ПРИЛОЖЕНИЯМИ"
                5 -> "ДИАГНОСТИКА УСТРОЙСТВА"; else -> "СИСТЕМА И ОБНОВЛЕНИЯ"
            }
            detail.addView(infoCard(label, section.first, section.second))
            when (index) {
                0 -> {
                    detail.addView(sectionLabel("БЫСТРЫЙ ДОСТУП"))
                    for (target in 1..6) {
                        val entry = sections[target]
                        detail.addView(controlTile(entry.first, entry.second) {
                            renderSection(target)
                            navItems.getOrNull(target)?.requestFocus()
                        }, LinearLayout.LayoutParams(-1, dp(66)).apply { bottomMargin = dp(8) })
                    }
                }
                1 -> {
                    detail.addView(sectionLabel("ОБОИ И ОФОРМЛЕНИЕ"))
                    addWallpaperSettings(detail)
                }
                2 -> {
                    detail.addView(sectionLabel("ШИРИНА DOCK · $dockWidthPercent%"))
                    detail.addView(settingSlider(65, 100, dockWidthPercent) { value ->
                        dockWidthPercent = value
                        prefs.edit().putInt("dock_width", value).apply()
                        updateDockSize()
                    })
                    detail.addView(sectionLabel("ВЫСОТА DOCK · $dockHeightDp dp"))
                    detail.addView(settingSlider(130, 210, dockHeightDp) { value ->
                        dockHeightDp = value
                        prefs.edit().putInt("dock_height", value).apply()
                        updateDockSize()
                    })
                }
                3 -> {
                    detail.addView(sectionLabel("АВТОЗАПУСК ПОСЛЕ БЕЗДЕЙСТВИЯ"))
                    listOf(0, 5, 15, 30, 60).forEach { minutes ->
                        val label = if (minutes == 0) "Отключено" else "$minutes минут"
                        detail.addView(controlTile(
                            if (screensaverTimeoutMinutes == minutes) "✓  $label" else label,
                            if (screensaverTimeoutMinutes == minutes) "Текущий интервал" else "Запуск через $label"
                        ) {
                            screensaverTimeoutMinutes = minutes
                            prefs.edit().putInt("screensaver_timeout_minutes", minutes).apply()
                            renderSection(3)
                            detail.post { requestFirstFocusable(detail) }
                        }, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(7) })
                    }
                    detail.addView(controlTile("Системные настройки заставки", "Выбор Dream Service") {
                        openSettings("android.settings.DREAM_SETTINGS")
                    }, LinearLayout.LayoutParams(-1, dp(64)))
                }
                4 -> {
                    detail.addView(controlTile("Каталог приложений", "Полный список · запуск и управление") {
                        settingsHubVisible = false
                        showAppCatalog()
                    }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })
                    detail.addView(controlTile("Скрытые приложения", "Восстановить приложения, скрытые из Dock и каталога") {
                        settingsHubVisible = false
                        showHiddenAppsPage()
                    }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })
                    detail.addView(controlTile("Обновить список приложений", "Повторно прочитать установленные приложения Android") {
                        loadInstalledApps()
                        renderSection(4)
                        detail.post { requestFirstFocusable(detail) }
                    }, LinearLayout.LayoutParams(-1, dp(68)))
                }
                5 -> {
                    val matchedProfiles = TvManagerProfiles.matchingProfiles()
                    detail.addView(infoCard(
                        "АНАЛИЗ УСТРОЙСТВА",
                        "${Build.MANUFACTURER} ${Build.MODEL}",
                        "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\nСовпавших проверенных профилей: ${matchedProfiles.size}\nПакеты анализируются без изменения системного состояния."
                    ))
                    detail.addView(controlTile("Данные по ТВ", "Характеристики устройства и установленные пакеты") {
                        settingsHubVisible = false
                        showTvDeviceInfo()
                    }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })
                    detail.addView(controlTile("Очистка ТВ", "Подбор профиля и безопасный предварительный анализ") {
                        settingsHubVisible = false
                        showTvCleanup()
                    }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })
                    detail.addView(controlTile("Отключённые пакеты", "Состояние и доступность восстановления") {
                        settingsHubVisible = false
                        showTvDisabledPackages()
                    }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })
                    detail.addView(controlTile("Журнал операций", "История действий TV Manager") {
                        settingsHubVisible = false
                        showTvOperationLog()
                    }, LinearLayout.LayoutParams(-1, dp(68)))
                }
                else -> {
                    detail.addView(controlTile("О системе и обновления", "Версия RFTV · скачать и установить OTA") {
                        settingsHubVisible = false
                        showAboutSystem()
                    }, LinearLayout.LayoutParams(-1, dp(76)))
                }
            }
            detail.addView(TextView(this).apply {
                text = "Настройки сохраняются автоматически. Управление с пульта: стрелки, OK и Back."
                textSize = 13f
                setTextColor(Color.LTGRAY)
                setPadding(dp(4), dp(18), dp(4), dp(8))
            })
            val firstRight = findFirstFocusable(detail)
            if (firstRight != null) {
                if (firstRight.id == View.NO_ID) firstRight.id = View.generateViewId()
                navItems.forEach { it.nextFocusRightId = firstRight.id }
                firstRight.nextFocusLeftId = navItems.getOrNull(index)?.id ?: View.NO_ID
            }
        }
        sections.forEachIndexed { index, section ->
            val item = TextView(this).apply {
                id = View.generateViewId()
                text = section.first; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isFocusableInTouchMode = true
                setPadding(dp(14), 0, dp(10), 0)
                background = RftvDesignSystem.focusSurface(
                    this@MainActivity, false, RftvDesignSystem.Radius.CONTROL, 28, 105
                )
                setOnFocusChangeListener { view, focused ->
                    view.background = RftvDesignSystem.focusSurface(
                        this@MainActivity, focused, RftvDesignSystem.Radius.CONTROL,
                        28, 105, index == selectedIndex
                    )
                    RftvDesignSystem.applyFocusScale(view, focused, 1.015f)
                }
                setOnClickListener { renderSection(index) }
            }
            navItems += item
            sidebar.addView(item, LinearLayout.LayoutParams(-1, dp(RftvDesignSystem.Size.SETTINGS_NAV_ITEM_HEIGHT)).apply { bottomMargin = dp(5) })
        }
        shell.addView(sidebar, LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * RftvDesignSystem.Size.SETTINGS_SIDEBAR_WIDTH_FRACTION).toInt(), -1))
        shell.addView(contentColumn, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(shell, FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * RftvDesignSystem.Size.SETTINGS_PANEL_WIDTH_FRACTION).toInt(),
            (resources.displayMetrics.heightPixels * RftvDesignSystem.Size.SETTINGS_PANEL_HEIGHT_FRACTION).toInt(), Gravity.CENTER
        ))
        setContentView(root)
        renderSection(settingsSelectedIndex)
        sidebar.post { navItems.getOrNull(settingsSelectedIndex)?.requestFocus() }
    }

    private fun showAppearanceSettings() {
        showPanel("Внешний вид и обои") { body ->
            body.addView(infoCard(
                "ОФОРМЛЕНИЕ RFTV",
                "Liquid Glass",
                "Лёгкие полупрозрачные поверхности, тонкие контуры и выразительный фокус. Тяжёлое постоянное размытие не используется."
            ))
            addWallpaperSettings(body)
            body.addView(glassButton("Назад к настройкам") { showLauncherSettings() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(14) })
        }
    }

    private fun showDockSettings() {
        showPanel("Настройки Dock") { body ->
            body.addView(infoCard(
                "ПАНЕЛЬ ПРИЛОЖЕНИЙ",
                "Размер и пропорции",
                "Настройки применяются сразу на главном экране и сохраняются автоматически."
            ))
            body.addView(sectionLabel("ШИРИНА DOCK · $dockWidthPercent%"))
            body.addView(settingSlider(65, 100, dockWidthPercent) { value ->
                dockWidthPercent = value
                prefs.edit().putInt("dock_width", value).apply()
                updateDockSize()
            })
            body.addView(sectionLabel("ВЫСОТА DOCK · $dockHeightDp dp"))
            body.addView(settingSlider(130, 210, dockHeightDp) { value ->
                dockHeightDp = value
                prefs.edit().putInt("dock_height", value).apply()
                updateDockSize()
            })
            body.addView(glassButton("Назад к настройкам") { showLauncherSettings() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(18) })
        }
    }

    private fun showScreensaverSettings() {
        showPanel("Настройки заставки") { body ->
            body.addView(infoCard(
                "АВТОМАТИЧЕСКИЙ ЗАПУСК",
                "После бездействия",
                "Любая кнопка пульта завершает заставку. Выбери время ожидания перед запуском."
            ))
            listOf(0, 5, 15, 30, 60).forEach { minutes ->
                val label = if (minutes == 0) "Отключено" else "$minutes минут"
                body.addView(controlTile(
                    if (screensaverTimeoutMinutes == minutes) "✓  $label" else label,
                    if (screensaverTimeoutMinutes == minutes) "Текущий интервал" else "Запуск экрана ожидания через $label"
                ) {
                    screensaverTimeoutMinutes = minutes
                    prefs.edit().putInt("screensaver_timeout_minutes", minutes).apply()
                    showScreensaverSettings()
                }, LinearLayout.LayoutParams(-1, dp(60)).apply { bottomMargin = dp(8) })
            }
            body.addView(controlTile("Системные настройки заставки", "Выбор и параметры системного Dream Service") {
                openSettings("android.settings.DREAM_SETTINGS")
            }, LinearLayout.LayoutParams(-1, dp(68)).apply { topMargin = dp(8) })
            body.addView(glassButton("Назад к настройкам") { showLauncherSettings() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(14) })
        }
    }

    private fun showAppSettings() {
        showPanel("Приложения") { body ->
            body.addView(infoCard(
                "УПРАВЛЕНИЕ ПРИЛОЖЕНИЯМИ",
                "${installedApps.size} приложений обнаружено",
                "Избранное и порядок настраиваются удержанием OK на плитке Dock или в каталоге."
            ))
            body.addView(controlTile("Каталог приложений", "Полный список · запуск и управление") {
                showAppCatalog()
            }, LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(8) })
            body.addView(controlTile("Скрытые приложения", "Восстановить приложения, скрытые из Dock и каталога") {
                showHiddenAppsPage()
            }, LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(8) })
            body.addView(controlTile("Обновить список приложений", "Повторно прочитать установленные приложения Android") {
                loadInstalledApps()
                showAppSettings()
            }, LinearLayout.LayoutParams(-1, dp(72)))
            body.addView(glassButton("Назад к настройкам") { showLauncherSettings() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(14) })
        }
    }

    private fun showWallpaperQuickSettings() {
        showPanel("Обои") { body ->
            body.addView(infoCard(
                "ДНЕВНАЯ ТЕМА",
                "Обои ${wallpaperLight + 1} из ${lightWallpapers.size}",
                if (lightCarousel) "Карусель включена · интервал ${carouselIntervalMinutes} мин."
                else "Используется выбранная фотография"
            ))
            body.addView(controlTile("Следующие дневные обои", "Переключить дневной фон") {
                wallpaperLight = (wallpaperLight + 1) % lightWallpapers.size
                prefs.edit().putInt("wallpaper_light", wallpaperLight).apply()
                showWallpaperQuickSettings()
            }, LinearLayout.LayoutParams(-1, dp(64)).apply { bottomMargin = dp(8) })
            body.addView(controlTile(
                if (lightCarousel) "Отключить дневную карусель" else "Включить дневную карусель",
                "Автоматическая смена только в светлое время"
            ) {
                lightCarousel = !lightCarousel
                prefs.edit().putBoolean("wallpaper_light_carousel", lightCarousel).apply()
                showWallpaperQuickSettings()
            }, LinearLayout.LayoutParams(-1, dp(64)).apply { bottomMargin = dp(14) })

            body.addView(infoCard(
                "НОЧНАЯ ТЕМА",
                "Обои ${wallpaperDark + 1} из ${darkWallpapers.size}",
                if (darkCarousel) "Карусель включена · интервал ${carouselIntervalMinutes} мин."
                else "Используется выбранная фотография"
            ))
            body.addView(controlTile("Следующие ночные обои", "Переключить ночной фон") {
                wallpaperDark = (wallpaperDark + 1) % darkWallpapers.size
                prefs.edit().putInt("wallpaper_dark", wallpaperDark).apply()
                showWallpaperQuickSettings()
            }, LinearLayout.LayoutParams(-1, dp(64)).apply { bottomMargin = dp(8) })
            body.addView(controlTile(
                if (darkCarousel) "Отключить ночную карусель" else "Включить ночную карусель",
                "Автоматическая смена только в тёмное время"
            ) {
                darkCarousel = !darkCarousel
                prefs.edit().putBoolean("wallpaper_dark_carousel", darkCarousel).apply()
                showWallpaperQuickSettings()
            }, LinearLayout.LayoutParams(-1, dp(64)).apply { bottomMargin = dp(14) })

            body.addView(glassButton("Все настройки обоев и интервала") {
                showLauncherSettings()
            }, LinearLayout.LayoutParams(-2, dp(48)))
        }
    }

    private fun showAppCatalog() {
        val visibleApps = installedApps
            .filter { it.packageName !in hiddenPackages }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }

        showPanel("Каталог приложений") { body ->
            body.addView(infoCard(
                "УСТАНОВЛЕННЫЕ ПРИЛОЖЕНИЯ",
                "${visibleApps.size} доступно",
                "Нажми OK для запуска. Удерживай OK, чтобы изменить избранное или скрыть приложение."
            ))

            if (visibleApps.isEmpty()) {
                body.addView(infoCard("Каталог пуст", "Нет доступных приложений", "Обнови список приложений или проверь их установку в настройках Android."))
            } else {
                visibleApps.forEach { app ->
                    val subtitle = if (app.packageName in favoritePackages) {
                        "В избранном · ${app.packageName}"
                    } else {
                        app.packageName
                    }
                    val tile = controlTile(app.label, subtitle) {
                        launchApp(app.packageName)
                    }
                    tile.setOnLongClickListener {
                        showAppActions(app)
                        true
                    }
                    body.addView(tile, LinearLayout.LayoutParams(-1, dp(68)).apply {
                        bottomMargin = dp(8)
                    })
                }
            }
        }
    }

    private fun addWallpaperSettings(body: LinearLayout) {
        body.addView(sectionLabel("ДНЕВНЫЕ ОБОИ · " + (wallpaperLight + 1) + "/10"))
        body.addView(infoCard("СВЕТЛАЯ ТЕМА", "Обои " + (wallpaperLight + 1) + " из 10",
            if (lightCarousel) "Режим: карусель · смена каждые " + carouselIntervalMinutes + " мин."
            else "Режим: один выбранный фон"))
        body.addView(wallpaperPicker(false))
        val dayActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dayActions.addView(glassButton("◀") {
            wallpaperLight = (wallpaperLight - 1 + lightWallpapers.size) % lightWallpapers.size
            prefs.edit().putInt("wallpaper_light", wallpaperLight).apply()
            showLauncherSettings()
        }, LinearLayout.LayoutParams(dp(72), dp(48)))
        dayActions.addView(glassButton("Следующий фон") {
            wallpaperLight = (wallpaperLight + 1) % lightWallpapers.size
            prefs.edit().putInt("wallpaper_light", wallpaperLight).apply()
            showLauncherSettings()
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        body.addView(dayActions)
        body.addView(glassButton(if (lightCarousel) "Дневная карусель: ВКЛ" else "Дневная карусель: ВЫКЛ") {
            lightCarousel = !lightCarousel
            prefs.edit().putBoolean("wallpaper_light_carousel", lightCarousel).apply()
            showLauncherSettings()
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        body.addView(sectionLabel("НОЧНЫЕ ОБОИ · " + (wallpaperDark + 1) + "/10"))
        body.addView(infoCard("ТЁМНАЯ ТЕМА", "Обои " + (wallpaperDark + 1) + " из 10",
            if (darkCarousel) "Режим: карусель · смена каждые " + carouselIntervalMinutes + " мин."
            else "Режим: один выбранный фон"))
        body.addView(wallpaperPicker(true))
        val nightActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nightActions.addView(glassButton("◀") {
            wallpaperDark = (wallpaperDark - 1 + darkWallpapers.size) % darkWallpapers.size
            prefs.edit().putInt("wallpaper_dark", wallpaperDark).apply()
            showLauncherSettings()
        }, LinearLayout.LayoutParams(dp(72), dp(48)))
        nightActions.addView(glassButton("Следующий фон") {
            wallpaperDark = (wallpaperDark + 1) % darkWallpapers.size
            prefs.edit().putInt("wallpaper_dark", wallpaperDark).apply()
            showLauncherSettings()
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        body.addView(nightActions)
        body.addView(glassButton(if (darkCarousel) "Ночная карусель: ВКЛ" else "Ночная карусель: ВЫКЛ") {
            darkCarousel = !darkCarousel
            prefs.edit().putBoolean("wallpaper_dark_carousel", darkCarousel).apply()
            showLauncherSettings()
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        body.addView(sectionLabel("ИНТЕРВАЛ КАРУСЕЛИ · " + carouselIntervalMinutes + " мин."))
        body.addView(settingSlider(1, 60, carouselIntervalMinutes) { value ->
            carouselIntervalMinutes = value
            prefs.edit().putInt("wallpaper_interval", value).apply()
        })
    }

    private fun wallpaperPicker(dark: Boolean): HorizontalScrollView {
        val palettes = if (dark) darkWallpapers else lightWallpapers
        val selected = if (dark) wallpaperDark else wallpaperLight
        val picker = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        palettes.forEachIndexed { index, colors ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                setPadding(dp(5), dp(5), dp(5), dp(5))
                background = roundedGlass(
                    if (index == selected) Color.argb(RftvDesignSystem.GlassAlpha.PALETTE_SELECTED, 255, 255, 255)
                    else Color.argb(RftvDesignSystem.GlassAlpha.PALETTE_RESTING, 255, 255, 255), RftvDesignSystem.Radius.CARD
                )
                setOnFocusChangeListener { view, focused ->
                    if (focused) view.background = roundedGlass(Color.argb(RftvDesignSystem.GlassAlpha.PALETTE_FOCUSED, 255, 255, 255), RftvDesignSystem.Radius.CARD)
                    else view.background = roundedGlass(
                        if (index == selected) Color.argb(RftvDesignSystem.GlassAlpha.PALETTE_SELECTED, 255, 255, 255)
                        else Color.argb(RftvDesignSystem.GlassAlpha.PALETTE_RESTING, 255, 255, 255), RftvDesignSystem.Radius.CARD
                    )
                }
                setOnClickListener {
                    if (dark) {
                        wallpaperDark = index
                        prefs.edit().putInt("wallpaper_dark", index).apply()
                    } else {
                        wallpaperLight = index
                        prefs.edit().putInt("wallpaper_light", index).apply()
                    }
                    showLauncherSettings()
                }
            }
            val preview = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(colors[0], colors[1], colors[2])
                ).apply { cornerRadius = dp(10).toFloat() }
            }
            loadPhoto(index, dark, preview, thumbnail = true)
            item.addView(preview, LinearLayout.LayoutParams(dp(116), dp(66)))
            item.addView(TextView(this).apply {
                text = if (index == selected) "✓  " + (index + 1) else (index + 1).toString()
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(0, dp(4), 0, 0)
            })
            row.addView(item, LinearLayout.LayoutParams(dp(130), dp(100)).apply {
                rightMargin = dp(8)
            })
        }
        picker.addView(row)
        return picker
    }

    private fun updateDockSize() {
        if (!::dock.isInitialized) return
        val params = dock.layoutParams as FrameLayout.LayoutParams
        params.width = (resources.displayMetrics.widthPixels * dockWidthPercent / 100f).toInt()
        val actualHeight = if (dockExpanded) max(270, dockHeightDp + 112) else dockHeightDp
        params.height = dp(actualHeight)
        dock.layoutParams = params
        updateWallpaperButtonPosition(actualHeight)
    }

    private fun updateWallpaperButtonPosition(actualDockHeightDp: Int) {
        if (!::root.isInitialized) return
        val button = root.findViewWithTag<View>("rftv-wallpaper-button") ?: return
        val params = button.layoutParams as? FrameLayout.LayoutParams ?: return
        params.bottomMargin = dp(actualDockHeightDp + RftvDesignSystem.Spacing.HOME_WALLPAPER_DOCK_GAP)
        button.layoutParams = params
    }

    private fun showTvManager() {
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName }
        val disabled = apps.count { isPackageDisabled(it.packageName, it.enabled) }
        showPanel("TV Manager") { body ->
            body.addView(infoCard(
                "ЦЕНТР УПРАВЛЕНИЯ ТВ",
                "${Build.MANUFACTURER} ${Build.MODEL}",
                "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\nУстановлено пакетов: ${apps.size} · отключено: $disabled"
            ))
            body.addView(controlTile("Данные по ТВ", "Характеристики устройства и установленные пакеты") {
                showTvDeviceInfo()
            }, LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(8) })
            body.addView(controlTile("Очистка ТВ", "Подбор профиля и безопасный предварительный анализ") {
                showTvCleanup()
            }, LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(8) })
            body.addView(controlTile("Отключённые пакеты", "Состояние и доступность восстановления") {
                showTvDisabledPackages()
            }, LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(8) })
            body.addView(controlTile("Журнал операций", "История действий TV Manager") {
                showTvOperationLog()
            }, LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(8) })
            body.addView(infoCard(
                "РЕЖИМ БЕЗОПАСНОСТИ",
                if (tvManagerShellService != null && (tvManagerShellUid == 0 || tvManagerShellUid == 2000))
                    "Диагностика доступна · ручные операции доступны через Shizuku"
                else "Диагностика доступна · изменение пакетов заблокировано",
                "Автоматический режим включается только при точном профиле и явно проверенных пакетах. Ручной режим требует отдельного подтверждения каждого изменения. Защищённые компоненты недоступны для переключения."
            ))
            body.addView(glassButton("Назад к настройкам") { showLauncherSettings() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })
        }
    }

    private fun isPackageDisabled(packageId: String, manifestEnabled: Boolean): Boolean {
        return try {
            when (packageManager.getApplicationEnabledSetting(packageId)) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> true
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> false
                else -> !manifestEnabled
            }
        } catch (_: Exception) { !manifestEnabled }
    }

    private fun showTvDeviceInfo() {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().absolutePath)
        val totalStorage = stat.blockCountLong * stat.blockSizeLong
        val freeStorage = stat.availableBlocksLong * stat.blockSizeLong
        val display = windowManager.defaultDisplay
        val metrics = android.util.DisplayMetrics().also { display.getRealMetrics(it) }
        val uptime = android.os.SystemClock.elapsedRealtime() / 1000L
        val uptimeText = String.format(Locale.getDefault(), "%d дн. %02d:%02d",
            uptime / 86400, (uptime % 86400) / 3600, (uptime % 3600) / 60)
        val systemApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName && it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
        val userApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName && it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
        showPanel("Данные по ТВ") { body ->
            body.addView(infoCard("УСТРОЙСТВО", "${Build.MANUFACTURER} ${Build.MODEL}",
                "Brand: ${Build.BRAND}\nDevice: ${Build.DEVICE}\nProduct: ${Build.PRODUCT}\nBoard: ${Build.BOARD}\nHardware: ${Build.HARDWARE}"))
            body.addView(infoCard("СИСТЕМА", "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
                "Прошивка: ${Build.DISPLAY}\nBuild ID: ${Build.ID}\nFingerprint: ${Build.FINGERPRINT}"))
            body.addView(infoCard("ПРОЦЕССОР", Build.SUPPORTED_ABIS.joinToString(),
                "Ядра CPU: ${Runtime.getRuntime().availableProcessors()}"))
            body.addView(infoCard("ОПЕРАТИВНАЯ ПАМЯТЬ",
                "${formatBytes(memory.totalMem)} всего · ${formatBytes(memory.availMem)} свободно",
                "Порог низкой памяти: ${formatBytes(memory.threshold)}\nLow memory: ${memory.lowMemory}"))
            body.addView(infoCard("ВНУТРЕННЕЕ ХРАНИЛИЩЕ",
                "${formatBytes(totalStorage - freeStorage)} занято · ${formatBytes(freeStorage)} свободно",
                "Общий объём: ${formatBytes(totalStorage)}"))
            body.addView(infoCard("ЭКРАН", "${metrics.widthPixels} × ${metrics.heightPixels}",
                "Плотность: ${metrics.densityDpi} dpi · scale ${metrics.density}"))
            body.addView(infoCard("ВРЕМЯ РАБОТЫ", uptimeText, "Время с последней загрузки устройства"))
            body.addView(infoCard("ПРИЛОЖЕНИЯ",
                "${systemApps.size} системных · ${userApps.size} пользовательских",
                "Полный перечень пакетов доступен в следующем подразделе."))
            body.addView(controlTile("Установленные пакеты", "Поиск, фильтр и цветовая классификация") {
                showTvPackages()
            }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })
            body.addView(controlTile("ADB и разрешения", "Состояние отладки, IP, порт и реальные возможности") {
                showTvAdb()
            }, LinearLayout.LayoutParams(-1, dp(68)))
            body.addView(glassButton("Назад в TV Manager") { showTvManager() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(12) })
        }
    }

    private fun showTvPackages() {
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase(Locale.getDefault()) }
        val matched = TvManagerProfiles.matchingProfiles()
        fun risk(packageId: String): Pair<String, Int> {
            val criticalTokens = listOf("android", "systemui", "settings", "packageinstaller",
                "inputmethod", "telephony", "networkstack", "launcher", "sberdevices.services")
            if (criticalTokens.any { packageId.contains(it, true) }) return Pair("КРИТИЧЕСКИЙ", RftvDesignSystem.ColorTokens.RISK_CRITICAL)
            val known = matched.flatMap { it.packages }.firstOrNull { it.packageId == packageId }
            return when (known?.risk) {
                "Критический", "Высокий" -> Pair("ВЫСОКИЙ РИСК", RftvDesignSystem.ColorTokens.RISK_CRITICAL)
                "Низкий" -> Pair("НЕ ПРОВЕРЕН", RftvDesignSystem.ColorTokens.RISK_REVIEW)
                else -> Pair("НЕ ПРОВЕРЕН", RftvDesignSystem.ColorTokens.RISK_REVIEW)
            }
        }
        showPanel("Установленные пакеты · ${apps.size}") { body ->
            body.addView(infoCard("КЛАССИФИКАЦИЯ",
                "Красный — критический · жёлтый — не проверен",
                "Зелёный статус не назначается автоматически. Для него требуется проверенный профиль именно этой модели и прошивки."))
            val search = android.widget.EditText(this).apply {
                hint = "Поиск по названию или package name"
                setSingleLine(true)
                textSize = 16f
                setTextColor(Color.WHITE)
                setHintTextColor(Color.LTGRAY)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = roundedGlass(Color.argb(RftvDesignSystem.GlassAlpha.SEARCH_FIELD, 255, 255, 255), RftvDesignSystem.Radius.CONTROL)
            }
            body.addView(search, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) })
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val scroll = ScrollView(this).apply { addView(list) }
            fun render(query: String) {
                list.removeAllViews()
                apps.filter {
                    val label = packageManager.getApplicationLabel(it).toString()
                    label.contains(query, true) || it.packageName.contains(query, true)
                }.take(250).forEach { app ->
                    val label = try { packageManager.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName }
                    val isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    val state = if (isPackageDisabled(app.packageName, app.enabled)) "Отключено" else "Включено"
                    val category = if (isSystem) "Системное" else "Пользовательское"
                    val (riskLabel, riskColor) = risk(app.packageName)
                    val card = controlTile(
                        label,
                        "${app.packageName} · $category · $state · $riskLabel · ОК — подробности"
                    ) {
                        showTvPackageDetails(app)
                    }
                    card.background = roundedGlass(
                        Color.argb(RftvDesignSystem.GlassAlpha.RISK_CARD, riskColor.red(), riskColor.green(), riskColor.blue()),
                        RftvDesignSystem.Radius.CONTROL
                    )
                    list.addView(card, LinearLayout.LayoutParams(-1, dp(66)).apply {
                        bottomMargin = dp(7)
                    })
                }
            }
            search.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            render("")
            body.addView(scroll, LinearLayout.LayoutParams(-1, dp(390)))
            body.addView(glassButton("Экспортировать полный список") {
                val report = apps.joinToString("\n") { app ->
                    val label = try { packageManager.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName }
                    "${app.packageName}\t$label\t${if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) "system" else "user"}\t${if (isPackageDisabled(app.packageName, app.enabled)) "disabled" else "enabled"}"
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "RFTV — список пакетов")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                startActivity(Intent.createChooser(send, "Экспорт пакетов"))
            }, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(10) })
            body.addView(glassButton("Назад к данным ТВ") { showTvDeviceInfo() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })
        }
    }

    private fun showTvPackageDetails(app: ApplicationInfo) {
        val packageInfo = try {
            packageManager.getPackageInfo(
                app.packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or PackageManager.GET_ACTIVITIES
            )
        } catch (_: Exception) {
            showTvPackages()
            return
        }
        val requested = packageInfo.requestedPermissions ?: emptyArray()
        val permissions = requested.map { permission ->
            val granted = packageManager.checkPermission(permission, app.packageName) == PackageManager.PERMISSION_GRANTED
            val permissionLabel = try {
                packageManager.getPermissionInfo(permission, 0).loadLabel(packageManager).toString()
            } catch (_: Exception) {
                permission.substringAfterLast(".")
            }
            Triple(permission, granted, permissionLabel)
        }
        val grantedCount = permissions.count { it.second }
        val deniedCount = permissions.size - grantedCount
        val appLabel = try { packageManager.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName }
        val installTime = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date(packageInfo.firstInstallTime))
        val updateTime = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date(packageInfo.lastUpdateTime))

        val matchedProfileEntry = TvManagerProfiles.matchingProfiles()
            .flatMap { it.packages }
            .firstOrNull { it.packageId == app.packageName }
        val analysis = PackageAnalysisPolicy.analyze(
            PackageAnalysisPolicy.Signals(
                packageName = app.packageName,
                label = appLabel,
                isSystemApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                enabled = !isPackageDisabled(app.packageName, app.enabled),
                requestedPermissions = requested.toList(),
                serviceCount = packageInfo.services?.size ?: 0,
                receiverCount = packageInfo.receivers?.size ?: 0,
                activityCount = packageInfo.activities?.size ?: 0,
                profilePurpose = matchedProfileEntry?.note,
                profileRisk = matchedProfileEntry?.risk
            )
        )
        val analysisSeverity = when (analysis.severity) {
            PackageAnalysisPolicy.Severity.CRITICAL -> "КРИТИЧЕСКИЙ КОМПОНЕНТ"
            PackageAnalysisPolicy.Severity.REVIEW -> "ТРЕБУЕТ ПРОВЕРКИ"
            PackageAnalysisPolicy.Severity.INFORMATIONAL -> "ИНФОРМАЦИЯ"
        }
        val analysisConfidence = when (analysis.confidence) {
            PackageAnalysisPolicy.Confidence.HIGH -> "Высокая"
            PackageAnalysisPolicy.Confidence.MEDIUM -> "Средняя"
            PackageAnalysisPolicy.Confidence.LOW -> "Низкая"
        }

        showPanel("Пакет · $appLabel") { body ->
            body.addView(infoCard(
                "АНАЛИЗ RFTV · $analysisSeverity",
                analysis.purpose,
                "Категория: ${analysis.category}\nУверенность: $analysisConfidence\n\nФакты:\n• " +
                    analysis.evidence.joinToString("\n• ") + "\n\nОграничение: ${analysis.limitation}"
            ))
            body.addView(infoCard("ИДЕНТИФИКАЦИЯ", appLabel, app.packageName))
            body.addView(infoCard(
                "СОСТОЯНИЕ",
                if (isPackageDisabled(app.packageName, app.enabled)) "Отключено" else "Включено",
                (if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) "Системное приложение" else "Пользовательское приложение") +
                    "\nUID: ${app.uid}\nУстановлено: $installTime\nОбновлено: $updateTime"
            ))
            body.addView(infoCard(
                "РАЗРЕШЕНИЯ",
                "${permissions.size} запрошено · $grantedCount выдано · $deniedCount не выдано",
                "Статус рассчитан по фактической проверке PackageManager. Отсутствие разрешения не означает ошибку приложения."
            ))
            if (permissions.isEmpty()) {
                body.addView(infoCard("Разрешения не запрошены", "Список пуст", "Пакет не объявил permissions в манифесте."))
            } else {
                permissions.forEach { (permission, granted, label) ->
                    body.addView(infoCard(
                        if (granted) "ВЫДАНО" else "НЕ ВЫДАНО",
                        label,
                        permission
                    ).apply {
                        background = roundedGlass(
                            Color.argb(
                                RftvDesignSystem.GlassAlpha.RISK_CARD,
                                if (granted) 42 else 150,
                                if (granted) 150 else 112,
                                if (granted) 82 else 42
                            ),
                            RftvDesignSystem.Radius.CONTROL
                        )
                    })
                }
            }
            body.addView(glassButton("Назад к списку пакетов") { showTvPackages() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(10) })
        }
    }

    private fun Int.red(): Int = android.graphics.Color.red(this)
    private fun Int.green(): Int = android.graphics.Color.green(this)
    private fun Int.blue(): Int = android.graphics.Color.blue(this)

    private fun connectTvManagerShizuku() {
        if (!TvManagerShizukuBridge.isBinderReady()) {
            val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (launch != null) {
                startActivity(launch)
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/guide/setup/")))
            }
            Toast.makeText(this, "Запусти Shizuku и вернись в RFTV", Toast.LENGTH_LONG).show()
            return
        }
        if (!TvManagerShizukuBridge.isSupportedServer()) {
            Toast.makeText(this, "Версия Shizuku API не поддерживается", Toast.LENGTH_LONG).show()
            return
        }
        if (!TvManagerShizukuBridge.hasPermission()) {
            try {
                TvManagerShizukuBridge.requestPermission()
            } catch (error: Exception) {
                Toast.makeText(this, "Не удалось запросить доступ Shizuku: ${error.message}", Toast.LENGTH_LONG).show()
            }
            return
        }
        bindTvManagerShizukuService()
    }

    private fun bindTvManagerShizukuService() {
        if (!TvManagerShizukuBridge.hasPermission()) {
            Toast.makeText(this, "Нет разрешения Shizuku", Toast.LENGTH_LONG).show()
            return
        }
        try {
            if (!TvManagerShizukuBridge.bind(this, tvManagerShizukuConnection)) {
                Toast.makeText(this, "Привилегированный канал недоступен", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Подключение TV Manager запрошено", Toast.LENGTH_SHORT).show()
            }
        } catch (error: Exception) {
            tvManagerShellService = null
            tvManagerShellUid = -1
            Toast.makeText(this, "Ошибка подключения Shizuku: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showTvAdb() {
        val adbDebuggingEnabled = try {
            android.provider.Settings.Global.getInt(contentResolver,
                android.provider.Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Exception) { false }

        val access = AdbAccessDiagnostics.inspect()
        val ip = try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
            val raw = wifi.connectionInfo.ipAddress
            if (raw == 0) "Не определён" else
                listOf(raw and 0xff, raw shr 8 and 0xff, raw shr 16 and 0xff, raw shr 24 and 0xff).joinToString(".")
        } catch (_: Exception) { "Недоступен" }

        showPanel("ADB и разрешения") { body ->
            val shizukuReady = TvManagerShizukuBridge.isSupportedServer()
            val shizukuPermission = TvManagerShizukuBridge.hasPermission()
            val shizukuConnected = tvManagerShellService != null &&
                (tvManagerShellUid == 0 || tvManagerShellUid == 2000)

            body.addView(infoCard("СИСТЕМНЫЙ СТАТУС ADB",
                if (adbDebuggingEnabled) "ADB Debugging включён" else "ADB Debugging выключен",
                "Это только системный индикатор отладки. RFTV не изменяет этот параметр."))
            body.addView(infoCard("ДОСТУП RFTV К ADB",
                if (shizukuConnected) "Привилегированный канал подключён · UID $tvManagerShellUid" else "ADB Shell не получен",
                if (shizukuConnected) "Shizuku UserService отвечает. Это подтверждает UID канала, но не означает, что любая команда управления пакетами разрешена прошивкой."
                else access.details))
            body.addView(infoCard("SHIZUKU",
                when {
                    shizukuConnected -> "Подключён"
                    shizukuReady && shizukuPermission -> "Сервер готов · требуется подключить службу"
                    shizukuReady -> "Сервер готов · ожидается разрешение RFTV"
                    else -> "Сервер не обнаружен"
                },
                "Shizuku запускается отдельно. На Android до 11 для старта без root обычно нужен авторизованный ADB-хост; после перезагрузки службу может потребоваться запустить снова."))
            body.addView(glassButton(
                if (shizukuConnected) "Переподключить Shizuku" else "Подключить Shizuku"
            ) { connectTvManagerShizuku() }, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })
            body.addView(infoCard("ЛОКАЛЬНЫЙ IP", ip,
                "Сетевой адрес интерфейса устройства. Не является подтверждением подключения ADB."))
            body.addView(infoCard("ADB-ПОРТ", access.portLabel,
                "Порт отображается только если его удалось достоверно определить."))
            body.addView(sectionLabel("ПРОВЕРЕННЫЕ ВОЗМОЖНОСТИ"))
            body.addView(infoCard("Чтение системной информации",
                if (access.systemInfoAvailable) "Доступно" else "Не подтверждено",
                "Диагностика через публичные Android API."))
            body.addView(infoCard("ADB Shell",
                if (access.shellAvailable) "Доступен" else "Нет доступа",
                "Проверяется фактическое выполнение без изменения состояния устройства."))
            body.addView(infoCard("Управление пакетами",
                if (shizukuConnected) "Привилегированный канал подключён" else "Недоступно / не подтверждено",
                "Наличие Shizuku подтверждает UID канала, но не гарантирует разрешение прошивки на конкретную команду. Первая операция выполняется отдельно, после сохранения исходного состояния и с проверкой результата."))
            body.addView(infoCard("Root",
                if (access.rootAvailable) "Обнаружен" else "Не обнаружен",
                "Root не требуется для обычной диагностики и не предполагается автоматически."))
            body.addView(glassButton("Повторить проверку") { showTvAdb() },
                LinearLayout.LayoutParams(-2, dp(48)))
            body.addView(glassButton("Назад к данным ТВ") { showTvDeviceInfo() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })
        }
    }

    private fun showTvCleanup() {
        val matched = TvManagerProfiles.matchingProfiles()
        val profile = matched.firstOrNull()
        val channelReady = tvManagerShellService != null && (tvManagerShellUid == 0 || tvManagerShellUid == 2000)
        val autoCandidates = tvManagerController.automaticCandidates()
        showPanel("Очистка ТВ") { body ->
            body.addView(infoCard("АВТО-ОПРЕДЕЛЕНИЕ",
                "${Build.MANUFACTURER} ${Build.MODEL}",
                "Android ${Build.VERSION.RELEASE} · ${Build.DISPLAY}\nПлата: ${Build.BOARD} · Hardware: ${Build.HARDWARE}\nDevice: ${Build.DEVICE} · Product: ${Build.PRODUCT}\nABI: ${Build.SUPPORTED_ABIS.joinToString()}"))
            body.addView(infoCard("ПРОФИЛЬ УСТРОЙСТВА",
                profile?.title ?: "Точного профиля нет",
                if (profile == null) "Для этой конфигурации нет проверенного профиля. Автоматическое отключение запрещено. Донорские профили Sber для Android 11/13 не подходят Android 9."
                else "${profile.androidLabel}\nИсточник: ${profile.sourceUrl}\nСовпадение аппаратных идентификаторов и API подтверждено."))
            body.addView(infoCard("АВТОМАТИЧЕСКАЯ ОЧИСТКА",
                if (autoCandidates.isNotEmpty() && channelReady) "Доступна после предпросмотра" else "Пока недоступна",
                when {
                    autoCandidates.isEmpty() -> "В точном профиле нет пакетов, прошедших все проверки: подтверждённая необязательность, высокая уверенность и проверенные зависимости. Автоматически ничего не отключается."
                    !channelReady -> "Профиль найден, но Shizuku privileged channel не подключён."
                    else -> "Кандидатов: ${autoCandidates.size}. Будет показан полный список, затем каждое изменение пройдёт с snapshot, проверкой и общим откатом при первой ошибке."
                }))
            body.addView(glassButton("Открыть автоматическую очистку") { showTvAutomaticCleanup() },
                LinearLayout.LayoutParams(-2, dp(48)))
            body.addView(infoCard("РУЧНАЯ ОЧИСТКА",
                if (channelReady) "Привилегированный канал подключён" else "Требуется Shizuku",
                "Полный список приложений и системных пакетов. Защищённые компоненты заблокированы. Для неизвестных пакетов — отдельное предупреждение и подтверждение."))
            body.addView(glassButton("Открыть ручную очистку") { showTvManualCleanup() },
                LinearLayout.LayoutParams(-2, dp(48)))
            if (!channelReady) {
                body.addView(glassButton("Подключить Shizuku") { connectTvManagerShizuku() },
                    LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })
            }
            body.addView(sectionLabel("КАТАЛОГ ПРОФИЛЕЙ"))
            TvManagerProfiles.all.forEach { item ->
                body.addView(controlTile(item.title, "${item.androidLabel} · ${item.packages.size} справочных записей") {
                    showTvManagerProfile(item.id)
                }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })
            }
            body.addView(glassButton("Сканировать пакеты") { showTvPackages() },
                LinearLayout.LayoutParams(-2, dp(48)))
            body.addView(glassButton("Назад в TV Manager") { showTvManager() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })
        }
    }

    private fun showTvAutomaticCleanup() {
        val candidates = tvManagerController.automaticCandidates()
        val channelReady = tvManagerShellService != null && (tvManagerShellUid == 0 || tvManagerShellUid == 2000)
        showPanel("Автоматическая очистка") { body ->
            body.addView(infoCard("СТРОГАЯ ПРОВЕРКА",
                if (candidates.isEmpty()) "Нет разрешённых кандидатов" else "Подтверждено кандидатов: ${candidates.size}",
                "Нужны точное совпадение профиля, явная отметка optional, высокая уверенность и проверенные зависимости. Списки CleanYourTV не считаются автоматически безопасными для другой модели или прошивки."))
            if (candidates.isNotEmpty()) {
                candidates.forEach { candidate ->
                    body.addView(infoCard(
                        candidate.label,
                        candidate.packageName,
                        "${candidate.decision.reason}\nСистемное приложение: ${candidate.isSystemApp}"
                    ))
                }
            }
            if (candidates.isNotEmpty() && channelReady) {
                body.addView(glassButton("Подтвердить и запустить очистку") {
                    val summary = candidates.joinToString("\n") { "• ${it.label} — ${it.packageName}" }
                    AlertDialog.Builder(this)
                        .setTitle("Запустить автоматическую очистку?")
                        .setMessage("Будут отключены только эти пакеты:\n\n$summary\n\nRFTV сохранит исходные состояния перед каждой операцией. При первой ошибке остановится и восстановит уже изменённые пакеты в обратном порядке.")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Запустить") { _, _ ->
                            tvManagerExecutor.execute {
                                val result = tvManagerController.disableAutomaticBatch(true)
                                runOnUiThread {
                                    val details = if (result.rollbackMessages.isEmpty()) "" else "\n" + result.rollbackMessages.joinToString("\n")
                                    Toast.makeText(this, result.message + details, Toast.LENGTH_LONG).show()
                                    showTvAutomaticCleanup()
                                }
                            }
                        }.show()
                }, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(10) })
            } else if (candidates.isNotEmpty()) {
                body.addView(infoCard("НЕТ ДОСТУПА",
                    "Подключи Shizuku",
                    "Привилегированный канал не подключён; операции заблокированы."))
            } else {
                body.addView(infoCard("АВТОМАТИЧЕСКИЙ РЕЖИМ НЕДОСТУПЕН",
                    "Профиль не подтверждает безопасные кандидаты",
                    "Для текущего ТВ автоматическое отключение запрещено. Можно использовать ручную очистку с отдельным подтверждением каждого пакета."))
            }
            body.addView(glassButton("Назад к очистке ТВ") { showTvCleanup() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })
        }
    }

    private fun showTvManualCleanup() {
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase(Locale.getDefault()) }
        val snapshots = tvManagerController.activeSnapshots().associateBy { it.packageName }
        val channelReady = tvManagerShellService != null && (tvManagerShellUid == 0 || tvManagerShellUid == 2000)
        showPanel("Ручная очистка · ${apps.size}") { body ->
            body.addView(infoCard("ПЕРЕД ИЗМЕНЕНИЕМ",
                if (channelReady) "Shizuku подключён · UID $tvManagerShellUid" else "Привилегированный канал не подключён",
                "Каждое изменение выполняется отдельно. Сначала сохраняется исходное состояние, затем меняется пакет и проверяется результат. При ошибке выполняется попытка отката. Уже отключённые до RFTV пакеты не включаются в список восстановления."))
            if (!channelReady) {
                body.addView(glassButton("Подключить Shizuku") { connectTvManagerShizuku() },
                    LinearLayout.LayoutParams(-2, dp(48)))
            }
            val search = android.widget.EditText(this).apply {
                hint = "Поиск по названию или package name"
                setSingleLine(true)
                textSize = 16f
                setTextColor(Color.WHITE)
                setHintTextColor(Color.LTGRAY)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = roundedGlass(Color.argb(RftvDesignSystem.GlassAlpha.SEARCH_FIELD, 255, 255, 255), RftvDesignSystem.Radius.CONTROL)
            }
            body.addView(search, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) })
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val scroll = ScrollView(this).apply { isFillViewport = true; addView(list) }
            fun render(query: String) {
                list.removeAllViews()
                apps.filter { app ->
                    val label = try { packageManager.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName }
                    label.contains(query, true) || app.packageName.contains(query, true)
                }.forEach { app ->
                    val label = try { packageManager.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName }
                    val preview = tvManagerController.preview(app.packageName, PackageOperationPolicy.Mode.MANUAL)
                    val disabled = isPackageDisabled(app.packageName, app.enabled)
                    val owned = snapshots[app.packageName]
                    val protected = preview?.isProtected != false
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(dp(12), dp(8), dp(12), dp(8))
                        background = roundedGlass(
                            Color.argb(RftvDesignSystem.GlassAlpha.CARD_SURFACE, 255, 255, 255),
                            RftvDesignSystem.Radius.CONTROL
                        )
                        isFocusable = false
                    }
                    val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    val titleView = TextView(this).apply {
                        text = label
                        textSize = 16f
                        setTextColor(Color.WHITE)
                        maxLines = 1
                        isFocusable = true
                        isClickable = true
                        contentDescription = "Подробности пакета $label"
                        setOnClickListener { showTvPackageDetails(app) }
                    }
                    val detailView = TextView(this).apply {
                        text = "${app.packageName}\n${if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) "Системное" else "Пользовательское"} · ${if (disabled) "Отключено" else "Включено"} · ${when { protected -> "Защищено"; owned != null -> "Изменено RFTV"; preview?.profileMatched == true -> "Есть справочный профиль"; else -> "Нет проверенного профиля" }} · ${if (preview?.decision?.requiresExplicitWarning == true) "Низкая уверенность / зависимости не проверены" else "Подробнее — OK"}"
                        textSize = 11f
                        setTextColor(Color.LTGRAY)
                        maxLines = 2
                    }
                    textColumn.addView(titleView)
                    textColumn.addView(detailView)
                    row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
                    val toggle = android.widget.Switch(this).apply {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        isChecked = !disabled
                        isEnabled = channelReady && if (disabled) {
                            owned != null
                        } else {
                            !protected && owned == null
                        }
                        contentDescription = if (owned != null) "Восстановить ${app.packageName}" else "Отключить ${app.packageName}"
                    }
                    toggle.setOnCheckedChangeListener { button, checked ->
                        button.isEnabled = false
                        if (!checked && !disabled) {
                            val warning = preview?.decision?.reason ?: "Назначение пакета не подтверждено."
                            AlertDialog.Builder(this)
                                .setTitle("Отключить пакет вручную?")
                                .setMessage("$label\n${app.packageName}\n\n$warning\n\nВозможные последствия: приложение или зависимые функции ТВ могут перестать работать. Данные не удаляются. RFTV сохранит исходное состояние и попытается откатить изменение при ошибке.")
                                .setNegativeButton("Отмена") { _, _ -> showTvManualCleanup() }
                                .setPositiveButton("Отключить") { _, _ ->
                                    tvManagerExecutor.execute {
                                        val result = tvManagerController.disable(
                                            app.packageName, PackageOperationPolicy.Mode.MANUAL, true
                                        )
                                        runOnUiThread {
                                            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                                            showTvManualCleanup()
                                        }
                                    }
                                }.setOnCancelListener { showTvManualCleanup() }
                                .show()
                        } else if (checked && disabled && owned != null) {
                            AlertDialog.Builder(this)
                                .setTitle("Восстановить пакет?")
                                .setMessage("$label\n${app.packageName}\n\nRFTV вернёт точное сохранённое состояние: ${owned.originalEnabledSetting}.")
                                .setNegativeButton("Отмена") { _, _ -> showTvManualCleanup() }
                                .setPositiveButton("Восстановить") { _, _ ->
                                    tvManagerExecutor.execute {
                                        val result = tvManagerController.restore(app.packageName)
                                        runOnUiThread {
                                            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                                            showTvManualCleanup()
                                        }
                                    }
                                }.setOnCancelListener { showTvManualCleanup() }
                                .show()
                        } else {
                            showTvManualCleanup()
                        }
                    }
                    row.addView(toggle)
                    list.addView(row, LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(6) })
                    if (owned != null && !disabled && channelReady) {
                        list.addView(glassButton("Проверить и вернуть исходное состояние") {
                            AlertDialog.Builder(this)
                                .setTitle("Завершить незавершённую операцию?")
                                .setMessage("${app.packageName}\nRFTV сохранён исходный snapshot, но текущее состояние не подтверждено. Команда вернёт сохранённое состояние и проверит результат.")
                                .setNegativeButton("Отмена", null)
                                .setPositiveButton("Восстановить") { _, _ ->
                                    tvManagerExecutor.execute {
                                        val result = tvManagerController.restore(app.packageName)
                                        runOnUiThread {
                                            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                                            showTvManualCleanup()
                                        }
                                    }
                                }.show()
                        }, LinearLayout.LayoutParams(-2, dp(42)).apply { bottomMargin = dp(8) })
                    }
                }
            }
            search.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            render("")
            body.addView(scroll, LinearLayout.LayoutParams(-1, dp(390)))
            body.addView(glassButton("Назад к очистке ТВ") { showTvCleanup() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })
        }
    }

    private fun showTvDisabledPackages() {
        val disabled = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName && isPackageDisabled(it.packageName, it.enabled) }
        val owned = tvManagerController.activeSnapshots().associateBy { it.packageName }
        showPanel("Отключённые пакеты") { body ->
            body.addView(infoCard("НАЙДЕНО ОТКЛЮЧЁННЫХ ПАКЕТОВ",
                disabled.size.toString(),
                "Состояние отражает PackageManager. Только пакеты с подтверждённым snapshot RFTV можно восстановить отсюда. Ранее отключённые пользователем пакеты не включаются автоматически."))
            if (disabled.isEmpty()) body.addView(infoCard("Список пуст", "Отключённых пакетов не найдено", ""))
            disabled.forEach { app ->
                val label = try { packageManager.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName }
                val snapshot = owned[app.packageName]
                body.addView(infoCard(label, app.packageName,
                    if (snapshot != null) "Отключено RFTV · исходное состояние: ${snapshot.originalEnabledSetting}"
                    else "Отключено до RFTV или другим способом · восстановление заблокировано."))
                if (snapshot != null) {
                    body.addView(glassButton("Восстановить исходное состояние") {
                        AlertDialog.Builder(this)
                            .setTitle("Восстановить пакет?")
                            .setMessage("$label\n${app.packageName}\nRFTV вернёт сохранённое состояние ${snapshot.originalEnabledSetting}.")
                            .setNegativeButton("Отмена", null)
                            .setPositiveButton("Восстановить") { _, _ ->
                                tvManagerExecutor.execute {
                                    val result = tvManagerController.restore(app.packageName)
                                    runOnUiThread {
                                        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                                        showTvDisabledPackages()
                                    }
                                }
                            }.show()
                    }, LinearLayout.LayoutParams(-2, dp(44)).apply { bottomMargin = dp(8) })
                }
            }
            body.addView(glassButton("Назад в TV Manager") { showTvManager() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })
        }
    }

    private fun showTvOperationLog() {
        val entries = tvManagerController.journal()
        showPanel("Журнал операций") { body ->
            body.addView(infoCard("ИСТОРИЯ", "${entries.size} записей",
                "Журнал содержит сохранение исходного состояния, отключение, восстановление и результаты отката."))
            if (entries.isEmpty()) body.addView(infoCard("История пуста", "Операций пока нет", ""))
            entries.forEach { entry ->
                body.addView(infoCard(
                    "${entry.action} · ${entry.packageName}",
                    SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp)),
                    "${entry.result}\nПровайдер: ${entry.provider}\nСостояние: ${entry.beforeState} → ${entry.afterState}"
                ))
            }
            body.addView(glassButton("Назад в TV Manager") { showTvManager() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })
        }
    }

    private fun showTvManagerProfile(profileId: String) {
        val profile = TvManagerProfiles.all.firstOrNull { it.id == profileId } ?: run {
            showTvManager()
            return
        }
        val isMatch = profile in TvManagerProfiles.matchingProfiles()
        showPanel("Профиль · ${profile.title}") { body ->
            body.addView(infoCard(
                if (isMatch) "СОВПАДЕНИЕ ПО УСТРОЙСТВУ" else "СПРАВОЧНЫЙ ПРОФИЛЬ · НЕ ПРИМЕНЯТЬ",
                profile.androidLabel,
                profile.testedNotes
            ))
            body.addView(infoCard(
                "ОГРАНИЧЕНИЕ",
                "Это не готовая команда очистки",
                "Пакеты ниже приведены для анализа. RFTV не выполняет отключение. Список тестировался на конкретной модели и прошивке; даже близкий телевизор может зависеть от других системных компонентов."
            ))
            var lastGroup = ""
            profile.packages.forEach { item ->
                if (item.group != lastGroup) {
                    lastGroup = item.group
                    body.addView(sectionLabel(lastGroup.uppercase(Locale.getDefault())))
                }
                body.addView(infoCard(
                    item.label,
                    item.packageId,
                    "${item.note}\nУровень внимания: ${item.risk}"
                ))
            }
            body.addView(glassButton("Открыть оригинальный профиль CleanYourTV") {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profile.sourceUrl)))
                } catch (_: Exception) {
                    // Keep the local reference available if no browser is installed.
                }
            }, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })
            body.addView(infoCard(
                "ЛИЦЕНЗИЯ И АВТОРСТВО",
                "CleanYourTV · MIT",
                "Списки адаптированы как справочные данные. Оригинальные файлы, история изменений и инструкции доступны по ссылке выше."
            ))
            body.addView(glassButton("Назад к каталогу") { showTvManager() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })
        }
    }

    private fun showCleaner() {
        showPanel("TV Cleaner") { body ->
            body.addView(infoCard(
                "БЕЗОПАСНОЕ УДАЛЕНИЕ",
                "Системные компоненты скрыты",
                "Выбери пользовательское приложение. Android откроет штатное подтверждение удаления."
            ))
            val scroll = ScrollView(this)
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val userApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != packageName && (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase(Locale.getDefault()) }

            userApps.forEach { info ->
                val appName = packageManager.getApplicationLabel(info).toString()
                list.addView(controlTile(appName, info.packageName) {
                    try {
                        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + info.packageName)))
                    } catch (_: Exception) {
                        openSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                    }
                }, LinearLayout.LayoutParams(-1, dp(66)).apply { bottomMargin = dp(7) })
            }
            if (userApps.isEmpty()) {
                list.addView(infoCard("Ничего не найдено", "Пользовательские приложения отсутствуют", ""))
            }
            scroll.addView(list)
            body.addView(scroll, LinearLayout.LayoutParams(-1, dp(300)))
        }
    }

    private fun showPanel(title: String, content: (LinearLayout) -> Unit) {
        page = "panel"
        activePanelTitle = title
        handler.removeCallbacks(clockTask)
        handler.removeCallbacks(carouselTask)

        root = FrameLayout(this).apply {
            setBackgroundColor(RftvDesignSystem.ColorTokens.PANEL_SCRIM)
        }
        val panelBackground = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(RftvDesignSystem.ColorTokens.PANEL_GRADIENT_START, RftvDesignSystem.ColorTokens.PANEL_GRADIENT_END)
            )
        }
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = RftvDesignSystem.panelSurface(this@MainActivity)
            setPadding(dp(RftvDesignSystem.Spacing.XL), dp(RftvDesignSystem.Spacing.LG), dp(RftvDesignSystem.Spacing.XL), dp(RftvDesignSystem.Spacing.LG))
            elevation = dp(24).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = TextView(this).apply {
            text = title
            textSize = RftvDesignSystem.Size.PAGE_TITLE_SP
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        header.addView(heading, LinearLayout.LayoutParams(0, dp(RftvDesignSystem.Size.PANEL_HEADER_HEIGHT), 1f))
        header.addView(glassButton("Закрыть  ✕") { showHome() }, LinearLayout.LayoutParams(-2, dp(RftvDesignSystem.Size.PANEL_CLOSE_HEIGHT)))
        shell.addView(header)

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(RftvDesignSystem.Spacing.PANEL_BODY_TOP), 0, dp(RftvDesignSystem.Spacing.PANEL_BODY_BOTTOM))
        }
        content(body)
        scroll.addView(body)
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(panelBackground, FrameLayout.LayoutParams(-1, -1))
        root.addView(shell, FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * RftvDesignSystem.Size.PANEL_WIDTH_FRACTION).toInt(),
            (resources.displayMetrics.heightPixels * .88f).toInt(),
            Gravity.CENTER
        ))
        setContentView(root)
        // Start on the first actionable item, not the close button. This keeps
        // the panel usable immediately with a TV remote; fall back to Close
        // only for informational panels without focusable controls.
        body.post {
            if (!requestFirstFocusable(body)) header.getChildAt(1)?.requestFocus()
        }
    }

    private fun infoCard(title: String, subtitle: String, detail: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = RftvDesignSystem.cardSurface(this@MainActivity)
            setPadding(dp(RftvDesignSystem.Spacing.CARD_PADDING_HORIZONTAL), dp(RftvDesignSystem.Spacing.CARD_PADDING_VERTICAL), dp(RftvDesignSystem.Spacing.CARD_PADDING_HORIZONTAL), dp(RftvDesignSystem.Spacing.CARD_PADDING_VERTICAL))
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 11f
            letterSpacing = 0.1f
            setTextColor(RftvDesignSystem.ColorTokens.LABEL_ON_GLASS)
        })
        card.addView(TextView(this).apply {
            text = subtitle
            textSize = RftvDesignSystem.Size.CARD_TITLE_SP
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(5), 0, 0)
        })
        if (detail.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = detail
                textSize = 13f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(5), 0, 0)
            })
        }
        return card.apply {
            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = dp(10)
            layoutParams = params
        }
    }

    private fun sectionLabel(label: String): TextView =
        TextView(this).apply {
            text = label
            textSize = RftvDesignSystem.Size.SECTION_TITLE_SP
            setTextColor(Color.WHITE)
            setPadding(dp(2), dp(14), 0, dp(2))
        }

    private fun settingSlider(minimum: Int, maximum: Int, current: Int, changed: (Int) -> Unit): SeekBar {
        return SeekBar(this).apply {
            max = maximum - minimum
            progress = current - minimum
            isFocusable = true
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) changed(minimum + progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun controlTile(title: String, subtitle: String, action: () -> Unit): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = RftvDesignSystem.surface(this@MainActivity, Color.argb(38, 255, 255, 255), RftvDesignSystem.Radius.CONTROL)
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(16), dp(9), dp(16), dp(9))
            setOnClickListener { action() }
            setOnFocusChangeListener { view, focused ->
                view.background = RftvDesignSystem.focusSurface(this@MainActivity, focused, RftvDesignSystem.Radius.CONTROL, 38, 112)
                RftvDesignSystem.applyFocusScale(view, focused, 1.025f)
            }
        }
        tile.addView(TextView(this).apply {
            text = title
            textSize = RftvDesignSystem.Size.CONTROL_TITLE_SP
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            maxLines = 1
        })
        tile.addView(TextView(this).apply {
            text = subtitle
            textSize = RftvDesignSystem.Size.CONTROL_SUBTITLE_SP
            setTextColor(Color.LTGRAY)
            maxLines = 1
        })
        return tile
    }

    private fun glassButton(label: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = RftvDesignSystem.Size.BUTTON_TEXT_SP
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = RftvDesignSystem.surface(this@MainActivity, Color.argb(45, 255, 255, 255), RftvDesignSystem.Radius.BUTTON)
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(18), 0, dp(18), 0)
            setOnClickListener { action() }
            setOnFocusChangeListener { view, focused ->
                view.background = RftvDesignSystem.focusSurface(this@MainActivity, focused, RftvDesignSystem.Radius.BUTTON, 45, 120)
                RftvDesignSystem.applyFocusScale(view, focused, 1.04f)
            }
        }
    }

    private fun weightedParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(82), 1f)

    private fun readMemory(): Pair<String, String> {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        return Pair(formatBytes(memory.totalMem), formatBytes(memory.availMem))
    }

    private fun formatBytes(bytes: Long): String =
        String.format(Locale.getDefault(), "%.1f ГБ", bytes / (1024.0 * 1024.0 * 1024.0))

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
                // Some vendor firmware restricts settings entry points.
            }
        }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            try {
                startActivity(intent)
            } catch (_: Exception) {
                // App may be disabled by the vendor firmware.
            }
        }
    }

    private fun glassPanel(alpha: Int, radius: Int): GradientDrawable =
        RftvDesignSystem.surface(this, Color.argb(alpha, 255, 255, 255), radius)

    private fun roundedGlass(color: Int, radius: Int): GradientDrawable =
        RftvDesignSystem.surface(this, color, radius)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
