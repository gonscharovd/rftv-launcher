# Changelog

## 0.9.3
- Fixed the crash when opening Launcher Settings: all eight sections now have navigation glyphs, with a regression test.
- Reworked Dock geometry and focus to match measured tvOS Launcher values while retaining RFTV favorites, app catalog and installed-app refresh.
- Refined Control Center capsules and Settings navigation surfaces using donor-measured dimensions and colors.
- Updated Apache-2.0 attribution for Dock, Control Center and Settings UI references.

## 0.9.2 (2026-11-10) — Iteration 2 candidate

- Overlay capture now draws the Home root directly into a <=480 px wide bitmap using a scaled Canvas; it no longer allocates a full-resolution screenshot on the UI thread or downsamples it again on the worker.
- Added separate debug performance markers for synchronous overlay view construction (build_ms) and first pre-draw (layout_ms); existing release Diagnostics UI and stored fields are unchanged.
- Control Center reorganized into compact full-width connectivity pills and two-column action rows; existing Wi-Fi, Bluetooth, theme, RAM, screensaver, launcher settings and TV settings actions are retained. RAM alone receives a quantitative progress indicator. No VPN or Recent Apps action was added because neither is part of the current RFTV Control Center behavior.
- Dock app tiles no longer contain expanding labels or scale as a whole. Icons scale within fixed-height tiles; focused app names appear in a separate tooltip above the Dock pill.
- Settings now has a fixed bottom remote-control hint that changes with the active section.
- Version candidate: com.rftv.launcher, versionName 0.9.2, versionCode 14. This is not a published Release; 0.9.1 remains the latest stable version until PR approval, merge, and verified publication.

## 0.9.1 (2026-11-10) — stable Release

- [Stable Release v0.9.1](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.9.1) published after successful main CI and signed Release verification (CI run 42822789300; publisher run 42823200090).
- APK: `com.rftv.launcher`, versionName `0.9.1`, versionCode `13`, minSdk `24`, targetSdk `28`, 7,985,259 bytes; no native libraries (ABI-independent).
- APK SHA-256: `ef03ecf77a13604ec3834bc47ad8c93ccfe1df615522f81c271235a704944167`.
- Release signing certificate SHA-256: `22b4f71da5f543e933780f05f8573783ad4718036b5bfc1b36fe258695e30917`.
- Iteration 1: hard protection for system shared UID, persistent applications and TV input services; All Apps icon grid with DPAD/focus restoration; asynchronous full-screen snapshot blur for All Apps and Control Center.
- Added a release-visible Diagnostics screen with the latest 10 overlay openings, cold/repeat classification, capture/blur/open timings, source/blurred bitmap sizes, Java/native heap before/after, open counter, and device/build details. Data is process-memory-only; no new permissions, disk writes or network access.
- Debug-only RFTV_PERF log format remains unchanged.
- Physical acceptance on Sber SDX-50U4123B remains pending.

## 0.9.0 (2026-11-10)

- Stable Release [v0.9.0](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.9.0) published; PR #36 merged into main (merge commit f4ab471e7f433e18ce96d507e9880ae5655379ee).
- Android CI run 42810753407 and publisher run 42811138590 succeeded.
- APK: com.rftv.launcher, versionName 0.9.0, versionCode 12, minSdk 24, targetSdk 28, 7,972,147 bytes.
- SHA-256: d4a2941add01cfb6c7c56845005f02939eceef3ed9c05c2e04b8c0839539e31c; permanent signing certificate SHA-256: 22b4f71da5f543e933780f05f8573783ad4718036b5bfc1b36fe258695e30917.
- Home: full-screen photo and central clock. Dock: single dark glass capsule with focus lift. Control Center: floating right panel above Dock. Settings: clearer sidebar/content hierarchy.
- All 20 real Unsplash photographs are downloaded and validated during build, packaged in the APK and available without runtime Unsplash access. CI verifies all JPEG assets and Apache notices in Debug/Release APKs.
- Home/forecast/screensaver use a shared cache and Open-Meteo → MET Norway → wttr.in fallback.
- No new user-facing functionality was added. Physical acceptance on Sber SDX-50U4123B remains pending.

# RFTV Launcher — Changelog

## 0.8.0 (2026-11-09)

- Стабильный Release опубликован: [v0.8.0](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.8.0), versionCode 11. PR #34 слит в main (merge `86b9d3d`).
- Android CI run `42619490305` и Release publisher run `42619792984` завершились успешно. Опубликованный APK: 817 577 байт; SHA-256 `1ab18c47b66bf3b45636b7e4fa97c516397be5105a66aa71a790917ec2df2f0a`; постоянный certificate SHA-256 `22b4f71da5f543e933780f05f8573783ad4718036b5bfc1b36fe258695e30917`.
- OTA `/releases/latest` обнаруживает стабильный v0.8.0 (не draft/prerelease). Установка и физическое поведение на ТВ ещё не подтверждены.

