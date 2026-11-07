package com.rftv.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(12, 16, 25)
    private val surfaceColor = Color.rgb(27, 34, 48)
    private val accentColor = Color.rgb(93, 151, 255)
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized && root.tag == "home") showHome()
    }

    private fun showHome() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(36), dp(24), dp(36), dp(24))
            setBackgroundColor(backgroundColor)
            tag = "home"
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = TextView(this).apply {
            text = "RFTV"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        header.addView(brand)

        val subtitle = TextView(this).apply {
            text = "   LIGHTWEIGHT TV EXPERIENCE"
            textSize = 11f
            setTextColor(Color.LTGRAY)
        }
        header.addView(subtitle)

        val cleanerButton = makeButton("TV CLEANER") { showCleaner() }
        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(48)
        ).apply {
            leftMargin = dp(24)
        }
        header.addView(cleanerButton, buttonParams)
        root.addView(header)

        val sectionTitle = TextView(this).apply {
            text = "Приложения"
            textSize = 23f
            setTextColor(Color.WHITE)
            setPadding(0, dp(28), 0, dp(16))
        }
        root.addView(sectionTitle)

        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply {
            columnCount = 5
            useDefaultMargins = false
        }

        val apps = getLaunchableApps()
        if (apps.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Не удалось найти приложения для запуска"
                textSize = 18f
                setTextColor(Color.LTGRAY)
            }
            root.addView(empty)
        } else {
            apps.forEach { app ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isFocusable = true
                    isFocusableInTouchMode = true
                    background = roundedBackground(surfaceColor, 16)
                    setPadding(dp(10), dp(14), dp(10), dp(12))
                    contentDescription = app.label
                    setOnFocusChangeListener { view, focused ->
                        view.background = roundedBackground(
                            if (focused) accentColor else surfaceColor,
                            16
                        )
                        view.scaleX = if (focused) 1.04f else 1f
                        view.scaleY = if (focused) 1.04f else 1f
                    }
                    setOnClickListener { launchApp(app.packageName) }
                }

                val icon = ImageView(this).apply {
                    setImageDrawable(app.icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                tile.addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)))

                val label = TextView(this).apply {
                    text = app.label
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    maxLines = 2
                    setPadding(0, dp(10), 0, 0)
                }
                tile.addView(
                    label,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(38)
                    )
                )

                val params = GridLayout.LayoutParams().apply {
                    width = dp(142)
                    height = dp(142)
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
                grid.addView(tile, params)
            }
            scroll.addView(grid)
            root.addView(
                scroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            setContentView(root)
            grid.getChildAt(0)?.requestFocus()
        }
        setContentView(root)
    }

    private fun showCleaner() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(36), dp(24), dp(36), dp(24))
            setBackgroundColor(backgroundColor)
            tag = "cleaner"
        }

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "TV Cleaner"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        top.addView(title, LinearLayout.LayoutParams(0, dp(52), 1f))
        top.addView(makeButton("НАЗАД") { showHome() })
        root.addView(top)

        val notice = TextView(this).apply {
            text = "Выберите приложение для перехода к штатному удалению Android. Системные компоненты здесь не удаляются. Перед удалением убедитесь, что приложение вам не нужно."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(12), 0, dp(18))
        }
        root.addView(notice)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val userApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter {
                it.packageName != packageName &&
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }
            .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }

        userApps.forEach { info ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = roundedBackground(surfaceColor, 10)
            }
            val appName = TextView(this).apply {
                text = packageManager.getApplicationLabel(info).toString()
                textSize = 16f
                setTextColor(Color.WHITE)
                maxLines = 1
            }
            row.addView(appName, LinearLayout.LayoutParams(0, dp(48), 1f))
            row.addView(makeButton("УДАЛИТЬ") {
                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:${info.packageName}")
                }
                startActivity(intent)
            })
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(7)
            }
            list.addView(row, params)
        }

        if (userApps.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Пользовательские приложения не найдены."
                setTextColor(Color.LTGRAY)
                textSize = 17f
            }
            list.addView(empty)
        }

        scroll.addView(list)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        setContentView(root)
        top.getChildAt(1)?.requestFocus()
    }

    private data class LaunchableApp(
        val label: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable
    )

    private fun getLaunchableApps(): List<LaunchableApp> {
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .mapNotNull { info ->
                if (info.packageName == packageName) return@mapNotNull null
                val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(info.packageName)
                    ?: packageManager.getLaunchIntentForPackage(info.packageName)
                    ?: return@mapNotNull null
                LaunchableApp(
                    packageManager.getApplicationLabel(info).toString(),
                    info.packageName,
                    packageManager.getApplicationIcon(info)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) startActivity(intent)
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            isFocusable = true
            setTextColor(Color.WHITE)
            background = roundedBackground(surfaceColor, 12)
            setOnClickListener { action() }
            setOnFocusChangeListener { view, focused ->
                view.background = roundedBackground(
                    if (focused) accentColor else surfaceColor,
                    12
                )
            }
        }
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
