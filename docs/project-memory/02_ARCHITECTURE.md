# Архитектура RFTV Launcher

**Срез кода:** `main` @ `0b1815b092840277b06dc7d7154c77790899d561` (2026-11-09).

## Технологии и сборка

- Kotlin 2.0.21; Android Views, без Jetpack Compose.
- Android Gradle Plugin 8.7.3; Gradle Wrapper/configuration рассчитан на Gradle 8.9; JDK 17.
- compileSdk 35, minSdk 24, targetSdk 28.
- Один Android application module: `:app`.
- Package/namespace: `com.rftv.launcher`.
- Единственная runtime dependency явно объявленная в `app/build.gradle.kts`: JUnit 4.13.2 для unit tests. UI нативный, third-party UI dependencies нет.
- Репозитория wrapper-файлов Gradle в дереве на дату аудита не обнаружено; README предлагает системный Gradle или Android Studio.

См. [root build](../../build.gradle.kts), [settings](../../settings.gradle.kts), [app build](../../app/build.gradle.kts), [gradle.properties](../../gradle.properties).

## Структура

```
.github/workflows/
  android.yml
  publish-release.yml
app/
  build.gradle.kts
  src/main/
    AndroidManifest.xml
    java/com/rftv/launcher/
      MainActivity.kt
      RftvDesignSystem.kt
      WallpaperImageUrls.kt
      TvManagerProfiles.kt
      AdbAccessDiagnostics.kt
      OtaInstallReceiver.kt
      RftvDreamService.kt
      ScreensaverActivity.kt
    res/drawable/rftv_banner.xml
    res/xml/rftv_dream.xml
  src/test/java/com/rftv/launcher/
    RftvDesignSystemTest.kt
    WallpaperImageUrlsTest.kt
```

## Основные компоненты

| Компонент | Роль, подтверждённая исходниками |
|---|---|
| `MainActivity.kt` | Основная HOME/LEANBACK activity и основная часть интерфейса; крупный файл (~126 KB), в нём сосредоточено много UI/поведения |
| `RftvDesignSystem.kt` | Общие цветовые/размерные/радиусные/анимационные токены и drawable/focus helpers |
| `WallpaperImageUrls.kt` | Каталог/URL-логика фотографических обоев |
| `TvManagerProfiles.kt` | Справочные профили пакетов с проверкой SDK + board/hardware + device/product; reference-only |
| `AdbAccessDiagnostics.kt` | Диагностические сведения ADB-доступа; не означает наличие привилегированного bridge |
| `OtaInstallReceiver.kt` | Внутренний receiver для результата/события OTA-установки |
| `RftvDreamService.kt` | Android DreamService заставка |
| `ScreensaverActivity.kt` | Отдельная activity заставки |
| `AndroidManifest.xml` | HOME/DEFAULT и LEANBACK launcher intent filters, permissions, activities, DreamService |

## Manifest и platform contracts

Manifest объявляет optional Leanback/touchscreen features; permissions: `QUERY_ALL_PACKAGES`, `INTERNET`, `ACCESS_WIFI_STATE`, `REQUEST_INSTALL_PACKAGES`. Приложение запрещает cleartext traffic, отключает backup, использует landscape и Material NoActionBar. `MainActivity` экспортирована для HOME/LEANBACK; ScreensaverActivity — не экспортирована; DreamService экспортирована с `BIND_DREAM_SERVICE`; OTA receiver не экспортирован.

Учитывать API 24–35 и особенности OEM. Разрешение или package name само по себе не подтверждает слежку, запись либо передачу данных.

## Потоки данных и интеграции

- Обои: HTTPS Unsplash image CDN → приватное хранилище приложения → повторное использование кэша/офлайн. Авторство и исходные ссылки: [WALLPAPER_CREDITS](../../WALLPAPER_CREDITS.md).
- Заставка: README описывает случайные пейзажи LoremFlickr, Open-Meteo для Челябинска, курсы ЦБ РФ и RSS RIA/Google News; часть настроек/источников зафиксирована в коде. Источники и обновления должны обрабатываться с сетевыми ошибками и кэшем.
- TV Manager: локальная системная инвентаризация и сопоставление со статическими справочными профилями. Два Sber reference profiles не совпадают с целевой моделью SDX-50U4123B по заявленной конфигурации. Привилегированное управление пакетами отсутствует.
- OTA: GitHub Releases и workflow publisher; APK подписывается через GitHub Actions secrets/env. Названия переменных видны в Gradle-конфигурации, значения секретны и не документируются.

## Дизайн и производительность

`RftvDesignSystem` задаёт glass fill/stroke, цвета, размеры, радиусы, elevation и короткие property animations. Реальный blur/RenderEffect не применяется — сознательное ограничение для MT9632/Android 9. Использовать асинхронную загрузку, приватный кэш и минимизировать invalidation.

## CI/CD

- [android.yml](../../.github/workflows/android.yml): CI сборки, unit tests, lint и debug metadata на push/PR (точные триггеры/шаги проверять в workflow).
- [publish-release.yml](../../.github/workflows/publish-release.yml): проверенная подписанная Release-сборка, metadata/hash/signature verification и публикация через draft → проверка assets → stable; workflow также безопасно пропускает уже выпущенный versionCode.
- Текущие конкретные run IDs и результат указаны в [03_CURRENT_STATE.md](03_CURRENT_STATE.md).

## Тесты

Доступны два локальных набора JUnit: дизайн-токены и URL/обои. Автоматический CI на проверенном HEAD успешен. Наличие unit tests не заменяет инструментальные и физические DPAD/OEM тесты.

## Архитектурные ориентиры

Текущая архитектура — исходная точка, а не запрет на развитие. ИИ самостоятельно выбирает и меняет внутреннюю архитектуру, структуру модулей и зависимости, если это обосновано задачей, улучшает качество/сопровождение и покрыто миграционным планом и проверками. Не переписывать крупный `MainActivity.kt` ради косметики; выделять компоненты по мере реальной необходимости. Не добавлять зависимости без технической причины. Совместимость с Android 9/API 28/ARMv7 целевого ТВ сохранять как обязательный regression target; расширять или менять матрицу поддержки на основании доказательств, не выдавая неподтверждённую совместимость за факт.
