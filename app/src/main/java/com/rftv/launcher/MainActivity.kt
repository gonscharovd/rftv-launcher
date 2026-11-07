package com.rftv.launcher

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

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

    private val handler = Handler(Looper.getMainLooper())
    private val wallpaperExecutor = Executors.newSingleThreadExecutor()
    private val installedApps = mutableListOf<TvApp>()
    private var dockExpanded = false
    private var page = "home"
    private var themeMode = 2 // 0 light, 1 dark, 2 automatic
    private var wallpaperLight = 0
    private var wallpaperDark = 0
    private var lightCarousel = false
    private var darkCarousel = false
    private var carouselIntervalMinutes = 5
    private var dockWidthPercent = 88
    private var dockHeightDp = 154


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
                applyWallpaper()
            }
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("rftv_preferences", MODE_PRIVATE)
        themeMode = prefs.getInt("theme_mode", 2)
        wallpaperLight = prefs.getInt("wallpaper_light", 0)
        wallpaperDark = prefs.getInt("wallpaper_dark", 0)
        lightCarousel = prefs.getBoolean("wallpaper_light_carousel", false)
        darkCarousel = prefs.getBoolean("wallpaper_dark_carousel", false)
        carouselIntervalMinutes = prefs.getInt("wallpaper_interval", 5).coerceIn(1, 60)
        dockWidthPercent = prefs.getInt("dock_width", 88)
        dockHeightDp = prefs.getInt("dock_height", 154)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        loadInstalledApps()
        showHome()
    }

    override fun onResume() {
        super.onResume()
        if (page == "home") startClock()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockTask)
        handler.removeCallbacks(carouselTask)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        wallpaperExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && page == "home") {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!dockExpanded) {
                        setDockExpanded(true)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (dockExpanded) {
                        setDockExpanded(false)
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
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
        val colors = currentWallpaper()
        wallpaperView.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(colors[0], colors[1], colors[2])
        )
        loadPhoto(if (dark) wallpaperDark else wallpaperLight, dark, wallpaperView)
    }

    private fun loadPhoto(index: Int, dark: Boolean, target: ImageView, thumbnail: Boolean = false) {
        val photos = if (dark) darkPhotos else lightPhotos
        val safeIndex = index.coerceIn(0, photos.lastIndex)
        val photo = photos[safeIndex]
        val tag = "${if (dark) "night" else "day"}-$safeIndex"
        if (target.tag == tag && target.drawable != null) return
        if (target.tag != tag) target.setImageDrawable(null)
        target.tag = tag
        val directory = File(filesDir, "wallpapers").apply { mkdirs() }
        val file = File(directory, "${if (dark) "night" else "day"}-$safeIndex.jpg")

        fun decodeAndPost() {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = if (thumbnail) 4 else 1
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return
            handler.post { if (target.tag == tag) target.setImageBitmap(bitmap) }
        }

        if (file.exists() && file.length() > 10_000L) {
            wallpaperExecutor.execute { decodeAndPost() }
            return
        }

        wallpaperExecutor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(photo.imageUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 25000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "RFTV-Launcher/0.1")
                }
                connection.connect()
                if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
                val temp = File(directory, file.name + ".part")
                connection.inputStream.use { input ->
                    FileOutputStream(temp).use { output -> input.copyTo(output) }
                }
                if (temp.length() > 10_000L) {
                    if (file.exists()) file.delete()
                    temp.renameTo(file)
                    decodeAndPost()
                } else {
                    temp.delete()
                }
            } catch (_: Exception) {
                // Keep the lightweight gradient fallback when the TV is offline.
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun showHome() {
        page = "home"
        dockExpanded = false
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = true
            clipToPadding = true
            isFocusableInTouchMode = true
        }

        wallpaperView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(18, 28, 44))
            contentDescription = "Фотографические обои RFTV"
        }
        root.addView(wallpaperView, FrameLayout.LayoutParams(-1, -1))
        applyWallpaper()

        val overlay = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(12, 0, 0, 0), Color.argb(118, 0, 0, 0))
            )
        }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP or Gravity.CENTER_VERTICAL
            setPadding(dp(54), dp(34), dp(54), 0)
        }

        val timeColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        clockText = TextView(this).apply {
            text = "09:41"
            textSize = 66f
            letterSpacing = -0.035f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(10f, 0f, 2f, Color.argb(100, 0, 0, 0))
        }
        dateText = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.argb(235, 255, 255, 255))
            setPadding(dp(4), 0, 0, 0)
        }
        timeColumn.addView(clockText)
        timeColumn.addView(dateText)
        timeColumn.gravity = Gravity.CENTER
        clockText.textSize = 82f
        clockText.setShadowLayer(18f, 0f, 3f, Color.argb(150, 0, 0, 0))
        root.addView(timeColumn, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val weather = glassButton("☀   ПОГОДА") { showWeather() }
        top.addView(weather, LinearLayout.LayoutParams(-2, dp(54)).apply {
            rightMargin = dp(12)
        })
        top.addView(glassButton("⚙") { showControlCenter() },
            LinearLayout.LayoutParams(dp(58), dp(54)))
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glassPanel(34, 30)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            clipChildren = true
            clipToPadding = true
            elevation = dp(16).toFloat()
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
            leftMargin = dp(54)
            rightMargin = dp(54)
            bottomMargin = dp(22)
            width = (resources.displayMetrics.widthPixels * dockWidthPercent / 100f).toInt()
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        root.addView(dock, dockParams)

        val wallpaperButton = glassButton("Обои  ◀   ▶") { nextWallpaper() }
        val wallpaperParams = FrameLayout.LayoutParams(-2, dp(42), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(56)
            bottomMargin = dp(dockHeightDp + 38)
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
        val firstRow = installedApps.take(7)
        val secondRow = installedApps.drop(7)

        dockContent.addView(buildAppRow(firstRow), LinearLayout.LayoutParams(-1, dp(122)))
        if (secondRow.isNotEmpty()) {
            dockContent.addView(buildAppRow(secondRow), LinearLayout.LayoutParams(-1, dp(122)))
        } else {
            val empty = TextView(this).apply {
                text = "Приложения появятся здесь после установки"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }
            dockContent.addView(empty, LinearLayout.LayoutParams(-1, dp(122)))
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
                isFocusable = true
                isFocusableInTouchMode = true
                background = roundedGlass(Color.argb(25, 255, 255, 255), 19)
                setPadding(dp(7), dp(5), dp(7), dp(4))
                contentDescription = app.label
                setOnFocusChangeListener { view, focused ->
                    view.background = roundedGlass(
                        if (focused) Color.argb(112, 255, 255, 255)
                        else Color.argb(25, 255, 255, 255), 19
                    )
                    view.scaleX = if (focused) 1.07f else 1f
                    view.scaleY = if (focused) 1.07f else 1f
                    view.elevation = if (focused) dp(12).toFloat() else 0f
                }
                setOnClickListener { launchApp(app.packageName) }
            }
            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            tile.addView(icon, LinearLayout.LayoutParams(dp(56), dp(56)))
            val label = TextView(this).apply {
                text = app.label
                textSize = 11f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, dp(4), 0, 0)
            }
            tile.addView(label, LinearLayout.LayoutParams(dp(88), dp(22)))
            row.addView(tile, LinearLayout.LayoutParams(dp(102), dp(112)).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
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
        dockExpanded = expanded
        val params = dock.layoutParams as FrameLayout.LayoutParams
        params.height = dp(if (expanded) max(270, dockHeightDp + 112) else dockHeightDp)
        dock.layoutParams = params
        dock.animate().alpha(1f).setDuration(180).start()
        if (expanded) {
            val second = dockContent.getChildAt(1)
            second?.alpha = 0f
            second?.animate()?.alpha(1f)?.setDuration(220)?.start()
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

    private fun showWeather() {
        showPanel("Погода и информация") { body ->
            body.addView(infoCard("ПОГОДА", "Челябинск · Open-Meteo",
                "Экран ожидания RFTV загружает текущую температуру, ощущаемую температуру и ветер. Данные обновляются раз в час и сохраняются на время сеанса."))
            body.addView(infoCard("КУРСЫ ВАЛЮТ", "Банк России",
                "На заставке показываются курсы доллара и евро к рублю из официального XML-источника ЦБ РФ."))
            body.addView(infoCard("НОВОСТИ", "Россия и мир",
                "Заголовки поступают из RSS-лент РИА Новости и BBC World. Источники подписаны на экране."))
            body.addView(glassButton("Настроить экран ожидания") {
                openSettings("android.settings.DREAM_SETTINGS")
            }, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(12) })
            body.addView(glassButton("Закрыть") { showHome() }, LinearLayout.LayoutParams(-2, dp(48)).apply {
                topMargin = dp(8)
            })
        }
    }

    private fun showControlCenter() {
        showPanel("Панель управления") { body ->
            val rowOne = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowOne.addView(controlTile("Wi-Fi", "Системное управление") {
                openSettings(Settings.ACTION_WIFI_SETTINGS)
            }, weightedParams())
            rowOne.addView(controlTile("Bluetooth", "Системное управление") {
                openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            }, weightedParams().apply { leftMargin = dp(10) })
            body.addView(rowOne)

            body.addView(infoCard("ЯРКОСТЬ", "Управление дисплеем", "На этой прошивке откроем системную регулировку яркости, если прямой API недоступен."))
            body.addView(glassButton("Открыть настройки дисплея") {
                openSettings(Settings.ACTION_DISPLAY_SETTINGS)
            }, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })

            val themeLabel = when (themeMode) {
                0 -> "Светлая"
                1 -> "Тёмная"
                else -> "По времени"
            }
            body.addView(controlTile("Тема: " + themeLabel, "Нажми, чтобы переключить режим") {
                themeMode = (themeMode + 1) % 3
                prefs.edit().putInt("theme_mode", themeMode).apply()
                showControlCenter()
            }, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(12) })

            val memory = readMemory()
            body.addView(controlTile("RAM · " + memory.first, "Доступно сейчас: " + memory.second) {
                openSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
            }, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(10) })
            body.addView(glassButton("Управление приложениями") {
                openSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
            }, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(8) })

            body.addView(controlTile("Настройки RFTV", "Dock, приложения, обои и внешний вид") {
                showLauncherSettings()
            }, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(12) })
            body.addView(controlTile("Системные настройки", "Открыть настройки Android / StarOS") {
                openSettings(Settings.ACTION_SETTINGS)
            }, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(10) })
        }
    }

    private fun showLauncherSettings() {
        showPanel("Настройки RFTV") { body ->
            body.addView(infoCard("ОФОРМЛЕНИЕ", "Liquid Glass", "Лёгкие полупрозрачные поверхности и контуры вместо тяжёлого постоянного размытия."))

            body.addView(sectionLabel("Ширина Dock · " + dockWidthPercent + "%"))
            body.addView(settingSlider(65, 100, dockWidthPercent) { value ->
                dockWidthPercent = value
                prefs.edit().putInt("dock_width", value).apply()
                updateDockSize()
            })

            body.addView(sectionLabel("Высота Dock · " + dockHeightDp + " dp"))
            body.addView(settingSlider(130, 210, dockHeightDp) { value ->
                dockHeightDp = value
                prefs.edit().putInt("dock_height", value).apply()
                updateDockSize()
            })

            addWallpaperSettings(body)

            body.addView(controlTile("Приложения", "Обновить список установленных приложений") {
                loadInstalledApps()
                showHome()
            }, LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(8) })

            body.addView(controlTile("Экран ожидания RFTV", "Обои каждый час · погода · курсы · новости") {
                openSettings("android.settings.DREAM_SETTINGS")
            }, LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(8) })

            body.addView(controlTile("TV Cleaner", "Список пользовательских приложений") {
                showCleaner()
            }, LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(8) })
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
                    if (index == selected) Color.argb(115, 255, 255, 255)
                    else Color.argb(32, 255, 255, 255), 15
                )
                setOnFocusChangeListener { view, focused ->
                    if (focused) view.background = roundedGlass(Color.argb(145, 255, 255, 255), 15)
                    else view.background = roundedGlass(
                        if (index == selected) Color.argb(115, 255, 255, 255)
                        else Color.argb(32, 255, 255, 255), 15
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
        params.height = dp(dockHeightDp)
        dock.layoutParams = params
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
        handler.removeCallbacks(clockTask)
        handler.removeCallbacks(carouselTask)

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(7, 12, 23))
        }
        val panelBackground = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(31, 48, 73), Color.rgb(8, 14, 27))
            )
        }
        root.addView(panelBackground, FrameLayout.LayoutParams(-1, -1))

        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(24))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = TextView(this).apply {
            text = title
            textSize = 29f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        header.addView(heading, LinearLayout.LayoutParams(0, dp(56), 1f))
        header.addView(glassButton("Закрыть  ✕") { showHome() }, LinearLayout.LayoutParams(-2, dp(48)))
        shell.addView(header)

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(18))
        }
        content(body)
        scroll.addView(body)
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        header.getChildAt(1)?.requestFocus()
    }

    private fun infoCard(title: String, subtitle: String, detail: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassPanel(42, 20)
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 11f
            letterSpacing = 0.1f
            setTextColor(Color.argb(190, 220, 232, 255))
        })
        card.addView(TextView(this).apply {
            text = subtitle
            textSize = 19f
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
            textSize = 15f
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
            background = glassPanel(38, 18)
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(16), dp(9), dp(16), dp(9))
            setOnClickListener { action() }
            setOnFocusChangeListener { view, focused ->
                view.background = roundedGlass(
                    if (focused) Color.argb(112, 255, 255, 255) else Color.argb(38, 255, 255, 255),
                    18
                )
                view.scaleX = if (focused) 1.015f else 1f
                view.scaleY = if (focused) 1.015f else 1f
            }
        }
        tile.addView(TextView(this).apply {
            text = title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            maxLines = 1
        })
        tile.addView(TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(Color.LTGRAY)
            maxLines = 1
        })
        return tile
    }

    private fun glassButton(label: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = glassPanel(45, 28)
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(18), 0, dp(18), 0)
            setOnClickListener { action() }
            setOnFocusChangeListener { view, focused ->
                view.background = roundedGlass(
                    if (focused) Color.argb(120, 255, 255, 255) else Color.argb(45, 255, 255, 255),
                    28
                )
                view.scaleX = if (focused) 1.04f else 1f
                view.scaleY = if (focused) 1.04f else 1f
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
        roundedGlass(Color.argb(alpha, 255, 255, 255), radius)

    private fun roundedGlass(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(86, 255, 255, 255))
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
