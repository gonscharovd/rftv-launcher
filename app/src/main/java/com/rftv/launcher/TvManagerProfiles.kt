package com.rftv.launcher

import android.os.Build

/**
 * Curated reference profiles derived from CleanYourTV examples.
 *
 * These are reference-only. A profile is never applied to a device unless its
 * hardware/OS fingerprint matches, and this prototype has no privileged bridge.
 */
internal object TvManagerProfiles {
    data class PackageEntry(
        val packageId: String,
        val label: String,
        val group: String,
        val note: String,
        val risk: String = "Проверить"
    )

    data class Profile(
        val id: String,
        val title: String,
        val boardTokens: List<String>,
        val deviceTokens: List<String>,
        val sdk: Int,
        val androidLabel: String,
        val sourceUrl: String,
        val testedNotes: String,
        val packages: List<PackageEntry>
    ) {
        fun matches(): Boolean {
            val identity = listOf(Build.BOARD, Build.DEVICE, Build.HARDWARE, Build.PRODUCT)
                .map { it.lowercase() }
            val boardMatch = boardTokens.any { token ->
                identity.any { it == token.lowercase() || it.contains(token.lowercase()) }
            }
            val deviceMatch = deviceTokens.any { token ->
                identity.any { it == token.lowercase() || it.contains(token.lowercase()) }
            }
            return Build.VERSION.SDK_INT == sdk && (boardMatch || deviceMatch)
        }
    }