- Добавлен live CI-аудит всех 20 фотообоев. Run 42618685512 на PR HEAD подтвердил 10/10 дневных и 10/10 ночных URL: HTTP 200, image/jpeg, полное Pillow decode и 1920×1080 для каждого изображения.
- Добавлен технический отчёт [V0.8.0_IMPLEMENTATION_AUDIT.md](docs/V0.8.0_IMPLEMENTATION_AUDIT.md) с архитектурой, результатами донорского аудита, проверками APK и списком физической приёмки.


- Уточнено визуальное требование v0.8.0: полноценный Liquid Glass blur обязателен; на Android 9 используется асинхронный blur уменьшенного snapshot, на новых API — нативные возможности с fallback. Предварительный запрет на размытие снят по подтверждённому опыту владельца с tvOS Launcher на том же ТВ.

- Зафиксирован согласованный план v0.8.0 по результатам OTA-приёмки v0.7.2: исправление фотообоев и данных заставки; стеклянные часы и погодный прогноз; одностраничный Dock с отдельным каталогом; полно-высотный Control Center; переработка заставки и навигации Settings. Подробности и донорский аудит — docs/POST_0.7.2_FEEDBACK_PLAN.md.
- Добавлен legacy Liquid Glass blur для Android 9: уменьшенный snapshot активных обоев размывается асинхронно несколькими box-blur проходами и переиспользуется для Control Center, Settings и панелей.
- Home получил крупные часы в новой гарнитуре, встроенные в фотографию через настоящий размытый стеклянный backdrop, и текущую погоду рядом; погодная карточка открывает прогноз Open-Meteo на пять дней.
- Dock переведён на одну адаптивную строку, добавлена отдельная плитка «Все приложения», удалены кнопка обоев на Home и раскрытие Dock по DPAD Down.
- Control Center получил собственные vector-иконки и группировку быстрых действий в две колонки в стиле iOS.
- У Settings удалена кнопка закрытия; переходы DPAD Left/Right между боковой навигацией и содержимым заданы явно.
- Ручная ScreensaverActivity и системный DreamService используют единый по структуре dashboard: стеклянные часы, курсы ЦБ РФ, погода и две колонки RSS-новостей поверх размытого активного wallpaper. Источники вынесены в общий ScreensaverDashboardRepository: параллельное получение, независимая свежесть четырёх источников и сохранение последнего успешного значения при сбое. Добавлены unit tests маппинга погоды и срока свежести кэша.

- Загрузчик обоев теперь сначала показывает доступный кэшированный preview, затем проверяет полноразмерный кэш и только после этого обращается к сети; при повторном запуске не скачивается лишнее превью, если оригинал уже сохранён.
- При смене обоев и ручном повторе загрузки последнее успешно показанное изображение остаётся на экране до готовности нового, без пустого/чёрного перехода.
- PR #32 слит: настройки получили многослойную стеклянную оболочку, отдельную поверхность навигации и контрастные состояния выбранного раздела/DPAD-фокуса.

- Dock получил более цельную стеклянную подложку: убраны постоянные контуры неактивных плиток, фокус стал заметнее, подписи получили мягкую тень для читаемости на светлых обоях.
- Настройки получили многослойную стеклянную оболочку, отдельную поверхность боковой навигации и контрастное выделение выбранного/сфокусированного раздела.
- Кнопки и интерактивные карточки используют единое градиентное стекло; при фокусе меняются заливка, контур и цвет текста, чтобы состояние было понятно с расстояния на ТВ.
- Маршрутная карта обновлена: визуальная доводка — текущий приоритет; физическая приёмка на ТВ остаётся обязательной перед закрытием этапа.

- Панель управления больше не перекрывает Dock: высота рассчитывается с учётом фактической высоты Dock и отступа.
- Навигация настроек прокручивается независимо, поэтому нижние разделы не обрезаются на компактных экранах.
- Усилено затемнение фона при открытом Control Center для лучшего отделения стеклянной панели.

- Обои на главном экране загружаются прогрессивно: сначала компактный предварительный вариант, затем полноэкранное изображение; сетевые таймауты сокращены, кэш превью и оригинала разделён.
- Если полноразмерная фотография не загрузилась, уже показанное превью сохраняется вместо сброса обоев в состояние ошибки.


## 0.7.0

- Унифицирован Control Center: общие градиенты, стеклянные поверхности, размеры, радиусы, типографика и состояния DPAD-фокуса вынесены в RftvDesignSystem.
- Исправлено восстановление фокуса после закрытия Control Center: возврат на элемент, с которого панель была открыта, с безопасным fallback на кнопку панели.
- Добавлено лёгкое появление Control Center без тяжёлого blur и дополнительных GPU-эффектов.
- Настройки и стандартные панели дополнительно переведены на общие токены типографики, размеров и прозрачности.
- Заставка приведена к общей эстетике RFTV: согласованный градиент, стеклянная карточка данных и общие токены типографики/отступов.
- Сохранены существующие функции, настройки, OTA и механизмы безопасного управления пакетами.

