# Анализ донорских проектов и план переноса

Дата анализа: 2026-11-09. Уточнение продуктового направления владельцем: 2026-11-10.

## Источники и лицензии

- [tvOS Launcher](https://github.com/techgenius1/tvOS-Launcher) — Apache License 2.0.
- [CleanYourTV](https://github.com/Gathin23/cleanyourtv) — MIT License.
- В RFTV 0.8.0 исходный код и бинарные компоненты этих проектов не копируются. Используются архитектурные идеи и ссылки на источники; адаптированные идентификаторы пакетов и описания CleanYourTV уже отмечены в `THIRD_PARTY_NOTICES.md`. tvOS Launcher распространяется по Apache-2.0 и содержит отдельный NOTICE; CleanYourTV — MIT. Если в будущем будет перенесён исходный код, необходимо добавить соответствующие LICENSE/NOTICE и авторские уведомления к конкретным файлам.

## Что показал анализ исходного кода tvOS Launcher

Изучен исходный `SettingsActivity.java`, а не только README:

- `SIDEBAR_SECTIONS` формирует постоянную боковую навигацию, правая область перерисовывается под выбранный раздел.
- Состояния `currentSection`, `inRows`, `activeSubMenu`, `subMenuStack` и `subMenuParentFocusIdx` сохраняют контекст вложенных экранов и позицию фокуса.
- Миниатюры сцен кэшируются в `ConcurrentHashMap`; загрузка ограничена пулом из двух потоков.
- Переносим паттерн навигации/фокуса, ограниченного кэша и snapshot blur. В tvOS Launcher Control Panel использует снимок фона и blur с уменьшением разрешения; на Android 12+ доступен RenderEffect. В RFTV для API 28 применяется собственный downsampled box blur в `GlassBackdrop`, выполняемый вне UI-потока. Экран и графику целиком не копируем.

## Что принимаем из tvOS Launcher

| Область | Решение | Причина |
|---|---|---|
| Настройки с боковой навигацией и правой областью деталей | Переносим | Лучше читаемость на телевизоре и меньше длинных вертикальных экранов |
| Единая glass-поверхность, snapshot blur и фокус с мягким scale | Переносим архитектурный подход | На API 28 применяем downsampled snapshot + асинхронный box blur с повторным использованием результата; на новых API возможен нативный путь. Полноценный blur — требование RFTV, производительность измеряется, а не предполагается |
| Кэш изображений и сетевых данных | Сохраняем/усиливаем | Уже есть асинхронная загрузка; переносим принципы ограничения задач и кэша |
| Top Shelf / контекст приложения | Отложено | Новая функция главного экрана; нужна отдельная UX-спецификация |
| Continue Watching / deep links | Не переносим сейчас | Требует интеграции с разными медиаприложениями и разрешений |
| Universal overlay over apps / Button Mapper | Не переносим | Accessibility и OEM-кнопки нестабильны, не нужно для согласованного этапа |
| RAM Cleaner / Close All | Не переносим | Принудительная остановка приложений может ломать воспроизведение и фоновые процессы |
| Caffeine / отключение сна | Не переносим | Меняет системное энергопотребление и поведение ТВ |

## Что показал анализ исходного кода CleanYourTV

Изучены `tools/privacy.py`, `tools/match.py`, `tools/recovery.py`, `tools/disable.sh`, а также `docs/privacy.md` и `docs/restore.md`.

- `privacy.py` не объявляет приложение шпионским по одному `RECORD_AUDIO`: учитывает общий `android.uid.system`, известные firmware-компоненты, AppOps history и совпадения только по имени.
- `match.py` считает, в скольких проверенных списках пакет отключали или явно оставляли включённым. Это сводка тестовых списков, не вердикт для текущего ТВ.
- `recovery.py` записывает исходное состояние пакетов, владельца HOME и animation scales. Первая запись сохраняется при повторном запуске.
- `restore.sh` возвращает именно записанное состояние; пакеты, отключённые владельцем заранее, остаются отключёнными.
- `disable.sh` блокирует критические Android/TV middleware package ID, защищает Home и отдельно проверяет риск потери Wi-Fi при отключении setupwraith.
- CleanYourTV отключает пакеты, не удаляет их. Обратимость — обязательный принцип.

## Что принимаем из CleanYourTV

| Область | Решение | Причина |
|---|---|---|
| Объяснение назначения пакета | Переносим | Пользователю нужен понятный отчёт, не только цветной индикатор |
| Факты отдельно от предположений | Переносим | Не делаем вывод о слежке по одному разрешению |
| Сопоставление профилей по точной модели/плате/API | Сохраняем | Защищает от применения чужих списков |
| Сохранение исходного состояния и точечное восстановление | Проектируем как обязательное условие будущего privileged bridge | Нельзя включать всё подряд при откате |
| Журнал операций | Сохраняем как требование | Нужна трассируемость каждого изменения |
| Массовые профили disable | Не переносим автоматически | Пакеты и прошивки разных ревизий несовместимы |
| Встроенный Local ADB debloater | Не переносим в этом этапе | Нужен отдельно проверенный механизм полномочий, авторизация и тестирование OEM |

## Детали двух Sber-профилей

- PB766: CVTE `TPD.MT9216.PB766`, MediaTek MT9256 / platform mt5867, Android 11, 1 GB RAM. Штатный launcher оставляли включённым. `ru.sberdevices.services` повторно устанавливала и включала часть приложений после перезагрузки.
- AT30A6: CVTE `TP.T962D4.PC756`, Amlogic T962D4 / t5w, Android 13, 2 GB RAM. `ru.sberdevices.services` работает как system UID, удерживает Salute voice assistant и watchdog-восстановление обязательных приложений. Отключение службы не является безопасным сценарием.
- Версия прошивки и reboot-check являются частью доказательства; одного имени бренда/пакета недостаточно.

## Конкретная совместимость Sber

Два опубликованных примера CleanYourTV не подходят к тестовому RFTV устройству:

- Sber PB766: MediaTek MT9256, Android 11 / API 30.
- Sber AT30A6: Amlogic T962D4, Android 13 / API 33.
- Целевое RFTV устройство SDX-50U4123B: MediaTek MT9632, Android 9 / API 28.

Совпадение бренда Sber не является совпадением платформы. Ни один из профилей не разрешается использовать для автоматической классификации или отключения пакетов целевого ТВ.

## Реализация в RFTV

1. Настройки получают навигацию по категориям и отдельную область содержимого.
2. TV Manager использует консервативный локальный анализ PackageManager metadata.
3. Системные критические идентификаторы помечаются для защиты, а неизвестные пакеты остаются «не проверены».
4. Чувствительные permissions показываются как запрошенные факты; это не доказательство использования или передачи данных.
5. Точное совпадение профиля допускается только по board/hardware + device/product + API.
6. Никаких package disable/uninstall команд и никаких привилегированных действий в этом изменении.

## Ограничения

PackageManager metadata не раскрывает фактическую сетевую активность, реальное использование датчиков, AppOps history, динамически загружаемый код и все OEM-зависимости. CleanYourTV получает часть дополнительных фактов через ADB shell (`dumpsys package`, AppOps и системные команды); обычное RFTV-приложение не получает эти полномочия автоматически. Текущий анализ RFTV не подменяет полноценный privacy audit. Для более глубокого анализа потребуется отдельно согласованный, авторизованный диагностический мост и проверка на конкретном устройстве.


## Дополнительный перенос визуального языка и погоды — v0.9.0

- По результатам установки v0.8.0 владелец подтвердил, что визуальная композиция Home, Control Center и Settings неприемлема, а обои и погода не отображаются.
- Визуальный ориентир tvOS Launcher: полноэкранная сцена, крупный центральный clock, единая плавающая Dock-капсула с focus lift, тёмные полупрозрачные поверхности, плавающая боковая панель Control Center, Settings с постоянной боковой навигацией и отдельной областью содержимого.
- RFTV не переносит Recent Apps, Continue Watching, button mapper, системный overlay, или другие функции донора. Меняется только визуальная композиция существующих экранов.
- Реализация weather fallback и WMO/WWO code mapping адаптирована из `Weather.java` tvOS Launcher; исходная Apache-2.0 LICENSE и NOTICE сохранены в `third_party/tvos-launcher/`, изменения отмечены в `RftvWeatherRepository.kt` и `THIRD_PARTY_NOTICES.md`.
- Фотографии остаются настоящими Unsplash-работами с существующей атрибуцией `WALLPAPER_CREDITS.md`. Gradle извлекает 20 URL из каталога RFTV, скачивает, проверяет HTTP/content-type/JPEG header/размер и включает JPEG в APK. Телевизор больше не должен скачивать основной фон при первом запуске.


## Owner clarification — product direction and implementation boundary (2026-11-10)

The launcher, not TV Manager, is the primary product. The tvOS Launcher is a visual and interaction reference only: full-screen photographic composition, glass surfaces, blur, Dock focus behavior, sidebar/right-pane Settings and focus restoration. Do not import donor features simply because they exist upstream.

The Home clock must be composed between two image layers: a full-screen photograph and a separate transparent foreground cut-out that partially overlaps the digits. The first delivery is 3–5 complete layered scenes. Existing v0.9.0 Unsplash photographs are flat backgrounds and do not satisfy this depth requirement. Donor imagery/assets must not be copied without checking their individual license; source and license notices remain mandatory for any adapted code.

All Apps and Control Center require a full-screen blurred backdrop. A blur implementation in source code is not proof of acceptable performance: measure memory, frame pacing and blur latency on the target Sber TV (Android 9/API 28/MT9632/ARMv7).

Weather must sit beside the Home clock and allow the owner to change city and provider configuration. The current fixed Chelyabinsk constants in `RftvWeatherRepository.kt` are shipped behavior, not the target product design.

Settings must use a persistent sidebar and right content pane and restore the user's nested focus position when navigating back. The current sidebar implementation is a starting point, not evidence that all nested focus restoration is complete.

The screensaver must show clock/date, weather, CBR currency rates and Russia/world news, and slowly move the clock to reduce burn-in. Manual and DreamService remote dismissal must be verified separately because Android DreamService may not receive remote key events on every firmware.

## TV Manager priority and safety requirements

TV Manager remains implemented in the repository but feature development is deferred until the primary launcher work is complete. Its existing code is not permission to expand the product scope.

Future TV Manager work must preserve these owner requirements:
- Inventory is discovered from PackageManager on the actual device, not assumed from a static app catalogue.
- Red protections have absolute precedence over all external profiles and must include HOME, System UI, Settings, installer/package manager, networking/Wi-Fi/Bluetooth, remote and input components, TvInputService, `android.uid.system`, and persistent system components. A donor profile or online source must never downgrade a red rule.
- Unknown packages are yellow. Green requires verified evidence. A permission or package name alone is not proof of behavior or safety.
- Automatic disable is restricted to green candidates with exact device identity (model, board/hardware, Android API and firmware/build identity), verified optional status and checked dependencies. Yellow candidates are manual-only and require package-specific confirmation.
- Preserve exact original enabled state, including packages already disabled before RFTV. Apply changes in small batches, verify read-back, stop on failure and roll back in reverse order.
- CleanYourTV MIT profiles are external evidence that a package was disabled on a particular configuration, not universal safety approval. Functional components (assistant, Chromecast/casting, updates and platform services) are not automatic candidates.
- An eventual online database must contain signed data, be generated automatically without requiring manual owner moderation, be signature-verified by the client and remain usable from cache.
- No CleanYourTV profile currently matches Sber SDX-50U4123B / Android 9 / MT9632; it must have no green package candidates until exact-device evidence exists.
- Package modification requires a verified privileged channel such as Shizuku. Without it, TV Manager is analysis-only.

The current v0.9.0 implementation is not yet fully compliant with every future rule: in particular, the source audit found no explicit protection check for shared UID `android.uid.system`, `ApplicationInfo.FLAG_PERSISTENT`, or TvInputService role, and no signed online profile database. These are recorded gaps, not completed features. Do not develop them during the current launcher-first phase.
