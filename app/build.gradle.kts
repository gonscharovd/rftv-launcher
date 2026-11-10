
import java.net.HttpURLConnection
import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rftv.launcher"
    compileSdk = 35

    buildFeatures {
        aidl = true
    }

    lint {
        // This launcher targets Android 9 TV firmware; Google Play target policy is not applicable.
        disable += "ExpiredTargetSdkVersion"
    }

    defaultConfig {
        applicationId = "com.rftv.launcher"
        minSdk = 24
        targetSdk = 28
        versionCode = 12
        versionName = "0.9.0"
        manifestPlaceholders["appLabel"] = "RFTV Launcher"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("ANDROID_KEYSTORE_FILE")
            val storePass = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            val keyAliasValue = System.getenv("ANDROID_KEY_ALIAS")
            val keyPass = System.getenv("ANDROID_KEY_PASSWORD")
            if (!storePath.isNullOrBlank() && !storePass.isNullOrBlank() &&
                !keyAliasValue.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = storePass
                keyAlias = keyAliasValue
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "RFTV Launcher Debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
}

// Bundle the 20 licensed Unsplash photographs into the APK. The TV must not depend on
// images.unsplash.com being reachable at runtime; this also makes first boot and offline
// wallpaper selection deterministic on older OEM Android TV firmware.
val generatedWallpaperAssets = layout.buildDirectory.dir("generated/rftv-wallpaper-assets")
android.sourceSets.getByName("main").assets.srcDir(generatedWallpaperAssets)

val packageWallpapers by tasks.registering {
    val wallpaperSource = layout.projectDirectory.file("src/main/java/com/rftv/launcher/MainActivity.kt")
    val donorLicense = rootProject.file("third_party/tvos-launcher/LICENSE")
    val donorNotice = rootProject.file("third_party/tvos-launcher/NOTICE")
    val notices = rootProject.file("THIRD_PARTY_NOTICES.md")
    inputs.file(wallpaperSource)
    inputs.files(donorLicense, donorNotice, notices)
    outputs.dir(generatedWallpaperAssets)

    doLast {
        val source = wallpaperSource.asFile.readText()
        val pattern = Regex("""WallpaperPhoto\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)"\)""")
        val photos = pattern.findAll(source).toList()
        check(photos.size == 20) {
            "Expected exactly 20 wallpaper sources (10 day + 10 night), found ${photos.size}"
        }

        val root = generatedWallpaperAssets.get().asFile
        val wallpaperDir = root.resolve("wallpapers")
        wallpaperDir.mkdirs()

        // Apache-2.0 requires the upstream license and NOTICE to accompany the
        // adapted weather implementation in the distributed APK as well as the source tree.
        val legalDir = root.resolve("third_party/tvos-launcher")
        legalDir.mkdirs()
        donorLicense.copyTo(legalDir.resolve("LICENSE"), overwrite = true)
        donorNotice.copyTo(legalDir.resolve("NOTICE"), overwrite = true)
        val noticesDir = root.resolve("third_party")
        noticesDir.mkdirs()
        notices.copyTo(noticesDir.resolve("THIRD_PARTY_NOTICES.md"), overwrite = true)

        photos.forEachIndexed { index, match ->
            val isNight = index >= 10
            val photoIndex = if (isNight) index - 10 else index
            val name = "${if (isNight) "night" else "day"}-$photoIndex.jpg"
            val target = wallpaperDir.resolve(name)
            if (target.isFile && target.length() > 10_000L) return@forEachIndexed

            val address = match.groupValues[4]
            val connection = URL(address).openConnection() as HttpURLConnection
            val part = wallpaperDir.resolve("$name.part")
            try {
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "RFTV-Launcher-Wallpaper-Packager/0.9")
                connection.setRequestProperty("Accept", "image/jpeg,image/*;q=0.9,*/*;q=0.5")
                val status = connection.responseCode
                check(status in 200..299) { "Wallpaper $name returned HTTP $status" }
                val contentType = connection.contentType.orEmpty()
                check(contentType.isBlank() || contentType.startsWith("image/", ignoreCase = true)) {
                    "Wallpaper $name returned non-image content: $contentType"
                }
                connection.inputStream.use { input ->
                    part.outputStream().buffered().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= 12L * 1024L * 1024L) { "Wallpaper $name exceeds 12 MB" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                check(part.length() > 10_000L) { "Wallpaper $name is empty or truncated" }
                val header = part.inputStream().use { it.readNBytes(3) }
                check(header.size == 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
                    "Wallpaper $name is not a JPEG image"
                }
                if (target.exists()) target.delete()
                check(part.renameTo(target)) { "Could not write bundled wallpaper $name" }
            } finally {
                part.delete()
                connection.disconnect()
            }
        }

        check(wallpaperDir.listFiles()?.count { it.extension.equals("jpg", ignoreCase = true) } == 20) {
            "Bundled wallpaper asset set is incomplete"
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(packageWallpapers)
}