    val all: List<Profile> = listOf(
        Profile(
            id = "sber-at30a6",
            title = "Sber Salute TV 43″ · CVTE / Amlogic T962D4",
            boardTokens = listOf("TP.T962D4.PC756", "t5w"),
            deviceTokens = listOf("at30a6"),
            sdk = 33,
            androidLabel = "StarOS 1.101 · Android 13",
            sourceUrl = "https://github.com/Gathin23/cleanyourtv/tree/main/examples/sber-salute-tv-at30a6",
            testedNotes = "Профиль проверен автором CleanYourTV на конкретной плате и прошивке. 26 пакетов в списке; отключение, не удаление. Службу ru.sberdevices.services и системные компоненты оставляют включёнными.",
            packages = listOf(
                PackageEntry("ru.sberdevices.music", "Zvuk / музыка", "Контент", "Обновление приложения может продолжать занимать место в /data.", "Средний"),
                PackageEntry("ru.sberdevices.starlivetv", "StarLiveTv", "Контент", "Онлайн-каналы Sber.", "Средний"),
                PackageEntry("ru.sberdevices.startube", "Startube", "Контент", "Обёртка YouTube от Sber; может переустанавливаться службой платформы.", "Средний"),
                PackageEntry("ru.sberdevices.videos", "Видео", "Контент", "Каталог видео.", "Средний"),
                PackageEntry("ru.sberdevices.sberplay", "SberPlay", "Контент", "Игры Sber.", "Средний"),
                PackageEntry("ru.sberdevices.subscriptions", "SberPrime", "Контент", "Подписки; платформа может вернуть пакет.", "Средний"),
                PackageEntry("ru.sberdevices.starkidsroom.prod", "Детская комната", "Контент", "Детский раздел.", "Средний"),
                PackageEntry("ru.sberdevices.parentcontrol", "Родительский контроль", "Контент", "Отключение лишает функций родительского контроля.", "Высокий"),
                PackageEntry("ru.sberdevices.starprofile", "Профили", "Контент", "Управление профилями пользователей.", "Средний"),
                PackageEntry("ru.sberdevices.smarthome.promo", "Промо умного дома", "Контент", "Промо-компонент.", "Низкий"),
                PackageEntry("com.salute.star", "Умный дом", "Контент", "Приложение умного дома.", "Средний"),
                PackageEntry("ru.sberdevices.starbrowser", "Браузер Salute", "Контент", "Браузер.", "Средний"),
                PackageEntry("ru.sberdevices.starmarket", "Магазин приложений Sber", "Магазин", "Нужен для установки и обновления приложений Sber.", "Высокий"),
                PackageEntry("ru.sberdevices.tv", "Настройка провайдеров", "ТВ-функции", "В профиле HDMI продолжал работать, но ТВ-гид Sber может зависеть от пакета.", "Высокий"),
                PackageEntry("ru.sberdevices.starscreensaver", "Заставка Sber", "Экран ожидания", "Перед отключением нужно назначить другую DreamService.", "Средний"),
                PackageEntry("ru.sberdevices.survey", "Опросы", "Служебное", "Опросы.", "Низкий"),
                PackageEntry("ru.sberdevices.openbeta", "Open Beta", "Служебное", "Программа бета-тестирования.", "Низкий"),
                PackageEntry("ru.sberdevices.bugreporter", "Отчёты об ошибках", "Служебное", "Отправка диагностических отчётов.", "Низкий"),
                PackageEntry("ru.sberdevices.starcleaner", "StarCleaner", "Служебное", "Штатный экран очистки памяти.", "Средний"),
                PackageEntry("ru.sberdevices.rcfupdater", "Обновление пульта", "Пульт", "В профиле пульт продолжал работать, но обновление прошивки пульта отключается.", "Высокий"),
                PackageEntry("ru.sberdevices.gamepad.service", "Служба геймпада", "Периферия", "Служба игрового контроллера.", "Средний"),
                PackageEntry("ru.sberdevices.huaweicast", "Huawei Cast", "Трансляция", "Приём трансляций Huawei.", "Средний"),
                PackageEntry("com.maxhub.screenshare.server.tv", "MHshare", "Трансляция", "Приём трансляций MAXHUB.", "Средний"),
                PackageEntry("com.screenshare.platform", "ScreenShare", "Трансляция", "Платформа приёма трансляций.", "Средний"),
                PackageEntry("com.cvte.dssshare.dlna", "DLNA renderer", "Трансляция", "DLNA-приёмник; в тесте это обычное приложение.", "Высокий"),
                PackageEntry("ru.sberdevices.starlauncher", "Штатный лаунчер Sber", "Оболочка", "В примере его отключали только после передачи роли Home новому лаунчеру. Не отключать вслепую.", "Критический")
            )
        ),
        Profile(
            id = "sber-pb766",
            title = "Sber Salute TV 32″ · CVTE / MediaTek MT9256",
            boardTokens = listOf("TPD.MT9216.PB766", "mt5867"),
            deviceTokens = listOf("MD_CVTE_OVERSEA_MT9256_11_0_1GB_STARKOS"),
            sdk = 30,
            androidLabel = "StarOS 1.103 · Android 11",
            sourceUrl = "https://github.com/Gathin23/cleanyourtv/tree/main/examples/sber-salute-tv-pb766",
            testedNotes = "Другой профиль Sber на MediaTek. 27 пакетов в списке. Штатный лаунчер в тесте оставляли включённым; несколько приложений платформа повторно включала после перезагрузки.",
            packages = listOf(
                PackageEntry("ru.sberdevices.survey", "Опросы", "Служебное", "Опросы.", "Низкий"),
                PackageEntry("ru.sberdevices.bugreporter", "Отчёты об ошибках", "Служебное", "Диагностика.", "Низкий"),
                PackageEntry("ru.sberdevices.starcleaner", "StarCleaner", "Служебное", "Штатный экран очистки памяти.", "Средний"),
                PackageEntry("ru.sberdevices.rcfupdater", "Обновление пульта", "Пульт", "Пакет мог повторно включаться после перезагрузки.", "Высокий"),
                PackageEntry("ru.sberdevices.starprofile", "Профили", "Контент", "Профили пользователей.", "Средний"),
                PackageEntry("ru.sberdevices.openbeta", "Open Beta", "Служебное", "Программа бета-тестирования.", "Низкий"),
                PackageEntry("ru.sberdevices.tv", "Настройка провайдеров", "ТВ-функции", "В тесте HDMI оставался доступен.", "Высокий"),
                PackageEntry("ru.sberdevices.starscreensaver", "Заставка Sber", "Экран ожидания", "Сначала назначить альтернативную заставку.", "Средний"),
                PackageEntry("ru.sberdevices.music", "Zvuk / музыка", "Контент", "Музыкальное приложение.", "Средний"),
                PackageEntry("ru.sberdevices.videos", "Видео", "Контент", "Каталог видео.", "Средний"),
                PackageEntry("ru.sberdevices.starlivetv", "StarLiveTv", "Контент", "Онлайн-каналы.", "Средний"),
                PackageEntry("ru.sberdevices.sberplay", "SberPlay", "Контент", "Игры.", "Средний"),
                PackageEntry("ru.sberdevices.subscriptions", "SberPrime", "Контент", "Платформа могла повторно включить пакет.", "Средний"),
                PackageEntry("ru.sberdevices.starbrowser", "Браузер Salute", "Контент", "Браузер.", "Средний"),
                PackageEntry("ru.sberdevices.starkidsroom.prod", "Детская комната", "Контент", "Детский раздел.", "Средний"),
                PackageEntry("ru.sberdevices.parentcontrol", "Родительский контроль", "Контент", "Отключение лишает функций родительского контроля.", "Высокий"),
                PackageEntry("ru.sberdevices.smarthome.promo", "Промо умного дома", "Контент", "Промо-компонент.", "Низкий"),
                PackageEntry("com.salute.star", "Умный дом", "Контент", "Приложение умного дома.", "Средний"),
                PackageEntry("com.mediatek.androidbox", "WFDSinkTest / Miracast", "Трансляция", "Приёмник Miracast.", "Высокий"),
                PackageEntry("ru.more.play", "Okko", "Видео", "В тесте пакет повторно устанавливался платформой.", "Средний"),
                PackageEntry("ru.mts.mtstv", "KION", "Видео", "Онлайн-кинотеатр.", "Средний"),
                PackageEntry("ru.ivi.client", "Иви", "Видео", "Онлайн-кинотеатр.", "Средний"),
                PackageEntry("com.vgtrk.smotrim", "Смотрим", "Видео", "Онлайн-видео.", "Средний"),
                PackageEntry("ru.tv1.android.tv", "Первый канал", "Видео", "Приложение телеканала.", "Средний"),
                PackageEntry("tv.lfstrm.smotreshka_stb", "Смотрёшка", "Видео", "ТВ-сервис.", "Средний"),
                PackageEntry("com.yandex.browser.tv", "Яндекс Браузер", "Контент", "Браузер.", "Средний"),
                PackageEntry("org.videolan.vlc", "VLC", "Медиа", "Встроенный видеоплеер; USB-видео в тесте продолжало работать через другой системный плеер.", "Высокий")
            )
        )
    )

    fun matchingProfiles(): List<Profile> = all.filter { it.matches() }
}