## 0.6.0

- TV Manager получил Shizuku UserService для работы с привилегиями ADB shell/root после отдельной авторизации.
- Добавлена ручная очистка: поиск по пакетам, индивидуальные переключатели, предупреждения и блокировка защищённых Android/TV/OEM/SoC-компонентов.
- Добавлены сохранение исходного PackageManager enabled state, синхронный snapshot, журнал операций, проверка результата и автоматический откат при ошибке.
- Добавлено восстановление одного пакета и всех изменений RFTV в обратном порядке; ранее отключённые пользователем пакеты не затрагиваются.
- Добавлен автоматический пакетный механизм с fail-fast и reverse-order rollback. Он остаётся закрытым без точного проверенного профиля и явных optional/dependency-меток.
- Добавлен экспорт полного fingerprint устройства для подготовки проверенных профилей.
- Распознавание профиля усилено: производитель, модель, плата/hardware, device/product, API, точный Build fingerprint и полный набор ABI.
- Донорские профили CleanYourTV оставлены справочными и не могут автоматически отключать пакеты без дополнительной проверки.
- Добавлены динамическая защита HOME/Settings/клавиатуры/заставки, защита приостановленных пакетов и проверка UID Shizuku непосредственно перед операцией.
- Обновлена документация TV Manager; физическая проверка Shizuku и операций на целевом Sber TV ещё не выполнена.

## 0.5.0

- Переработан корневой экран настроек: боковая навигация слева, контекстная область справа, единый фокус и управление с пульта.
- Параметры категорий доступны прямо в правой области; Back возвращает в выбранную категорию и восстанавливает фокус.
- OTA сведено к одной команде «Скачать и установить»: RFTV само ищет последний стабильный Release, загружает APK, проверяет SHA-256/пакет/версию/ABI/сертификат и передаёт его штатному установщику Android.
- Dock и кнопка смены обоев синхронно меняют положение при изменении высоты и раскрытии Dock; стандартные панели используют общие размеры и card tokens.
- TV Manager получил объяснение назначения пакетов на основе PackageManager: наблюдаемые факты, категорию, уровень риска и уверенность.
- Чувствительные разрешения больше не трактуются как доказательство слежки или передачи данных.
- Защищённые системные идентификаторы отмечаются критическими; неизвестные пакеты остаются непроверенными.
- Добавлены unit-тесты аналитики пакетов и зафиксирован аудит донорских проектов.
- Отключение пакетов не включено; привилегированный мост ещё не реализован.

## 0.4.1

- Перестроено меню настроек: основные параметры разделены на понятные категории с отдельными экранами.
- Улучшены сценарии настройки внешнего вида, Dock, заставки и приложений с сохранением управления пультом.
- OTA-кнопка переименована в «Скачать и установить обновление»; после выдачи разрешения на установку процесс автоматически продолжается.
- Сохранены обязательные проверки SHA-256, package name, versionCode, ABI и сертификата до передачи APK системному установщику.

## 0.4.0

- Dock и каталог приложений автоматически обновляются после установки, удаления и обновления приложений без перезапуска лаунчера.
- При обновлении приложения сохраняются избранное и текущий фокус пульта; намеренно пустой список избранного больше не заполняется заново.
- Дата в ручной и системной заставках обновляется вместе с часами.
- Погодный экран использует ограниченные тайм-ауты сети и не перезаписывает новый экран устаревшим ответом.
- Визуальные параметры главного экрана, Dock и стеклянных поверхностей сведены в общую дизайн-систему без тяжёлого blur.
- Усилены unit-тесты политик каталога и визуальных параметров.

## 0.3.0

- Переработан Control Center: стеклянные плитки, управление с пульта и возврат фокуса.
- Добавлена полноэкранная заставка с часами, погодой, курсами ЦБ РФ и новостями.
- Добавлен настраиваемый таймер бездействия: выкл./5/15/30/60 минут.
- Добавлены кэширование данных заставки и резервный источник погоды.
- Усилен CI: Android lint, unit-тесты и проверка подписанного APK.
- Публикация OTA стала версионной: версия берётся из APK, проверяется увеличение versionCode и запрещена перезапись существующего Release.


## 0.2.0 (Release candidate)

- Обновлён главный экран с центральными часами, датой и автоматическими дневной/ночной темами.
- Добавлены независимые наборы дневных и ночных фотографических обоев, ручной выбор, карусель и локальный кэш.
- Улучшены Dock, каталог приложений, избранное, скрытие и сортировка.
- Добавлены быстрые настройки обоев в Control Center и улучшено управление фокусом с TV-пульта.
- Расширены сведения о приложениях и разрешениях в TV Manager.
- Усилен OTA-контур: проверка SHA-256, package name, versionCode и сертификата подписи до запуска системного установщика.
- Исправлены ошибки навигации и обновления интерфейса после изменения избранного и скрытых приложений.

> Release candidate. Перед официальной публикацией необходима проверка подписанного APK и приёмочное тестирование на целевом телевизоре.
