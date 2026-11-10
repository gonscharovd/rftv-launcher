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
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import android.widget.GridLayout
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.BitmapDrawable
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
import kotlin.math.min
import rikka.shizuku.Shizuku

/**
 * RFTV Launcher visual prototype.
 * Target device: Android 9 / API 28 / ARMv7 / low-memory Android TV.
 *
 * Liquid Glass uses a cached, downsampled backdrop snapshot blur on legacy Android.
 * Blur is generated asynchronously when an overlay opens, never on every frame.
 */
class MainActivity : Activity() {

    private data class TvApp(
        val label: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable
    )

    private data class OverlayPerfMetrics(
        val overlay: String,
        val openedAt: Long,
        val openCount: Int,
        val javaHeapBeforeBytes: Long,
        val nativeHeapBeforeBytes: Long,
        val diagnosticRecord: OverlayDiagnosticRecord,
        var captureMs: Long = 0L,
        var buildMs: Long = 0L,
        var layoutMs: Long = 0L,
        var screenshotWidthPx: Int = 0,
        var screenshotHeightPx: Int = 0,
        var screenshotBitmapBytes: Int = 0
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var dock: FrameLayout
    private lateinit var dockContent: LinearLayout
    private var dockTooltip: TextView? = null
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var homeWeatherText: TextView
    private lateinit var wallpaperView: ImageView
    private var lastAppliedWallpaperKey: String? = null
    @Volatile private var tvManagerShellService: ITvManagerShellService? = null
    @Volatile private var tvManagerShellUid: Int = -1
    private var tvManagerFocusPackage: String? = null
    private var tvManagerInventoryGeneration: Int = 0
    private val tvManagerShizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == TvManagerShizukuBridge.REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    bindTvManagerShizukuService()
                } else {
                    Toast.makeText(this, "Доступ Shizuku не предоставлен", Toast.LENGTH_LONG).show()
                    refreshTvManagerScreen()
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
                refreshTvManagerScreen()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            tvManagerShellService = null
            tvManagerShellUid = -1
            handler.post {
                refreshTvManagerScreen()
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val wallpaperExecutor = Executors.newSingleThreadExecutor()
    private val glassBlurExecutor = Executors.newSingleThreadExecutor()
    private val appIconExecutor = Executors.newFixedThreadPool(2)
    private val appIconCache = object : LruCache<String, Bitmap>(4 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }
    private var glassBackdropBitmap: Bitmap? = null
    private var overlayBlurBitmap: Bitmap? = null
    private var overlayBlurGeneration = 0
    private var catalogFocusedPackage: String? = null
    private var catalogLaunchPending = false
    private var returnFocusToCatalogButton = false
    private var glassBackdropCacheKey: String? = null
    private var glassBackdropLoadingKey: String? = null
    private var activeGlassBackdropView: ImageView? = null
    private var activeGlassBackdropKey: String? = null
    private var homeHeroBackdropView: ImageView? = null
    private var homeDockBackdropView: ImageView? = null
    private var dockGlassBackdropBitmap: Bitmap? = null
    private var dockBackdropGeneration = 0
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
            changedPackage?.let { appIconCache.remove(it) }
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
    private val wallpaperFallbackAttempted = ConcurrentHashMap.newKeySet<String>()
    private val installedApps = mutableListOf<TvApp>()
    private var controlCenterReturnFocus: View? = null
    private var page = "home"
    private var activePanelTitle = ""
    private var settingsNavigationActive = false
    private var settingsHubVisible = false
    private var settingsSelectedIndex = 0
    private var settingsSidebarView: View? = null
    private var settingsContentColumnView: View? = null
    private var settingsEntryFocusView: View? = null
    private val settingsNavViews = mutableListOf<View>()
    private var packageReceiverRegistered = false
    private var themeMode = 2 // 0 light, 1 dark, 2 automatic
    private var wallpaperLight = 0
    private var wallpaperDark = 0
    private var lightCarousel = false
    private var darkCarousel = false
    private var carouselIntervalMinutes = 5
    private var dockWidthPercent = 88
    private var dockTileCount = 3
    private var dockHeightDp = 74
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

    private val homeWeatherTask = object : Runnable {
        override fun run() {
            if (page != "home" || isFinishing) return
            refreshHomeWeather()
            handler.postDelayed(this, 15 * 60_000L)
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
        dockHeightDp = prefs.getInt("dock_height", 74).coerceIn(70, 110)
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
        if (catalogLaunchPending) {
            catalogLaunchPending = false
            if (page == "panel" && activePanelTitle == "Каталог приложений") {
                returnFocusToCatalogButton = true
                showHome()
                return
            }
        }
        if (page == "home") {
            startClock()
            resetIdleScreensaverTimer()
            handler.removeCallbacks(homeWeatherTask)
            handler.postDelayed(homeWeatherTask, 15 * 60_000L)
        }
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
        handler.removeCallbacks(homeWeatherTask)
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
        appIconExecutor.shutdownNow()
        glassBlurExecutor.shutdownNow()
        overlayBlurBitmap?.takeIf { !it.isRecycled }?.recycle()
        overlayBlurBitmap = null
        glassBackdropBitmap?.recycle()
        glassBackdropBitmap = null
        dockBackdropGeneration++
        dockGlassBackdropBitmap?.takeIf { !it.isRecycled }?.recycle()
        dockGlassBackdropBitmap = null
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
                    else {
                        if (activePanelTitle == "Каталог приложений") returnFocusToCatalogButton = true
                        showHome()
                    }
                    return true
                }
            }

            if (page == "panel" && settingsNavigationActive && settingsHubVisible) {
                val focused = currentFocus
                if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                    focused != null && settingsSidebarView?.let { isDescendantOf(focused, it) } == true
                ) {
                    if (settingsEntryFocusView?.requestFocus() != true) {
                        settingsNavViews.getOrNull(settingsSelectedIndex)?.requestFocus()
                    }
                    return true
                }
                if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                    focused != null && settingsContentColumnView?.let { isDescendantOf(focused, it) } == true
                ) {
                    settingsNavViews.getOrNull(settingsSelectedIndex)?.requestFocus()
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
                // Dock is a single row; vertical navigation no longer expands or collapses it.
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
        wallpaperFallbackAttempted.remove(selectionKey)
        // A neutral fallback is intentionally quiet; the packaged photograph is the
        // actual Home surface, not a decorative blue gradient pretending to be wallpaper.
        wallpaperView.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if (dark) intArrayOf(0xFF080B11.toInt(), 0xFF141923.toInt(), 0xFF090C12.toInt())
            else intArrayOf(0xFF20252D.toInt(), 0xFF303943.toInt(), 0xFF171C23.toInt())
        )
        loadPhoto(index, dark, wallpaperView)
    }

    private fun loadPhoto(index: Int, dark: Boolean, target: ImageView, thumbnail: Boolean = false) {
        val photos = if (dark) darkPhotos else lightPhotos
        val safeIndex = index.coerceIn(0, photos.lastIndex)
        val photo = photos[safeIndex]
        val tag = WallpaperImageUrls.cacheKey(dark, safeIndex, thumbnail)
        if (target.tag == tag && target.drawable != null) return
        // Keep the currently visible wallpaper until the next cached/downloaded
        // image is ready. This avoids a blank frame when the user changes photos.
        target.tag = tag

        // Failed requests are not restarted by the 30-second clock refresh.
        if (tag in wallpaperFailedKeys) return
        val requestKey = "$tag@${System.identityHashCode(target)}"
        if (!wallpaperLoadingKeys.add(requestKey)) return

        val directory = File(filesDir, "wallpapers").apply { mkdirs() }
        val fullFile = File(directory, WallpaperImageUrls.cacheFileName(dark, safeIndex, false))
        val previewFile = File(directory, WallpaperImageUrls.cacheFileName(dark, safeIndex, true))
        val fullUrl = photo.imageUrl
        val previewUrl = WallpaperImageUrls.thumbnailUrl(photo.imageUrl)

        fun decode(file: File, sampleSize: Int): Bitmap? {
            if (!file.exists() || file.length() <= 10_000L) return null
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = sampleSize
            }
            return BitmapFactory.decodeFile(file.absolutePath, options)
        }

        fun fetch(file: File, address: String, maxBytes: Long, sampleSize: Int): Bitmap? {
            decode(file, sampleSize)?.let { return it }
            if (file.exists()) file.delete()

            var connection: HttpURLConnection? = null
            var tempFile: File? = null
            try {
                connection = (URL(address).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    instanceFollowRedirects = true
                    useCaches = true
                    setRequestProperty("User-Agent", "RFTV-Launcher/0.7")
                    setRequestProperty("Accept", "image/jpeg,image/*;q=0.8,*/*;q=0.5")
                }
                connection.connect()
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Wallpaper HTTP ${connection.responseCode}")
                }
                val contentType = connection.contentType.orEmpty()
                if (contentType.isNotBlank() && !contentType.startsWith("image/", ignoreCase = true)) {
                    throw IllegalStateException("Wallpaper returned non-image content: $contentType")
                }
                val part = File(directory, file.name + ".part")
                tempFile = part
                connection.inputStream.use { input ->
                    FileOutputStream(part).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > maxBytes) throw IllegalStateException("Wallpaper exceeds size limit")
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (part.length() <= 10_000L) throw IllegalStateException("Wallpaper response is empty")
                if (file.exists()) file.delete()
                if (!part.renameTo(file)) throw IllegalStateException("Could not commit wallpaper cache")
                return decode(file, sampleSize) ?: run {
                    file.delete()
                    throw IllegalStateException("Wallpaper bitmap decode failed: $address")
                }
            } finally {
                tempFile?.takeIf { it.exists() }?.delete()
                connection?.disconnect()
            }
        }

        fun finish(bitmap: Bitmap?, completed: Boolean, failure: Boolean = false) {
            handler.post {
                if (completed) wallpaperLoadingKeys.remove(requestKey)
                if (target.tag != tag) {
                    bitmap?.recycle()
                    return@post
                }
                if (bitmap != null) {
                    wallpaperFailedKeys.remove(tag)
                    prefs.edit().remove("wallpaper_last_error").apply()
                    if (target === wallpaperView && page == "home") {
                        val selectedIndex = if (dark) wallpaperDark else wallpaperLight
                        val selectedKey = WallpaperImageUrls.selectionKey(dark, selectedIndex)
                        if (safeIndex != selectedIndex && wallpaperFallbackAttempted.contains(selectedKey)) {
                            if (dark) wallpaperDark = safeIndex else wallpaperLight = safeIndex
                            prefs.edit().putInt(if (dark) "wallpaper_dark" else "wallpaper_light", safeIndex).apply()
                            lastAppliedWallpaperKey = WallpaperImageUrls.selectionKey(dark, safeIndex)
                            activeGlassBackdropKey = lastAppliedWallpaperKey
                            wallpaperFallbackAttempted.remove(selectedKey)
                            android.util.Log.i("RFTVWallpaper", "Fallback wallpaper selected and persisted: ${lastAppliedWallpaperKey}")
                        }
                    }
                    if (!thumbnail && target === wallpaperView && target.drawable == null) {
                        target.alpha = 0f
                        target.setImageBitmap(bitmap)
                        target.animate().alpha(1f)
                            .setDuration(RftvDesignSystem.Motion.HOME_FADE_DURATION_MS)
                            .start()
                    } else {
                        target.setImageBitmap(bitmap)
                    }
                    target.contentDescription = "${photo.title} — ${photo.author}"
                    if (target === wallpaperView) refreshGlassBackdrop()
                } else if (failure) {
                    wallpaperFailedKeys.add(tag)
                    target.contentDescription = "Не удалось загрузить обои: ${photo.title}"
                    if (target === wallpaperView && page == "home") {
                        val selectedIndex = if (dark) wallpaperDark else wallpaperLight
                        val selectedKey = WallpaperImageUrls.selectionKey(dark, selectedIndex)
                        if (wallpaperFallbackAttempted.add(selectedKey)) {
                            android.util.Log.w("RFTVWallpaper", "Primary wallpaper failed; trying next photo. selected=$selectedKey")
                            loadPhoto((safeIndex + 1) % photos.size, dark, target)
                        } else {
                            android.util.Log.e("RFTVWallpaper", "Primary and fallback wallpapers failed. selected=$selectedKey")
                            Toast.makeText(this, "Не удалось загрузить фотографии обоев. Ошибка сохранена в журнале.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        wallpaperExecutor.execute {
            var previewShown = false
            try {
                if (thumbnail) {
                    // Derive picker thumbnails from the bundled full image. No second
                    // network request and no blank thumbnails on an offline TV.
                    val cachedThumbSource = decode(fullFile, 4)
                    if (cachedThumbSource != null) {
                        finish(cachedThumbSource, completed = true)
                        return@execute
                    }
                    if (restoreBundledWallpaper(fullFile, dark, safeIndex)) {
                        val bundledPreview = decode(fullFile, 4)
                        if (bundledPreview != null) {
                            finish(bundledPreview, completed = true)
                            return@execute
                        }
                    }
                    val preview = fetch(previewFile, previewUrl, 8L * 1024L * 1024L, 1)
                    finish(preview, completed = true, failure = preview == null)
                } else {
                    // The full-resolution photograph is packaged in the APK and copied
                    // into the same cache used by the carousel and screensaver.
                    val cachedFull = decode(fullFile, 2)
                    if (cachedFull != null) {
                        finish(cachedFull, completed = true)
                        return@execute
                    }
                    if (restoreBundledWallpaper(fullFile, dark, safeIndex)) {
                        val bundled = decode(fullFile, 2)
                        if (bundled != null) {
                            finish(bundled, completed = true)
                            return@execute
                        }
                    }

                    val cachedPreview = decode(previewFile, 1)
                    if (cachedPreview != null) {
                        previewShown = true
                        finish(cachedPreview, completed = false)
                    }
                    val full = try {
                        fetch(fullFile, fullUrl, 24L * 1024L * 1024L, 2)
                    } catch (error: Exception) {
                        android.util.Log.w("RFTVWallpaper", "Full wallpaper fallback failed for ${photo.imageUrl}", error)
                        prefs.edit().putString("wallpaper_last_error", error.message ?: error.javaClass.simpleName).apply()
                        null
                    }
                    finish(full, completed = true, failure = full == null && !previewShown)
                }
            } catch (error: Exception) {
                android.util.Log.w("RFTVWallpaper", "Wallpaper load failed for ${photo.imageUrl}", error)
                prefs.edit().putString("wallpaper_last_error", error.message ?: error.javaClass.simpleName).apply()
                finish(null, completed = true, failure = !previewShown)
            }
        }
    }

    /**
     * Restores a packaged photograph to the normal on-device wallpaper cache.
     * The build downloads and verifies the 20 real Unsplash photos once; playback
     * on the TV is then offline-safe and no longer depends on the TV's DNS/TLS path.
     */
    private fun restoreBundledWallpaper(file: File, dark: Boolean, index: Int): Boolean {
        val part = File(file.parentFile, file.name + ".asset.part")
        return try {
            assets.open(WallpaperImageUrls.assetPath(dark, index)).use { input ->
                FileOutputStream(part).use { output -> input.copyTo(output) }
            }
            if (part.length() <= 10_000L) throw IllegalStateException("Bundled wallpaper asset is empty")
            if (file.exists()) file.delete()
            if (!part.renameTo(file)) throw IllegalStateException("Could not restore bundled wallpaper")
            true
        } catch (error: Exception) {
            part.delete()
            android.util.Log.w("RFTVWallpaper", "Bundled wallpaper unavailable: ${WallpaperImageUrls.assetPath(dark, index)}", error)
            false
        }
    }

    private fun retryCurrentWallpaper() {
        if (!::wallpaperView.isInitialized || page != "home") return
        val dark = isDarkTheme()
        val index = if (dark) wallpaperDark else wallpaperLight
        val key = "${if (dark) "night" else "day"}-${index.coerceIn(0, 9)}"
        wallpaperFailedKeys.remove(key)
        wallpaperView.tag = null
        // Keep the last good image visible while retrying the selected wallpaper.
        lastAppliedWallpaperKey = null
        Toast.makeText(this, "Повторная загрузка обоев…", Toast.LENGTH_SHORT).show()
        applyWallpaper()
    }

    private fun showHome() {
        activeGlassBackdropView = null
        activeGlassBackdropKey = null
        homeHeroBackdropView = null
        homeDockBackdropView = null
        dockBackdropGeneration++
        dockGlassBackdropBitmap?.takeIf { !it.isRecycled }?.recycle()
        dockGlassBackdropBitmap = null
        dockTooltip = null
        settingsNavigationActive = false
        settingsHubVisible = false
        settingsSelectedIndex = 0
        page = "home"
        activePanelTitle = ""
        releaseOverlayBackdrop()
        root = FrameLayout(this).apply {
            setBackgroundColor(RftvDesignSystem.ColorTokens.HOME_BACKDROP)
            clipChildren = false
            clipToPadding = false
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

        // A restrained edge gradient keeps white tvOS-style typography readable without
        // tinting the photograph into a flat blue background.
        root.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x3A000000, 0x08000000, 0x18000000, 0x92000000.toInt())
            )
            isFocusable = false
        }, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(46), dp(28), dp(46), 0)
            clipChildren = false
            clipToPadding = false
        }

        val weatherIcon = RftvControlIconView(this, "weather")
        val weatherPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = RftvDesignSystem.surface(this@MainActivity, 0x7A171B22, 26, 48)
            setPadding(dp(18), dp(10), dp(22), dp(10))
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            contentDescription = "Погода в Челябинске. OK — прогноз"
            setOnClickListener { showWeather() }
            setOnFocusChangeListener { view, focused ->
                view.background = RftvDesignSystem.surface(
                    this@MainActivity,
                    if (focused) 0xC8FFFFFF.toInt() else 0x7A171B22,
                    26,
                    if (focused) 220 else 48
                )
                homeWeatherText.setTextColor(if (focused) 0xFF17191F.toInt() else Color.WHITE)
                weatherIcon.setTint(if (focused) 0xFF17191F.toInt() else Color.WHITE)
                RftvDesignSystem.applyFocusScale(view, focused, 1.035f)
            }
        }
        homeWeatherText = TextView(this).apply {
            text = "Челябинск\nПолучаем погоду…"
            textSize = RftvDesignSystem.Size.HOME_WEATHER_TEXT_SP
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 2
            setShadowLayer(4f, 0f, 1f, 0x66000000)
            isFocusable = false
        }
        weatherPill.addView(weatherIcon, LinearLayout.LayoutParams(dp(30), dp(30)).apply {
            rightMargin = dp(12)
        })
        weatherPill.addView(homeWeatherText, LinearLayout.LayoutParams(-2, -2))
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(weatherPill, LinearLayout.LayoutParams(-2, dp(66)).apply {
            rightMargin = dp(12)
        })
        top.addView(glassButton("⚙") { showControlCenter() }.apply {
            tag = "rftv-control-center-trigger"
        }, LinearLayout.LayoutParams(dp(62), dp(62)))
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // The clock is the hero. No enclosing card: the photograph remains the visual surface.
        val timeColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = false
        }
        clockText = TextView(this).apply {
            text = "09:41"
            textSize = RftvDesignSystem.Size.CLOCK_TEXT_SP
            letterSpacing = -0.065f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setShadowLayer(24f, 0f, 4f, 0x99000000.toInt())
        }
        dateText = TextView(this).apply {
            textSize = RftvDesignSystem.Size.DATE_TEXT_SP
            letterSpacing = 0.025f
            setTextColor(0xE8FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
            setShadowLayer(8f, 0f, 2f, 0x99000000.toInt())
        }
        timeColumn.addView(clockText, LinearLayout.LayoutParams(-2, -2))
        timeColumn.addView(dateText, LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = dp(4)
        })
        val heroFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = false
            isClickable = false
        }
        heroFrame.addView(timeColumn, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        root.addView(heroFrame, FrameLayout.LayoutParams(-1, dp(390), Gravity.CENTER))

        dock = FrameLayout(this).apply {
            background = RftvDesignSystem.dockSurface(this@MainActivity)
            clipChildren = false
            clipToPadding = false
            clipToOutline = true
            elevation = dp(20).toFloat()
        }
        homeDockBackdropView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            isFocusable = false
            contentDescription = "Размытое стекло Dock"
        }
        dock.addView(homeDockBackdropView, FrameLayout.LayoutParams(-1, -1))
        dock.addView(View(this).apply {
            setBackgroundColor(Color.argb(76, 8, 13, 22))
            isFocusable = false
        }, FrameLayout.LayoutParams(-1, -1))
        dockContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }
        buildDockContent()
        dock.addView(dockContent, FrameLayout.LayoutParams(-1, dp(RftvDesignSystem.Size.DOCK_TILE_SIZE), Gravity.CENTER))

        val dockParams = FrameLayout.LayoutParams(
            calculateDockWidthPx(dockTileCount),
            dp(dockHeightDp),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = dp(RftvDesignSystem.Spacing.DOCK_BOTTOM_INSET)
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        root.addView(dock, dockParams)
        dock.post { refreshDockGlassBackdrop() }
        dockTooltip = TextView(this).apply {
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = dp(340)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = DockTooltipDrawable(this@MainActivity, 0f)
            elevation = dp(26).toFloat()
            visibility = View.INVISIBLE
            alpha = 0f
            isFocusable = false
        }
        root.addView(dockTooltip, FrameLayout.LayoutParams(dp(340), dp(31), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(dockHeightDp + RftvDesignSystem.Spacing.DOCK_BOTTOM_INSET + 5)
        })

        setContentView(root)
        startClock()
        refreshHomeWeather()
        handler.removeCallbacks(homeWeatherTask)
        handler.postDelayed(homeWeatherTask, 15 * 60_000L)
        if (returnFocusToCatalogButton) {
            returnFocusToCatalogButton = false
            val allApps = root.findViewWithTag<View>(CatalogReturnPolicy.ALL_APPS_DOCK_TAG)
            if (allApps?.requestFocus() != true) focusFirstApp()
        } else {
            focusFirstApp()
        }
    }

    private fun startClock() {
        handler.removeCallbacks(clockTask)
        handler.removeCallbacks(carouselTask)
        clockTask.run()
        handler.postDelayed(carouselTask, carouselIntervalMinutes * 60_000L)
    }

    private fun calculateDockWidthPx(tileCount: Int): Int {
        val density = resources.displayMetrics.density
        val screenWidthDp = (resources.displayMetrics.widthPixels / density).toInt()
        val widthDp = DockWidthPolicy.widthDp(
            tileCount = tileCount,
            screenWidthDp = screenWidthDp,
            maxWidthPercent = dockWidthPercent
        )
        return dp(widthDp)
    }

    private fun updateDockWidth(tileCount: Int) {
        if (!::dock.isInitialized) return
        val params = dock.layoutParams as? FrameLayout.LayoutParams ?: return
        val width = calculateDockWidthPx(tileCount)
        if (params.width != width) {
            params.width = width
            dock.layoutParams = params
            dock.post { refreshDockGlassBackdrop() }
        }
    }

    private fun buildDockContent() {
        dockContent.removeAllViews()
        val visibleApps = installedApps.filter { it.packageName !in hiddenPackages }
        val favorites = visibleApps.filter { it.packageName in favoritePackages }
        // Keep RFTV's favorites ordering and the dedicated All Apps entry.
        val firstRow = favorites.ifEmpty { visibleApps }
        dockTileCount = firstRow.size + 1
        dockContent.addView(buildAppRow(firstRow), LinearLayout.LayoutParams(-1, dp(RftvDesignSystem.Size.DOCK_TILE_SIZE)))
        dock.post { updateDockWidth(dockTileCount) }
    }

    private fun buildAppRow(items: List<TvApp>): HorizontalScrollView {
        val tileSize = dp(RftvDesignSystem.Size.DOCK_TILE_SIZE)
        val iconSize = dp(RftvDesignSystem.Size.DOCK_ICON)
        val tileGap = dp(RftvDesignSystem.Spacing.DOCK_TILE_GAP)
        val edgeInset = dp(RftvDesignSystem.Spacing.DOCK_EDGE_INSET)
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipChildren = false
            clipToPadding = false
            setPadding(edgeInset, 0, edgeInset, 0)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }

        fun addTile(label: String, packageName: String?, drawable: android.graphics.drawable.Drawable?, glyph: String? = null) {
            val tile = FrameLayout(this).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                contentDescription = label
                tag = packageName ?: CatalogReturnPolicy.ALL_APPS_DOCK_TAG
                background = RftvDesignSystem.dockTileSurface(this@MainActivity, focused = false)
                clipChildren = false
                clipToPadding = false
                clipToOutline = true
                setOnFocusChangeListener { view, focused ->
                    view.background = RftvDesignSystem.dockTileSurface(this@MainActivity, focused)
                    view.animate().scaleX(if (focused) RftvDesignSystem.Size.DOCK_FOCUS_SCALE else 1f)
                        .scaleY(if (focused) RftvDesignSystem.Size.DOCK_FOCUS_SCALE else 1f)
                        .setDuration(RftvDesignSystem.Motion.DOCK_FOCUS_DURATION_MS).start()
                    view.elevation = if (focused) dp(RftvDesignSystem.Elevation.FOCUSED_TILE_DP).toFloat() else 0f
                    updateDockTooltip(label, focused, view)
                }
                setOnClickListener { packageName?.let(::launchApp) ?: showAppCatalog() }
            }
            if (glyph != null) {
                tile.addView(TextView(this).apply {
                    text = glyph
                    textSize = 25f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                }, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
            } else {
                tile.addView(ImageView(this).apply {
                    setImageDrawable(drawable)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }, FrameLayout.LayoutParams(tileSize, tileSize, Gravity.CENTER))
            }
            row.addView(tile, LinearLayout.LayoutParams(tileSize, tileSize).apply {
                leftMargin = tileGap / 2
                rightMargin = tileGap / 2
            })
        }

        items.forEach { app -> addTile(app.label, app.packageName, app.icon) }
        addTile("Все приложения", null, null, "▦")
        scroll.addView(row, ViewGroup.LayoutParams(-2, tileSize))
        return scroll
    }

    private fun updateDockTooltip(label: String, visible: Boolean, anchor: View? = null) {
        val tooltip = dockTooltip ?: return
        tooltip.animate().cancel()
        if (visible) {
            tooltip.text = label
            tooltip.background = DockTooltipDrawable(this, tooltip.paint.measureText(label))
            tooltip.visibility = View.VISIBLE
            tooltip.alpha = 1f
            tooltip.post {
                if (tooltip.visibility != View.VISIBLE || anchor?.isShown != true) return@post
                val rootLocation = IntArray(2)
                val anchorLocation = IntArray(2)
                root.getLocationOnScreen(rootLocation)
                anchor.getLocationOnScreen(anchorLocation)
                val centerX = anchorLocation[0] - rootLocation[0] + anchor.width / 2f
                val halfTooltip = tooltip.width / 2f
                val minCenter = halfTooltip + dp(8)
                val maxCenter = root.width - halfTooltip - dp(8)
                tooltip.translationX = centerX.coerceIn(minCenter, maxCenter) - root.width / 2f
            }
        } else {
            tooltip.animate().alpha(0f).setDuration(90L).withEndAction {
                if (tooltip.alpha == 0f) tooltip.visibility = View.INVISIBLE
            }.start()
        }
    }

    private fun focusFirstApp() {
        if (!::dockContent.isInitialized) return
        val rowScroll = dockContent.getChildAt(0) as? HorizontalScrollView ?: return
        val row = rowScroll.getChildAt(0) as? LinearLayout ?: return
        row.getChildAt(0)?.requestFocus()
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

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "Ясно"
        1 -> "Преимущественно ясно"
        2 -> "Переменная облачность"
        3 -> "Облачно"
        45, 48 -> "Туман"
        in 51..57 -> "Морось"
        in 61..67 -> "Дождь"
        in 71..77 -> "Снег"
        in 80..82 -> "Ливень"
        in 85..86 -> "Снегопад"
        95, 96, 99 -> "Гроза"
        else -> "Погодные условия"
    }

    private fun weatherEndpoint(): String =
        "https://api.open-meteo.com/v1/forecast?latitude=55.1599&longitude=61.4026" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=5&timezone=Asia%2FYekaterinburg"

    private fun refreshHomeWeather() {
        if (!::homeWeatherText.isInitialized) return
        val cached = RftvWeatherRepository.cached(this)
        homeWeatherText.text = cached?.summary ?: "Челябинск\nПолучаем погоду…"
        networkExecutor.execute {
            val result = try {
                RftvWeatherRepository.load(this@MainActivity, force = false)
            } catch (error: Exception) {
                android.util.Log.w("RFTVWeather", "All weather providers failed", error)
                null
            }
            handler.post {
                if (!isFinishing && page == "home" && ::homeWeatherText.isInitialized) {
                    homeWeatherText.text = result?.summary ?: cached?.summary
                        ?: "Челябинск\nПогода временно недоступна"
                }
            }
        }
    }

    private fun showWeather() {
        showPanel("Погода · прогноз на 5 дней") { body ->
            val currentCard = TextView(this).apply {
                text = "Челябинск · получаем текущие условия…"
                textSize = 25f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(Color.WHITE)
                setPadding(dp(26), dp(22), dp(26), dp(22))
                background = RftvDesignSystem.cardSurface(this@MainActivity)
                gravity = Gravity.CENTER_VERTICAL
            }
            body.addView(currentCard, LinearLayout.LayoutParams(-1, -2))
            body.addView(sectionLabel("ПРОГНОЗ НА БЛИЖАЙШИЕ ДНИ"))
            val daysContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            body.addView(daysContainer, LinearLayout.LayoutParams(-1, -2))

            fun render(snapshot: RftvWeatherRepository.Snapshot) {
                currentCard.text = buildString {
                    append("${snapshot.temperature}° · ")
                    append(RftvWeatherRepository.weatherDescription(snapshot.code))
                    append("\n${snapshot.place}")
                    append("\nОщущается ${snapshot.feelsLike}° · ветер ${snapshot.wind} ${snapshot.windUnit}")
                    if (snapshot.humidity > 0) append(" · влажность ${snapshot.humidity}%")
                }
                daysContainer.removeAllViews()
                snapshot.days.forEachIndexed { index, day ->
                    daysContainer.addView(infoCard(
                        if (index == 0) "СЕГОДНЯ" else "ДЕНЬ ${index + 1}",
                        day.label,
                        "${RftvWeatherRepository.weatherDescription(day.code)} · ${day.minimum}° … ${day.maximum}°"
                    ))
                }
                val updated = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(snapshot.fetchedAt))
                daysContainer.addView(infoCard("ИСТОЧНИК ДАННЫХ", snapshot.provider, "Обновлено в $updated"))
            }

            val cached = RftvWeatherRepository.cached(this)
            if (cached != null) {
                render(cached)
            } else {
                daysContainer.addView(infoCard("ПРОГНОЗ", "Загрузка…", "Проверяем несколько независимых погодных источников"))
            }

            networkExecutor.execute {
                val result = try {
                    RftvWeatherRepository.load(this@MainActivity, force = false)
                } catch (error: Exception) {
                    android.util.Log.w("RFTVWeather", "Forecast refresh failed", error)
                    null
                }
                handler.post {
                    if (isFinishing || page != "panel" || activePanelTitle != "Погода · прогноз на 5 дней") return@post
                    if (result != null) {
                        render(result)
                    } else if (cached == null) {
                        daysContainer.removeAllViews()
                        daysContainer.addView(infoCard(
                            "НЕТ СВЯЗИ",
                            "Прогноз не получен",
                            "Не удалось подключиться к Open-Meteo, MET Norway или wttr.in. Повторная попытка будет выполнена автоматически."
                        ))
                    }
                }
            }
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
        val existingOverlay = root.findViewWithTag<View>("rftv-control-center")
        if (existingOverlay != null) root.removeView(existingOverlay)
        val perf = beginOverlayPerf("control_center")
        val backdropSnapshot = captureBackdropSnapshot(perf)
        releaseOverlayBackdrop()
        val buildStartedAt = SystemClock.elapsedRealtime()
        if (existingOverlay == null) {
            controlCenterReturnFocus = currentFocus
                ?.takeIf { it.tag != "rftv-control-center-trigger" }
                ?: root.findViewWithTag("rftv-control-center-trigger")
        }

        val overlay = FrameLayout(this).apply {
            tag = "rftv-control-center"
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = false
            isFocusableInTouchMode = false
        }
        addGlassBackdrop(overlay, backdropSnapshot, perf)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = RftvDesignSystem.gradientSurface(
                this@MainActivity,
                intArrayOf(RftvDesignSystem.ColorTokens.CONTROL_CENTER_GRADIENT_START,
                    RftvDesignSystem.ColorTokens.CONTROL_CENTER_GRADIENT_MID,
                    RftvDesignSystem.ColorTokens.CONTROL_CENTER_GRADIENT_END),
                RftvDesignSystem.Radius.CONTROL_CENTER_PANEL,
                RftvDesignSystem.ColorTokens.CONTROL_CENTER_STROKE
            )
            setPadding(0, 0, 0, 0)
            elevation = dp(RftvDesignSystem.Elevation.CONTROL_CENTER_DP).toFloat()
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        val header = TextView(this).apply {
            text = "Пункт управления"
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(RftvDesignSystem.ColorTokens.TEXT_PRIMARY)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), dp(8))
        }
        card.addView(header, LinearLayout.LayoutParams(-1, dp(42)))
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

        fun makeTile(icon: String, title: String, subtitle: String, metricPercent: Int? = null, action: () -> Unit): LinearLayout {
            val iconView = RftvControlIconView(this@MainActivity, icon)
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(12), 0)
                background = RftvDesignSystem.gradientSurface(
                    this@MainActivity,
                    intArrayOf(RftvDesignSystem.ColorTokens.CONTROL_CENTER_TILE_START,
                        RftvDesignSystem.ColorTokens.CONTROL_CENTER_TILE_END),
                    RftvDesignSystem.Radius.CONTROL_CENTER_TILE,
                    RftvDesignSystem.ColorTokens.CONTROL_CENTER_STROKE
                )
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                contentDescription = "$title. $subtitle"
                setOnFocusChangeListener { view, focused ->
                    view.background = RftvDesignSystem.gradientSurface(
                        this@MainActivity,
                        if (focused) intArrayOf(RftvDesignSystem.ColorTokens.CONTROL_CENTER_TILE_FOCUSED_START,
                            RftvDesignSystem.ColorTokens.CONTROL_CENTER_TILE_FOCUSED_END)
                        else intArrayOf(RftvDesignSystem.ColorTokens.CONTROL_CENTER_TILE_START,
                            RftvDesignSystem.ColorTokens.CONTROL_CENTER_TILE_END),
                        RftvDesignSystem.Radius.CONTROL_CENTER_TILE,
                        if (focused) RftvDesignSystem.ColorTokens.TEXT_PRIMARY
                        else RftvDesignSystem.ColorTokens.CONTROL_CENTER_STROKE,
                        if (focused) 2 else 1
                    )
                    iconView.setTint(if (focused) RftvDesignSystem.ColorTokens.CONTROL_CENTER_FOCUSED_TEXT
                        else RftvDesignSystem.ColorTokens.CONTROL_CENTER_ICON)
                    val labels = (view as LinearLayout).getChildAt(1) as LinearLayout
                    for (i in 0 until labels.childCount) {
                        val child = labels.getChildAt(i)
                        if (child is TextView) child.setTextColor(
                            if (focused) RftvDesignSystem.ColorTokens.CONTROL_CENTER_FOCUSED_TEXT
                            else if (child.tag == "metric") Color.WHITE
                            else RftvDesignSystem.ColorTokens.TEXT_PRIMARY
                        )
                    }
                    RftvDesignSystem.applyFocusScale(view, focused, 1.05f)
                }
                setOnClickListener { action() }
            }
            tile.addView(iconView, LinearLayout.LayoutParams(dp(25), dp(25)).apply { rightMargin = dp(10) })
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            labels.addView(TextView(this).apply {
                text = title
                textSize = 12.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(RftvDesignSystem.ColorTokens.TEXT_PRIMARY)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                tag = if (metricPercent != null) "metric" else null
            })
            labels.addView(TextView(this).apply {
                text = subtitle
                textSize = 10.5f
                setTextColor(RftvDesignSystem.ColorTokens.CONTROL_CENTER_SUBTITLE)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (metricPercent != null) {
                val progress = android.widget.ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = metricPercent.coerceIn(0, 100)
                    progressTintList = android.content.res.ColorStateList.valueOf(0xFF63B7FF.toInt())
                    progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x447C8CA2)
                }
                labels.addView(progress, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(4) })
            }
            tile.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            return tile
        }

        val wifi = makeTile("wifi", "Wi-Fi", "Сеть и подключение") { openSettings(Settings.ACTION_WIFI_SETTINGS) }
        val bluetooth = makeTile("bluetooth", "Bluetooth", "Устройства") { openBluetoothPairing() }
        val themeLabels = arrayOf("Светлая", "Тёмная", "Авто")
        val theme = makeTile("theme", "Тема", themeLabels[themeMode.coerceIn(0, 2)]) {
            themeMode = (themeMode + 1) % 3
            prefs.edit().putInt("theme_mode", themeMode).apply()
            applyWallpaper()
            showControlCenter()
        }
        val ramInfo = readRamUsage()
        val ram = makeTile("memory", "RAM · ${ramInfo.first}%", ramInfo.second, ramInfo.first) {
            System.runFinalization()
            Runtime.getRuntime().gc()
            handler.postDelayed({
                if (root.findViewWithTag<View>("rftv-control-center") != null) showControlCenter()
            }, 500)
        }
        val saver = makeTile("clock", "Заставка", "Погода · курсы · новости") { showScreensaver() }
        val launcherSettings = makeTile("settings", "Лаунчер", "Настройки RFTV") { showLauncherSettings() }
        val tvSettings = makeTile("tv", "Настройки ТВ", "Системные параметры") { openSettings(Settings.ACTION_SETTINGS) }

        fun addFull(tile: View) {
            content.addView(tile, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(7) })
        }
        fun addPair(left: View, right: View) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(left, LinearLayout.LayoutParams(0, dp(56), 1f).apply { rightMargin = dp(4) })
            row.addView(right, LinearLayout.LayoutParams(0, dp(56), 1f).apply { leftMargin = dp(4) })
            content.addView(row, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(7) })
        }
        addFull(wifi)
        addFull(bluetooth)
        addPair(theme, ram)
        addPair(saver, launcherSettings)
        addFull(tvSettings)

        val focusRows = listOf(listOf(wifi), listOf(bluetooth), listOf(theme, ram),
            listOf(saver, launcherSettings), listOf(tvSettings))
        focusRows.flatten().forEach { it.id = View.generateViewId() }
        focusRows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { columnIndex, view ->
                val upperRow = focusRows.getOrNull(rowIndex - 1)
                val lowerRow = focusRows.getOrNull(rowIndex + 1)
                view.nextFocusLeftId = row.getOrNull(columnIndex - 1)?.id ?: view.id
                view.nextFocusRightId = row.getOrNull(columnIndex + 1)?.id ?: view.id
                view.nextFocusUpId = upperRow?.getOrNull(columnIndex.coerceAtMost((upperRow.size - 1).coerceAtLeast(0)))?.id ?: view.id
                view.nextFocusDownId = lowerRow?.getOrNull(columnIndex.coerceAtMost((lowerRow.size - 1).coerceAtLeast(0)))?.id ?: view.id
            }
        }

        scroll.addView(content)
        card.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        card.alpha = 0f
        card.translationX = dp(18).toFloat()
        val controlCenterTop = dp(36)
        val dockClearance = dp(dockHeightDp + RftvDesignSystem.Spacing.DOCK_BOTTOM_INSET + 22)
        val controlCenterHeight = (resources.displayMetrics.heightPixels - controlCenterTop - dockClearance).coerceAtLeast(dp(320))
        overlay.addView(card, FrameLayout.LayoutParams(
            dp(RftvDesignSystem.Size.CONTROL_CENTER_PANEL_WIDTH),
            controlCenterHeight, Gravity.TOP or Gravity.RIGHT
        ).apply { rightMargin = dp(28); topMargin = controlCenterTop })
        perf.buildMs = SystemClock.elapsedRealtime() - buildStartedAt
        val layoutStartedAt = SystemClock.elapsedRealtime()
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        recordOverlayLayout(perf, overlay, layoutStartedAt)
        overlay.post { logOverlayOpen(perf) }
        card.animate().alpha(1f).translationX(0f)
            .setDuration(RftvDesignSystem.Motion.CONTROL_CENTER_REVEAL_DURATION_MS).start()
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
        releaseOverlayBackdrop()
        val previous = controlCenterReturnFocus
        controlCenterReturnFocus = null
        if (previous != null && previous.isShown && previous.isFocusable && previous.requestFocus()) return
        root.findViewWithTag<View>("rftv-control-center-trigger")?.requestFocus()
    }


    private fun showDiagnosticsScreen() {
        fun render() {
            showPanel("Диагностика") { body ->
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val versionCode = if (Build.VERSION.SDK_INT >= 28) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }
                val memoryClass = (getSystemService(ACTIVITY_SERVICE) as ActivityManager).memoryClass
                val abi = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { Build.CPU_ABI }
                body.addView(infoCard(
                    "УСТРОЙСТВО",
                    "RFTV ${packageInfo.versionName} · ${versionCode}",
                    "Модель: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nABI: $abi\nMemory class: $memoryClass MB"
                ))
                body.addView(sectionLabel("ПОСЛЕДНИЕ 10 ОТКРЫТИЙ"))
                val records = OverlayDiagnosticsStore.latest()
                if (records.isEmpty()) {
                    body.addView(infoCard("ПОКА НЕТ ДАННЫХ", "Открой каталог или Control Center", "После открытия здесь появятся замеры времени, bitmap и использования памяти."))
                } else {
                    records.forEachIndexed { index, record ->
                        val title = if (record.overlay == "app_catalog") "Каталог приложений" else "Control Center"
                        val blur = if (record.blurMs >= 0) "${record.blurMs} мс" else "ожидается"
                        val card = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            background = RftvDesignSystem.settingsNavigationSurface(this@MainActivity, false, false)
                            setPadding(dp(18), dp(14), dp(18), dp(14))
                            isFocusable = false
                        }
                        card.addView(TextView(this).apply {
                            text = "#${record.openCount} · $title · ${if (record.temperature == "cold") "холодное" else "повторное"}"
                            textSize = 22f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(Color.WHITE)
                        })
                        val details = listOf(
                            "capture: ${record.captureMs} мс · показ: ${record.openToVisibleMs} мс · blur: $blur",
                            "Bitmap: исходный ${record.sourceBitmapBytes} Б · размытый ${record.blurredBitmapBytes} Б",
                            "Java heap: ${record.javaHeapBeforeBytes} → ${record.javaHeapAfterBytes} Б",
                            "Native heap: ${record.nativeHeapBeforeBytes} → ${record.nativeHeapAfterBytes} Б",
                            if (record.blurMs >= 0) "Blur: ${if (record.blurSucceeded) "готов" else "fallback"}" else "Blur: обработка ещё не завершена"
                        )
                        card.addView(TextView(this).apply {
                            text = details.joinToString("\n")
                            textSize = 18f
                            setTextColor(0xFFE4EAF2.toInt())
                            setPadding(0, dp(8), 0, 0)
                        })
                        body.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                            bottomMargin = dp(10)
                        })
                    }
                }
                body.addView(glassButton("Очистить историю диагностики") {
                    OverlayDiagnosticsStore.clear()
                    render()
                }, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(10) })
                body.addView(glassButton("Назад к настройкам") { showLauncherSettings() },
                    LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })
            }
        }
        render()
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
        // Android 9 can return signingInfo=null for APK archives even when the
        // archive is correctly signed. Request both modern and legacy metadata.
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        } else {
            PackageManager.GET_SIGNATURES
        }
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

        @Suppress("DEPRECATION")
        fun certificateDigests(info: android.content.pm.PackageInfo, source: String): Set<String> {
            // Prefer SigningInfo, but fall back to PackageInfo.signatures. Some
            // Android 9 PackageParser paths populate only the legacy field.
            val modern = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else null
            val signatures = modern?.takeIf { it.isNotEmpty() }
                ?: info.signatures?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("Не удалось прочитать сертификат $source (SigningInfo и signatures пусты)")
            if (signatures.isEmpty()) throw IllegalStateException("$source не имеет сертификата подписи")
            return signatures.map {
                java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }.toSet()
        }
        if (certificateDigests(candidate, "скачанного APK") != certificateDigests(installed, "установленного приложения")) {
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
        // Hide Control Center before capture so the Settings backdrop contains the Home scene,
        // not a screenshot of the panel that is being replaced.
        root.findViewWithTag<View>("rftv-control-center")?.visibility = View.GONE
        val settingsBackdropSnapshot = captureBackdropSnapshot()
        releaseOverlayBackdrop()
        settingsNavigationActive = true
        settingsHubVisible = true
        page = "panel"
        activePanelTitle = "Настройки RFTV"
        handler.removeCallbacks(clockTask)
        handler.removeCallbacks(carouselTask)
        root = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        addGlassBackdrop(root, settingsBackdropSnapshot)
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = RftvDesignSystem.settingsShellSurface(this@MainActivity)
            setPadding(dp(44), dp(28), dp(48), dp(22))
            elevation = dp(RftvDesignSystem.Elevation.PANEL_DP).toFloat()
        }
        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = RftvDesignSystem.settingsSidebarSurface(this@MainActivity)
            setPadding(0, 0, 0, 0)
        }
        sidebar.addView(TextView(this).apply {
            text = "RFTV"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); setPadding(dp(10), dp(4), dp(10), dp(2))
        })
        sidebar.addView(TextView(this).apply {
            text = "НАСТРОЙКИ"; textSize = 10.5f; letterSpacing = .14f
            setTextColor(RftvDesignSystem.ColorTokens.TEXT_SECONDARY); setPadding(dp(10), 0, dp(10), dp(18))
        })
        // The navigation list must scroll independently on compact TV panels;
        // otherwise the last sections can be clipped on high-density 720p TVs.
        val navigationScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        val navigationList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(8))
            clipChildren = false
            clipToPadding = false
        }
        navigationScroll.addView(navigationList)
        sidebar.addView(navigationScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        settingsSidebarView = sidebar
        val contentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        settingsContentColumnView = contentColumn
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "Настройки"; textSize = 20f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val settingsCloseButton = glassButton("✕") { showHome() }.apply {
            id = View.generateViewId()
            contentDescription = "Закрыть настройки"
        }
        header.addView(settingsCloseButton, LinearLayout.LayoutParams(dp(54), dp(54)))
        contentColumn.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(16); topMargin = dp(14); bottomMargin = dp(12)
        })
        val detailScroll = ScrollView(this).apply { clipToPadding = false }
        val detail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(28))
        }
        detailScroll.addView(detail)
        contentColumn.addView(detailScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val settingsHintFooter = TextView(this).apply {
            textSize = 10.5f
            setTextColor(0xB8FFFFFF.toInt())
            background = RftvDesignSystem.settingsHintSurface(this@MainActivity)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(14), dp(7), dp(14), dp(7))
            isFocusable = false
        }
        contentColumn.addView(settingsHintFooter, LinearLayout.LayoutParams(-1, dp(34)))
        val sections = listOf(
            Triple("Обзор", "Быстрый доступ к настройкам", { showLauncherSettings() }),
            Triple("Внешний вид", "Обои и визуальное оформление", { showAppearanceSettings() }),
            Triple("Dock", "Размер и пропорции панели", { showDockSettings() }),
            Triple("Заставка", "Таймер и экран ожидания", { showScreensaverSettings() }),
            Triple("Приложения", "Каталог, избранное и скрытые", { showAppSettings() }),
            Triple("TV Manager", "Устройство и анализ пакетов", { showTvManager() }),
            Triple("Диагностика", "Показатели открытия каталога и Control Center", { showDiagnosticsScreen() }),
            Triple("О системе", "Версия и OTA-обновления", { showAboutSystem() })
        )
        val navItems = mutableListOf<LinearLayout>()
        settingsNavViews.clear()
        settingsEntryFocusView = null
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
                val selected = index == selectedIndex
                item.background = RftvDesignSystem.settingsNavigationSurface(this, focused, selected)
                val iconBox = item.getChildAt(0) as FrameLayout
                val icon = iconBox.getChildAt(0) as TextView
                val label = item.getChildAt(1) as TextView
                iconBox.background = RftvDesignSystem.settingsNavIconSurface(this, focused)
                icon.setTextColor(if (focused) 0xFF111114.toInt() else if (selected) Color.WHITE else 0xD9FFFFFF.toInt())
                label.setTextColor(if (focused) 0xFF111114.toInt() else if (selected) Color.WHITE else 0xC2FFFFFF.toInt())
            }
        }
        fun renderSection(index: Int) {
            selectedIndex = index
            settingsSelectedIndex = index
            settingsHintFooter.text = when (index) {
                0 -> "Вверх/вниз — обзор · ОК — открыть · Назад — закрыть"
                1 -> "Вверх/вниз — выбор обоев · ОК — применить · Назад — настройки"
                2 -> "Вверх/вниз — параметры Dock · ОК — изменить · Назад — настройки"
                3 -> "Вверх/вниз — интервал · ОК — выбрать · Назад — настройки"
                4 -> "Вверх/вниз — приложения · ОК — открыть · Назад — настройки"
                5 -> "Вверх/вниз — разделы ТВ · ОК — открыть · Назад — настройки"
                6 -> "ОК — открыть диагностику · Назад — настройки"
                else -> "ОК — открыть сведения и OTA · Назад — настройки"
            }
            refreshNavigation()
            detail.removeAllViews()
            val section = sections[index]
            val label = when (index) {
                0 -> "ЦЕНТР НАСТРОЕК"; 1 -> "ВНЕШНИЙ ВИД"; 2 -> "ПАНЕЛЬ ПРИЛОЖЕНИЙ"
                3 -> "ЭКРАН ОЖИДАНИЯ"; 4 -> "УПРАВЛЕНИЕ ПРИЛОЖЕНИЯМИ"
                5 -> "ДИАГНОСТИКА УСТРОЙСТВА"; 6 -> "ДИАГНОСТИКА RFTV"; else -> "СИСТЕМА И ОБНОВЛЕНИЯ"
            }
            detail.addView(infoCard(label, section.first, section.second))
            when (index) {
                0 -> {
                    detail.addView(sectionLabel("БЫСТРЫЙ ДОСТУП"))
                    for (target in 1..7) {
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
                    detail.addView(settingSlider(70, 110, dockHeightDp) { value ->
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
                6 -> {
                    detail.addView(controlTile("Открыть диагностику", "Последние 10 открытий · память и скорость") {
                        settingsHubVisible = false
                        showDiagnosticsScreen()
                    }, LinearLayout.LayoutParams(-1, dp(76)))
                }
                else -> {
                    detail.addView(controlTile("О системе и обновления", "Версия RFTV · скачать и установить OTA") {
                        settingsHubVisible = false
                        showAboutSystem()
                    }, LinearLayout.LayoutParams(-1, dp(76)))
                }
            }
            val firstRight = findFirstFocusable(detail)
            settingsEntryFocusView = firstRight
            if (firstRight != null) {
                if (firstRight.id == View.NO_ID) firstRight.id = View.generateViewId()
                navItems.forEach { it.nextFocusRightId = firstRight.id }
                firstRight.nextFocusLeftId = navItems.getOrNull(index)?.id ?: View.NO_ID
                firstRight.nextFocusUpId = settingsCloseButton.id
                settingsCloseButton.nextFocusDownId = firstRight.id
            }
        }
        sections.forEachIndexed { index, section ->
            val item = LinearLayout(this).apply {
                id = View.generateViewId()
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                minimumHeight = dp(35)
                setPadding(dp(7), dp(4), dp(14), dp(4))
                background = RftvDesignSystem.settingsNavigationSurface(this@MainActivity, focused = false, selected = false)
                val iconBox = FrameLayout(this@MainActivity).apply {
                    background = RftvDesignSystem.settingsNavIconSurface(this@MainActivity, focused = false)
                }
                iconBox.addView(TextView(this@MainActivity).apply {
                    text = SettingsNavigationSpec.glyphFor(index)
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setTextColor(0xD9FFFFFF.toInt())
                }, FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER))
                addView(iconBox, LinearLayout.LayoutParams(dp(27), dp(27)).apply { rightMargin = dp(11) })
                addView(TextView(this@MainActivity).apply {
                    text = section.first
                    textSize = 14.5f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(0xC2FFFFFF.toInt())
                    maxLines = 1
                }, LinearLayout.LayoutParams(-2, -2))
                setOnFocusChangeListener { view, focused ->
                    refreshNavigation()
                    RftvDesignSystem.applyFocusScale(view, focused, 1.02f)
                }
                setOnClickListener { renderSection(index) }
            }
            navItems += item
            settingsNavViews += item
            navigationList.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
        }
        shell.addView(sidebar, LinearLayout.LayoutParams(dp(288), -1).apply { rightMargin = dp(26) })
        shell.addView(contentColumn, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        renderSection(settingsSelectedIndex)
        sidebar.post { navItems.getOrNull(settingsSelectedIndex)?.requestFocus() }
    }

    private fun showAppearanceSettings() {
        showPanel("Внешний вид и обои") { body ->
            body.addView(infoCard(
                "ОФОРМЛЕНИЕ RFTV",
                "tvOS Glass",
                "Полноэкранная фотографическая сцена, тёмное стекло, деликатные блики, пространственная глубина и выразительный фокус."
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
            body.addView(settingSlider(70, 110, dockHeightDp) { value ->
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
                return@showPanel
            }

            val screenWidth = resources.displayMetrics.widthPixels
            val panelWidth = (screenWidth * RftvDesignSystem.Size.PANEL_WIDTH_FRACTION).toInt()
            val contentWidth = (panelWidth - dp(64)).coerceAtLeast(dp(420))
            val columns = (contentWidth / dp(178)).coerceIn(3, 6)
            val cellWidth = contentWidth / columns
            val grid = GridLayout(this).apply {
                columnCount = columns
                orientation = GridLayout.HORIZONTAL
                useDefaultMargins = false
                alignmentMode = GridLayout.ALIGN_BOUNDS
                setPadding(dp(4), dp(8), dp(4), dp(12))
                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            }
            val tiles = mutableListOf<View>()
            visibleApps.forEachIndexed { index, app ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isClickable = true
                    tag = app.packageName
                    contentDescription = app.label
                    background = RftvDesignSystem.dockTileSurface(this@MainActivity, focused = false)
                    setPadding(dp(8), dp(10), dp(8), dp(10))
                    setOnFocusChangeListener { view, focused ->
                        view.background = RftvDesignSystem.dockTileSurface(this@MainActivity, focused)
                        RftvDesignSystem.applyFocusScale(view, focused, 1.045f)
                        if (focused) catalogFocusedPackage = app.packageName
                    }
                    setOnClickListener { launchApp(app.packageName) }
                    setOnLongClickListener { showAppActions(app); true }
                }
                val icon = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = app.label
                    isFocusable = false
                }
                val label = TextView(this).apply {
                    text = app.label
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(8), 0, 0)
                }
                tile.addView(icon, LinearLayout.LayoutParams(dp(76), dp(76)))
                tile.addView(label, LinearLayout.LayoutParams(-1, dp(42)))
                val id = View.generateViewId()
                tile.id = id
                tiles += tile
                grid.addView(tile, GridLayout.LayoutParams(
                    GridLayout.spec(index / columns),
                    GridLayout.spec(index % columns)
                ).apply {
                    width = cellWidth
                    height = dp(142)
                    setMargins(dp(2), dp(3), dp(2), dp(3))
                })
                loadCatalogIcon(app.packageName, icon)
            }
            tiles.forEachIndexed { index, tile ->
                val column = index % columns
                tile.nextFocusLeftId = tiles.getOrNull(if (column > 0) index - 1 else index)?.id ?: tile.id
                tile.nextFocusRightId = tiles.getOrNull(if (column < columns - 1 && index + 1 < tiles.size) index + 1 else index)?.id ?: tile.id
                tile.nextFocusUpId = tiles.getOrNull(index - columns)?.id ?: tile.id
                tile.nextFocusDownId = tiles.getOrNull(index + columns)?.id ?: tile.id
            }
            body.addView(grid, LinearLayout.LayoutParams(-1, -2))
            body.post {
                val target = tiles.firstOrNull { it.tag == catalogFocusedPackage } ?: tiles.firstOrNull()
                target?.requestFocus()
                if (target != null) {
                    val rect = Rect(0, 0, target.width, target.height)
                    target.requestRectangleOnScreen(rect, false)
                }
            }
        }
    }

    private fun loadCatalogIcon(packageId: String, target: ImageView) {
        val cached = appIconCache.get(packageId)
        if (cached != null && !cached.isRecycled) {
            target.setImageBitmap(cached)
            return
        }
        appIconExecutor.execute {
            val bitmap = try {
                val drawable = packageManager.getApplicationIcon(packageId)
                val size = dp(96)
                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { output ->
                    val canvas = Canvas(output)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(canvas)
                }
            } catch (_: Exception) {
                null
            }
            if (bitmap != null) {
                appIconCache.put(packageId, bitmap)
                handler.post {
                    if (!bitmap.isRecycled && target.parent != null) target.setImageBitmap(bitmap)
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
        params.width = calculateDockWidthPx(dockTileCount)
        params.height = dp(dockHeightDp)
        dock.layoutParams = params
        dockTooltip?.let { tooltip ->
            (tooltip.layoutParams as? FrameLayout.LayoutParams)?.let { tooltipParams ->
                tooltipParams.bottomMargin = dp(dockHeightDp + RftvDesignSystem.Spacing.DOCK_BOTTOM_INSET + 5)
                tooltip.layoutParams = tooltipParams
            }
        }
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
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName }
        val systemApps = installedApps.filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
        val userApps = installedApps.filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
        showPanel("Данные по ТВ") { body ->
            body.addView(infoCard("УСТРОЙСТВО", "${Build.MANUFACTURER} ${Build.MODEL}",
                "Brand: ${Build.BRAND}\nDevice: ${Build.DEVICE}\nProduct: ${Build.PRODUCT}\nBoard: ${Build.BOARD}\nHardware: ${Build.HARDWARE}"))
            body.addView(infoCard("СИСТЕМА", "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
                "Прошивка: ${Build.DISPLAY}\nBuild ID: ${Build.ID}\nFingerprint: ${Build.FINGERPRINT}"))
            body.addView(infoCard("ПРОЦЕССОР", Build.SUPPORTED_ABIS.joinToString(),
                "Ядра CPU: ${Runtime.getRuntime().availableProcessors()}"))
            body.addView(glassButton("Экспортировать fingerprint устройства") {
                val fingerprint = listOf(
                    "manufacturer=${Build.MANUFACTURER}",
                    "model=${Build.MODEL}",
                    "brand=${Build.BRAND}",
                    "board=${Build.BOARD}",
                    "hardware=${Build.HARDWARE}",
                    "device=${Build.DEVICE}",
                    "product=${Build.PRODUCT}",
                    "android_release=${Build.VERSION.RELEASE}",
                    "sdk=${Build.VERSION.SDK_INT}",
                    "build_id=${Build.ID}",
                    "display=${Build.DISPLAY}",
                    "build_fingerprint=${Build.FINGERPRINT}",
                    "supported_abis=${Build.SUPPORTED_ABIS.joinToString(",")}"
                ).joinToString("\n")
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "RFTV — fingerprint устройства")
                    putExtra(Intent.EXTRA_TEXT, fingerprint)
                }
                startActivity(Intent.createChooser(send, "Экспорт fingerprint"))
            }, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })
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
        data class PackageRow(
            val app: ApplicationInfo,
            val label: String,
            val isSystem: Boolean,
            val disabled: Boolean,
            val isProtected: Boolean,
            val riskLabel: String,
            val riskColor: Int
        )

        val generation = ++tvManagerInventoryGeneration
        val matched = TvManagerProfiles.matchingProfiles()
        fun risk(packageId: String, protected: Boolean): Pair<String, Int> {
            if (protected || PackageAnalysisPolicy.isProtectedPackage(packageId)) {
                return Pair("КРИТИЧЕСКИЙ", RftvDesignSystem.ColorTokens.RISK_CRITICAL)
            }
            val known = matched.flatMap { it.packages }.firstOrNull { it.packageId == packageId }
            return when (known?.risk) {
                "Критический", "Высокий" -> Pair("ВЫСОКИЙ РИСК", RftvDesignSystem.ColorTokens.RISK_CRITICAL)
                else -> Pair("НЕ ПРОВЕРЕН", RftvDesignSystem.ColorTokens.RISK_REVIEW)
            }
        }
        showPanel("Установленные пакеты") { body ->
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
            var rows: List<PackageRow> = emptyList()
            var loaded = false
            var scanError: String? = null
            fun render(query: String) {
                list.removeAllViews()
                if (!loaded) {
                    list.addView(infoCard("СКАНИРОВАНИЕ", "Читаем полный список пакетов", "Операция выполняется в фоне, интерфейс остаётся доступен."))
                    return
                }
                if (scanError != null) {
                    list.addView(infoCard("ОШИБКА СКАНИРОВАНИЯ", "Список пакетов недоступен", scanError ?: "PackageManager недоступен"))
                    return
                }
                val filtered = rows.filter {
                    it.label.contains(query, true) || it.app.packageName.contains(query, true)
                }
                list.addView(infoCard("ПАКЕТЫ", "${filtered.size} из ${rows.size}",
                    "Нажми OK на карточке для подробностей. Список полный, без ограничения по количеству."))
                if (filtered.isEmpty()) list.addView(infoCard("Ничего не найдено", "Измени запрос", ""))
                filtered.forEach { item ->
                    val card = controlTile(
                        item.label,
                        "${item.app.packageName} · ${if (item.isSystem) "Системное" else "Пользовательское"} · ${if (item.disabled) "Отключено" else "Включено"} · ${item.riskLabel} · ${if (item.isProtected) "Изменение заблокировано" else "Требуется оценка"} · ОК — подробности"
                    ) {
                        showTvPackageDetails(item.app)
                    }
                    card.background = roundedGlass(
                        Color.argb(
                            RftvDesignSystem.GlassAlpha.RISK_CARD,
                            item.riskColor.red(), item.riskColor.green(), item.riskColor.blue()
                        ),
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
                if (!loaded || scanError != null) {
                    Toast.makeText(this, scanError ?: "Список пакетов ещё загружается.", Toast.LENGTH_LONG).show()
                } else {
                    val report = rows.joinToString("\n") { item ->
                        "${item.app.packageName}\t${item.label}\t${if (item.isSystem) "system" else "user"}\t${if (item.disabled) "disabled" else "enabled"}"
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "RFTV — список пакетов")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    startActivity(Intent.createChooser(send, "Экспорт пакетов"))
                }
            }, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(10) })
            body.addView(glassButton("Назад к данным ТВ") { showTvDeviceInfo() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })

            tvManagerExecutor.execute {
                try {
                    val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                        .filter { it.packageName != packageName }
                    val loadedRows = apps.map { app ->
                        val label = try { packageManager.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName }
                        val preview = tvManagerController.preview(
                            app.packageName,
                            PackageOperationPolicy.Mode.MANUAL,
                            hasActiveSnapshot = false
                        )
                        val protected = preview?.isProtected == true
                        val (riskLabel, riskColor) = risk(app.packageName, protected)
                        val setting = preview?.enabledSetting ?: try {
                            packageManager.getApplicationEnabledSetting(app.packageName)
                        } catch (_: Exception) { PackageManager.COMPONENT_ENABLED_STATE_DEFAULT }
                        val disabled = setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                            setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                            setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED ||
                            (setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && !app.enabled)
                        PackageRow(
                            app,
                            label,
                            app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                            disabled,
                            protected,
                            riskLabel,
                            riskColor
                        )
                    }.sortedBy { it.label.lowercase(Locale.getDefault()) }
                    runOnUiThread {
                        if (generation != tvManagerInventoryGeneration ||
                            page != "panel" || activePanelTitle != "Установленные пакеты") return@runOnUiThread
                        rows = loadedRows
                        loaded = true
                        render(search.text?.toString().orEmpty())
                    }
                } catch (error: Exception) {
                    runOnUiThread {
                        if (generation != tvManagerInventoryGeneration ||
                            page != "panel" || activePanelTitle != "Установленные пакеты") return@runOnUiThread
                        scanError = error.message ?: "PackageManager недоступен"
                        loaded = true
                        render(search.text?.toString().orEmpty())
                    }
                }
            }
        }
    }

    private fun showTvPackageDetails(app: ApplicationInfo, returnToManual: Boolean = false) {
        val packageInfo = try {
            packageManager.getPackageInfo(
                app.packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or PackageManager.GET_ACTIVITIES
            )
        } catch (_: Exception) {
            if (returnToManual) showTvManualCleanup() else showTvPackages()
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
                sharedUserId = packageInfo.sharedUserId,
                isPersistent = app.flags and ApplicationInfo.FLAG_PERSISTENT != 0,
                providesTvInputService = try {
                    packageManager.queryIntentServices(
                        Intent("android.media.tv.TvInputService"),
                        PackageManager.MATCH_DISABLED_COMPONENTS
                    ).any { it.serviceInfo?.packageName == app.packageName }
                } catch (_: Exception) { false },
                profilePurpose = matchedProfileEntry?.note,
                profileRisk = matchedProfileEntry?.risk,
                isProtected = tvManagerController.preview(
                    app.packageName, PackageOperationPolicy.Mode.MANUAL, hasActiveSnapshot = false
                )?.isProtected == true
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
                "КОМПОНЕНТЫ",
                "Служб: ${packageInfo.services?.size ?: 0} · Приёмников: ${packageInfo.receivers?.size ?: 0} · Activity: ${packageInfo.activities?.size ?: 0}",
                "Это декларации из PackageManager. Они не подтверждают, что компонент сейчас запущен или использует разрешения."
            ))
            body.addView(infoCard(
                "ЗАВИСИМОСТИ",
                "Полный граф не подтверждён",
                "PackageManager на этой версии Android не предоставляет надёжный полный список зависимостей между приложениями и OEM-службами. Перед ручным отключением считай зависимости неизвестными; поэтому показывается отдельное предупреждение."
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
            body.addView(glassButton(if (returnToManual) "Назад к ручной очистке" else "Назад к списку пакетов") {
                if (returnToManual) {
                    tvManagerFocusPackage = app.packageName
                    showTvManualCleanup()
                } else {
                    showTvPackages()
                }
            }, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(10) })
        }
    }

    private fun Int.red(): Int = android.graphics.Color.red(this)
    private fun Int.green(): Int = android.graphics.Color.green(this)
    private fun Int.blue(): Int = android.graphics.Color.blue(this)

    private fun refreshTvManagerScreen() {
        if (page != "panel") return
        when {
            activePanelTitle == "ADB и разрешения" -> showTvAdb()
            activePanelTitle == "Очистка ТВ" -> showTvCleanup()
            activePanelTitle.startsWith("Ручная очистка") -> showTvManualCleanup()
            activePanelTitle == "Автоматическая очистка" -> showTvAutomaticCleanup()
            activePanelTitle == "Отключённые пакеты" -> showTvDisabledPackages()
            activePanelTitle == "Журнал операций" -> showTvOperationLog()
        }
    }

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
                        "${candidate.profileNote ?: "Нет подтверждённого описания"}\nРиск профиля: ${candidate.profileRisk ?: "Не оценён"} · Уверенность: ${candidate.confidence}\nЗависимости проверены: ${candidate.dependenciesVerified} · Необязательный пакет: ${candidate.verifiedOptional}\nСистемное приложение: ${candidate.isSystemApp}\n${candidate.decision.reason}"
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
        data class PackageRow(
            val app: ApplicationInfo,
            val label: String,
            val preview: TvManagerPackageController.Preview?,
            val disabled: Boolean,
            val snapshot: TvManagerOperationStore.Snapshot?
        )

        val generation = ++tvManagerInventoryGeneration
        val channelReady = tvManagerShellService != null && (tvManagerShellUid == 0 || tvManagerShellUid == 2000)
        showPanel("Ручная очистка") { body ->
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
            var rows: List<PackageRow> = emptyList()
            var inventoryLoaded = false
            var scanError: String? = null

            fun render(query: String) {
                list.removeAllViews()
                if (!inventoryLoaded) {
                    list.addView(infoCard("СКАНИРОВАНИЕ", "Загружаем список пакетов", "Чтение PackageManager выполняется в фоне, интерфейс остаётся доступен."))
                    return
                }
                if (scanError != null) {
                    list.addView(infoCard("ОШИБКА СКАНИРОВАНИЯ", "Список пакетов недоступен", scanError ?: "Неизвестная ошибка"))
                    return
                }
                val filtered = rows.filter {
                    it.label.contains(query, true) || it.app.packageName.contains(query, true)
                }
                list.addView(infoCard("ПАКЕТЫ", "${filtered.size} из ${rows.size}",
                    "Нажми OK на названии для подробностей. Переключатель меняет только состояние пакета; данные приложения не удаляются."))
                if (filtered.isEmpty()) {
                    list.addView(infoCard("Ничего не найдено", "Измени запрос", ""))
                }
                filtered.forEach { item ->
                    val app = item.app
                    val label = item.label
                    val preview = item.preview
                    val disabled = item.disabled
                    val owned = item.snapshot
                    val protected = preview?.isProtected != false
                    val suspended = preview?.isSuspended == true
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
                        setOnClickListener {
                            tvManagerFocusPackage = app.packageName
                            showTvPackageDetails(app, returnToManual = true)
                        }
                    }
                    val detailView = TextView(this).apply {
                        text = "${app.packageName}\n${if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) "Системное" else "Пользовательское"} · ${when { suspended -> "Приостановлено системой"; disabled -> "Отключено"; else -> "Включено" }} · ${when { protected -> "Защищено"; owned != null -> "Изменено RFTV"; preview?.profileMatched == true -> "Есть справочный профиль"; else -> "Нет проверенного профиля" }} · ${if (preview?.decision?.requiresExplicitWarning == true) "Низкая уверенность / зависимости не проверены" else "Подробнее — OK"}"
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
                        isEnabled = channelReady && (if (disabled) {
                            owned != null
                        } else {
                            !protected && !suspended && owned == null && preview?.decision?.allowed == true
                        })
                        contentDescription = when {
                            owned != null -> "Восстановить ${app.packageName}"
                            disabled -> "Пакет уже отключён до RFTV: ${app.packageName}"
                            protected -> "Защищённый пакет: ${app.packageName}"
                            suspended -> "Пакет приостановлен системой: ${app.packageName}"
                            else -> "Отключить ${app.packageName}"
                        }
                    }
                    toggle.setOnCheckedChangeListener { button, checked ->
                        tvManagerFocusPackage = app.packageName
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
                    if (tvManagerFocusPackage == app.packageName) {
                        titleView.post {
                            titleView.requestFocus()
                            tvManagerFocusPackage = null
                        }
                    }
                    if (owned != null && !disabled && channelReady) {
                        list.addView(glassButton("Проверить и вернуть исходное состояние") {
                            AlertDialog.Builder(this)
                                .setTitle("Завершить незавершённую операцию?")
                                .setMessage("${app.packageName}\nRFTV сохранил исходный snapshot, но текущее состояние не подтверждено. Команда вернёт сохранённое состояние и проверит результат.")
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

            tvManagerExecutor.execute {
                try {
                    val snapshots = tvManagerController.activeSnapshots().associateBy { it.packageName }
                    val loadedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                        .filter { it.packageName != packageName }
                    val loadedRows = loadedApps.map { app ->
                        val label = try { packageManager.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName }
                        val preview = tvManagerController.preview(
                            app.packageName,
                            PackageOperationPolicy.Mode.MANUAL,
                            hasActiveSnapshot = app.packageName in snapshots
                        )
                        val setting = preview?.enabledSetting ?: try {
                            packageManager.getApplicationEnabledSetting(app.packageName)
                        } catch (_: Exception) { PackageManager.COMPONENT_ENABLED_STATE_DEFAULT }
                        val disabled = setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                            setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                            setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED ||
                            (setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && !app.enabled)
                        PackageRow(app, label, preview, disabled, snapshots[app.packageName])
                    }.sortedBy { it.label.lowercase(Locale.getDefault()) }
                    runOnUiThread {
                        if (generation != tvManagerInventoryGeneration ||
                            page != "panel" || !activePanelTitle.startsWith("Ручная очистка")) return@runOnUiThread
                        rows = loadedRows
                        inventoryLoaded = true
                        render(search.text?.toString().orEmpty())
                    }
                } catch (error: Exception) {
                    runOnUiThread {
                        if (generation != tvManagerInventoryGeneration ||
                            page != "panel" || !activePanelTitle.startsWith("Ручная очистка")) return@runOnUiThread
                        scanError = error.message ?: "PackageManager недоступен"
                        inventoryLoaded = true
                        render(search.text?.toString().orEmpty())
                    }
                }
            }
        }
    }

    private fun showTvDisabledPackages() {
        val generation = ++tvManagerInventoryGeneration
        val owned = tvManagerController.activeSnapshots().associateBy { it.packageName }
        showPanel("Отключённые пакеты") { body ->
            body.addView(infoCard("ИЗМЕНЕНИЯ RFTV",
                "${owned.size} сохранённых исходных состояний",
                "Можно восстановить только пакеты, которые RFTV изменил и для которых сохранил snapshot. Восстановление выполняется в обратном порядке; ранее отключённые пользователем пакеты не затрагиваются."))
            if (owned.isNotEmpty()) {
                body.addView(glassButton("Восстановить все изменения RFTV") {
                    AlertDialog.Builder(this)
                        .setTitle("Восстановить все пакеты RFTV?")
                        .setMessage("Будет восстановлено ${owned.size} сохранённых состояний. Пакеты, отключённые до RFTV, не затрагиваются.")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Восстановить все") { _, _ ->
                            tvManagerExecutor.execute {
                                val results = tvManagerController.restoreAll()
                                val success = results.count { it.success }
                                val failed = results.size - success
                                runOnUiThread {
                                    Toast.makeText(
                                        this,
                                        "Восстановлено: $success · ошибок: $failed",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    showTvDisabledPackages()
                                }
                            }
                        }.show()
                }, LinearLayout.LayoutParams(-2, dp(48)).apply { bottomMargin = dp(10) })
            }
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val scroll = ScrollView(this).apply { isFillViewport = true; addView(list) }
            list.addView(infoCard("СКАНИРОВАНИЕ", "Проверяем состояние пакетов", "Чтение PackageManager выполняется в фоне."))
            body.addView(scroll, LinearLayout.LayoutParams(-1, dp(390)))
            body.addView(glassButton("Назад в TV Manager") { showTvManager() },
                LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })

            tvManagerExecutor.execute {
                val disabled = try {
                    packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                        .filter { app ->
                            app.packageName != packageName && isPackageDisabled(app.packageName, app.enabled)
                        }
                        .map { app ->
                            val label = try { packageManager.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName }
                            app to label
                        }
                } catch (_: Exception) {
                    emptyList()
                }
                runOnUiThread {
                    if (generation != tvManagerInventoryGeneration ||
                        page != "panel" || activePanelTitle != "Отключённые пакеты") return@runOnUiThread
                    list.removeAllViews()
                    list.addView(infoCard("НАЙДЕНО ОТКЛЮЧЁННЫХ ПАКЕТОВ",
                        disabled.size.toString(),
                        "Состояние отражает PackageManager. Только пакеты с подтверждённым snapshot RFTV можно восстановить отсюда."))
                    if (disabled.isEmpty()) {
                        list.addView(infoCard("Список пуст", "Отключённых пакетов не найдено", ""))
                    }
                    disabled.forEach { (app, label) ->
                        val snapshot = owned[app.packageName]
                        list.addView(infoCard(label, app.packageName,
                            if (snapshot != null) "Отключено RFTV · исходное состояние: ${snapshot.originalEnabledSetting}"
                            else "Отключено до RFTV или другим способом · восстановление заблокировано."))
                        if (snapshot != null) {
                            list.addView(glassButton("Восстановить исходное состояние") {
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
                }
            }
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
        val perf = if (title == "Каталог приложений") beginOverlayPerf("app_catalog") else null
        val backdropSnapshot = perf?.let { captureBackdropSnapshot(it) }
        releaseOverlayBackdrop()
        val buildStartedAt = if (perf != null) SystemClock.elapsedRealtime() else 0L
        page = "panel"
        activePanelTitle = title
        handler.removeCallbacks(clockTask)
        handler.removeCallbacks(carouselTask)
        handler.removeCallbacks(homeWeatherTask)

        root = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        if (perf != null) addGlassBackdrop(root, backdropSnapshot, perf)
        else addWallpaperGlassBackdrop(root)
        val panelBackground = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(72, 8, 16, 30), Color.argb(116, 8, 16, 30))
            )
        }
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = RftvDesignSystem.panelSurface(this@MainActivity)
            setPadding(dp(RftvDesignSystem.Spacing.XL), dp(RftvDesignSystem.Spacing.LG), dp(RftvDesignSystem.Spacing.XL), dp(RftvDesignSystem.Spacing.LG))
            elevation = dp(RftvDesignSystem.Elevation.PANEL_DP).toFloat()
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
        header.addView(glassButton("Закрыть  ✕") {
            if (title == "Каталог приложений") returnFocusToCatalogButton = true
            showHome()
        }, LinearLayout.LayoutParams(-2, dp(RftvDesignSystem.Size.PANEL_CLOSE_HEIGHT)))
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
            (resources.displayMetrics.heightPixels * RftvDesignSystem.Size.SETTINGS_PANEL_HEIGHT_FRACTION).toInt(),
            Gravity.CENTER
        ))
        if (perf != null) perf.buildMs = SystemClock.elapsedRealtime() - buildStartedAt
        val layoutStartedAt = if (perf != null) SystemClock.elapsedRealtime() else 0L
        setContentView(root)
        if (perf != null) {
            recordOverlayLayout(perf, root, layoutStartedAt)
            root.post { logOverlayOpen(perf) }
        }
        // Start on the first actionable item, not the close button. This keeps
        // the panel usable immediately with a TV remote; fall back to Close
        // only for informational panels without focusable controls.
        body.post {
            if (title == "Каталог приложений") return@post
            if (!requestFirstFocusable(body)) header.getChildAt(1)?.requestFocus()
        }
    }

    private fun isDebugBuild(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun javaHeapUsedBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun beginOverlayPerf(name: String): OverlayPerfMetrics {
        val javaHeapBefore = javaHeapUsedBytes()
        val nativeHeapBefore = android.os.Debug.getNativeHeapAllocatedSize()
        val diagnosticRecord = OverlayDiagnosticsStore.begin(name, javaHeapBefore, nativeHeapBefore)
        val metrics = OverlayPerfMetrics(
            overlay = name,
            openedAt = SystemClock.uptimeMillis(),
            openCount = diagnosticRecord.openCount,
            javaHeapBeforeBytes = javaHeapBefore,
            nativeHeapBeforeBytes = nativeHeapBefore,
            diagnosticRecord = diagnosticRecord
        )
        if (isDebugBuild()) {
            Log.d(
                "RFTV_PERF",
                "event=opening overlay=$name open_count=${metrics.openCount} java_heap_before_bytes=${metrics.javaHeapBeforeBytes} native_heap_before_bytes=${metrics.nativeHeapBeforeBytes}"
            )
        }
        return metrics
    }

    // Draw directly into the <=480px target on the UI thread; never render full-resolution first.
    private fun captureBackdropSnapshot(metrics: OverlayPerfMetrics? = null): Bitmap? {
        if (!::root.isInitialized || root.width <= 0 || root.height <= 0) return null
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            val size = OverlaySnapshotSizing.fit(root.width, root.height)
            val width = size.width
            val height = size.height
            metrics?.screenshotWidthPx = width
            metrics?.screenshotHeightPx = height
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.scale(width / root.width.toFloat(), height / root.height.toFloat())
                root.draw(canvas)
                metrics?.screenshotBitmapBytes = bitmap.allocationByteCount
                metrics?.captureMs = SystemClock.elapsedRealtime() - startedAt
            }
        } catch (_: Throwable) {
            metrics?.captureMs = SystemClock.elapsedRealtime() - startedAt
            null
        }
    }

    private fun addWallpaperGlassBackdrop(container: FrameLayout) {
        val selectionKey = lastAppliedWallpaperKey ?: return
        val backdrop = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Размытый фон Liquid Glass"
            isFocusable = false
        }
        container.addView(backdrop, 0, FrameLayout.LayoutParams(-1, -1))
        container.addView(View(this).apply {
            setBackgroundColor(Color.argb(112, 5, 11, 22))
            isFocusable = false
        }, 1, FrameLayout.LayoutParams(-1, -1))
        activeGlassBackdropView = backdrop
        activeGlassBackdropKey = selectionKey
        refreshGlassBackdrop()
    }

    private fun addGlassBackdrop(container: FrameLayout, snapshot: Bitmap?, metrics: OverlayPerfMetrics? = null) {
        val backdrop = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Размытый снимок экрана Liquid Glass"
            isFocusable = false
            setBackgroundColor(Color.rgb(16, 20, 28))
        }
        container.addView(backdrop, 0, FrameLayout.LayoutParams(-1, -1))
        container.addView(View(this).apply {
            setBackgroundColor(Color.argb(154, 5, 11, 22))
            isFocusable = false
        }, 1, FrameLayout.LayoutParams(-1, -1))
        activeGlassBackdropView = backdrop
        if (snapshot == null) {
            metrics?.let { logBlurResult(it, 0L, 0, 0, false) }
            return
        }

        val generation = ++overlayBlurGeneration
        glassBlurExecutor.execute {
            val downscaledBytes = snapshot.allocationByteCount
            val blurStartedAt = SystemClock.elapsedRealtime()
            val blurred = try {
                // The UI thread already rendered directly into the final low-resolution bitmap.
                GlassBackdrop.createBlurredSnapshot(
                    snapshot, snapshot.width, snapshot.height, radius = 7, passes = 3
                )
            } catch (_: Throwable) {
                null
            } finally {
                if (!snapshot.isRecycled) snapshot.recycle()
            }
            val blurElapsed = SystemClock.elapsedRealtime() - blurStartedAt
            val blurredBytes = blurred?.allocationByteCount ?: 0
            handler.post {
                if (generation != overlayBlurGeneration || isFinishing || activeGlassBackdropView !== backdrop) {
                    blurred?.takeIf { !it.isRecycled }?.recycle()
                    return@post
                }
                if (blurred != null) {
                    overlayBlurBitmap?.takeIf { !it.isRecycled }?.recycle()
                    overlayBlurBitmap = blurred
                    backdrop.setImageBitmap(blurred)
                }
                metrics?.let { logBlurResult(it, blurElapsed, downscaledBytes, blurredBytes, blurred != null) }
            }
        }
    }

    private fun logOverlayOpen(metrics: OverlayPerfMetrics) {
        val openToVisibleMs = SystemClock.uptimeMillis() - metrics.openedAt
        val javaHeapAfter = javaHeapUsedBytes()
        val nativeHeapAfter = android.os.Debug.getNativeHeapAllocatedSize()
        OverlayDiagnosticsStore.opened(
            metrics.diagnosticRecord,
            metrics.captureMs,
            openToVisibleMs,
            metrics.screenshotBitmapBytes,
            javaHeapAfter,
            nativeHeapAfter
        )
        if (!isDebugBuild()) return
        Log.d(
            "RFTV_PERF",
            "event=opened overlay=${metrics.overlay} open_count=${metrics.openCount} open_to_visible_ms=$openToVisibleMs capture_ms=${metrics.captureMs} build_ms=${metrics.buildMs} layout_ms=${metrics.layoutMs} screenshot_width_px=${metrics.screenshotWidthPx} screenshot_height_px=${metrics.screenshotHeightPx} screenshot_bitmap_bytes=${metrics.screenshotBitmapBytes} java_heap_before_bytes=${metrics.javaHeapBeforeBytes} java_heap_after_bytes=$javaHeapAfter native_heap_before_bytes=${metrics.nativeHeapBeforeBytes} native_heap_after_bytes=$nativeHeapAfter"
        )
    }

    private fun recordOverlayLayout(metrics: OverlayPerfMetrics, target: View, startedAt: Long) {
        val observer = target.viewTreeObserver
        if (!observer.isAlive) return
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (target.viewTreeObserver.isAlive) target.viewTreeObserver.removeOnPreDrawListener(this)
                metrics.layoutMs = SystemClock.elapsedRealtime() - startedAt
                if (isDebugBuild()) {
                    Log.d("RFTV_PERF", "event=layout_complete overlay=${metrics.overlay} open_count=${metrics.openCount} build_ms=${metrics.buildMs} layout_ms=${metrics.layoutMs}")
                }
                return true
            }
        }
        observer.addOnPreDrawListener(listener)
    }

    private fun logBlurResult(
        metrics: OverlayPerfMetrics,
        blurMs: Long,
        downscaledBytes: Int,
        blurredBytes: Int,
        success: Boolean
    ) {
        OverlayDiagnosticsStore.blurCompleted(
            metrics.diagnosticRecord, blurMs, blurredBytes, success
        )
        if (!isDebugBuild()) return
        Log.d(
            "RFTV_PERF",
            "event=blur_complete overlay=${metrics.overlay} open_count=${metrics.openCount} blur_ms=$blurMs downscaled_bitmap_bytes=$downscaledBytes blurred_bitmap_bytes=$blurredBytes success=$success"
        )
    }

    private fun releaseOverlayBackdrop() {
        overlayBlurGeneration++
        activeGlassBackdropView?.setImageDrawable(null)
        activeGlassBackdropView = null
        overlayBlurBitmap?.takeIf { !it.isRecycled }?.recycle()
        overlayBlurBitmap = null
    }

    /**
     * Crops the exact wallpaper region behind the Dock, downsamples it before blur,
     * and reuses the same GlassBackdrop radius/passes as the other frosted surfaces.
     */
    private fun refreshDockGlassBackdrop(
        source: Bitmap? = (wallpaperView.drawable as? BitmapDrawable)?.bitmap
    ) {
        val target = homeDockBackdropView ?: return
        if (source == null || source.isRecycled || !::dock.isInitialized) return
        if (dock.width <= 0 || dock.height <= 0 || root.width <= 0 || root.height <= 0) {
            dock.post { refreshDockGlassBackdrop() }
            return
        }
        val dockLocation = IntArray(2)
        val rootLocation = IntArray(2)
        dock.getLocationOnScreen(dockLocation)
        root.getLocationOnScreen(rootLocation)
        val dockLeft = dockLocation[0] - rootLocation[0]
        val dockTop = dockLocation[1] - rootLocation[1]
        val scale = kotlin.math.max(
            root.width / source.width.toFloat(),
            root.height / source.height.toFloat()
        )
        val offsetX = (root.width - source.width * scale) / 2f
        val offsetY = (root.height - source.height * scale) / 2f
        val left = ((dockLeft - offsetX) / scale).toInt().coerceIn(0, source.width - 1)
        val top = ((dockTop - offsetY) / scale).toInt().coerceIn(0, source.height - 1)
        val right = ((dockLeft + dock.width - offsetX) / scale).toInt().coerceIn(left + 1, source.width)
        val bottom = ((dockTop + dock.height - offsetY) / scale).toInt().coerceIn(top + 1, source.height)
        val size = OverlaySnapshotSizing.fit(dock.width, dock.height)
        val crop = try {
            Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                Canvas(bitmap).drawBitmap(
                    source,
                    Rect(left, top, right, bottom),
                    Rect(0, 0, size.width, size.height),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
            }
        } catch (_: Throwable) {
            return
        }
        val generation = ++dockBackdropGeneration
        glassBlurExecutor.execute {
            val blurred = try {
                GlassBackdrop.createBlurredSnapshot(crop, crop.width, crop.height, radius = 7, passes = 3)
            } catch (_: Throwable) {
                null
            } finally {
                if (!crop.isRecycled) crop.recycle()
            }
            handler.post {
                if (generation != dockBackdropGeneration || isFinishing || homeDockBackdropView !== target) {
                    blurred?.takeIf { !it.isRecycled }?.recycle()
                    return@post
                }
                if (blurred != null) {
                    dockGlassBackdropBitmap?.takeIf { it !== blurred && !it.isRecycled }?.recycle()
                    dockGlassBackdropBitmap = blurred
                    target.setImageBitmap(blurred)
                }
            }
        }
    }

    private fun refreshGlassBackdrop() {
        val selectionKey = activeGlassBackdropKey ?: lastAppliedWallpaperKey ?: return
        val overlayBackdrop = activeGlassBackdropView
        val homeBackdrop = homeHeroBackdropView
        if (overlayBackdrop == null && homeBackdrop == null) return
        if (glassBackdropCacheKey == selectionKey && glassBackdropBitmap?.isRecycled == false) {
            overlayBackdrop?.setImageBitmap(glassBackdropBitmap)
            homeBackdrop?.setImageBitmap(glassBackdropBitmap)
            return
        }
        if (glassBackdropLoadingKey == selectionKey) return
        val source = (wallpaperView.drawable as? BitmapDrawable)?.bitmap ?: return
        if (source.isRecycled) return
        refreshDockGlassBackdrop(source)
        glassBackdropLoadingKey = selectionKey
        val width = min(480, (resources.displayMetrics.widthPixels / 2).coerceAtLeast(240))
        val height = (width * 9f / 16f).toInt().coerceAtLeast(135)
        glassBlurExecutor.execute {
            val blurred = try {
                GlassBackdrop.createBlurredSnapshot(source, width, height, radius = 7, passes = 3)
            } catch (_: Exception) {
                null
            }
            handler.post {
                if (glassBackdropLoadingKey == selectionKey) glassBackdropLoadingKey = null
                if (blurred == null) return@post
                if (selectionKey != lastAppliedWallpaperKey || isFinishing) {
                    blurred.recycle()
                    return@post
                }
                glassBackdropBitmap?.takeIf { it !== blurred && !it.isRecycled }?.recycle()
                glassBackdropBitmap = blurred
                glassBackdropCacheKey = selectionKey
                if (activeGlassBackdropKey == selectionKey) {
                    activeGlassBackdropView?.setImageBitmap(blurred)
                }
                homeHeroBackdropView?.setImageBitmap(blurred)
                refreshDockGlassBackdrop(source)
            }
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
            textSize = RftvDesignSystem.Size.CARD_LABEL_SP
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
            background = RftvDesignSystem.interactiveSurface(this@MainActivity, focused = false, RftvDesignSystem.Radius.CONTROL)
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(16), dp(9), dp(16), dp(9))
            setOnClickListener { action() }
            setOnFocusChangeListener { view, focused ->
                view.background = RftvDesignSystem.interactiveSurface(this@MainActivity, focused, RftvDesignSystem.Radius.CONTROL)
                val labels = view as LinearLayout
                for (index in 0 until labels.childCount) {
                    (labels.getChildAt(index) as? TextView)?.setTextColor(
                        if (focused) RftvDesignSystem.ColorTokens.INTERACTIVE_FOCUSED_TEXT
                        else if (index == 0) Color.WHITE else Color.LTGRAY
                    )
                }
                RftvDesignSystem.applyFocusScale(view, focused, 1.035f)
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
            background = RftvDesignSystem.interactiveSurface(this@MainActivity, focused = false, RftvDesignSystem.Radius.BUTTON)
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(18), 0, dp(18), 0)
            setOnClickListener { action() }
            setOnFocusChangeListener { view, focused ->
                view.background = RftvDesignSystem.interactiveSurface(this@MainActivity, focused, RftvDesignSystem.Radius.BUTTON)
                (view as TextView).setTextColor(
                    if (focused) RftvDesignSystem.ColorTokens.INTERACTIVE_FOCUSED_TEXT else Color.WHITE
                )
                RftvDesignSystem.applyFocusScale(view, focused, 1.035f)
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
            val launchedFromCatalog = page == "panel" && activePanelTitle == "Каталог приложений"
            if (launchedFromCatalog) catalogLaunchPending = true
            try {
                startActivity(intent)
            } catch (_: Exception) {
                if (launchedFromCatalog) catalogLaunchPending = false
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
