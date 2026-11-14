# Current project status — verified 2026-11-14 21:26 +05:00

- Repository: `gonscharovd/rftv-launcher`; `main` HEAD: `4981dce3629592ac1edb243ba2e6c7cae2fe23fa`.
- Latest stable Release: [v0.9.4](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.9.4), versionCode 16; APK size 7,993,855 bytes; SHA-256 `398ec55a1ce62cccde33afccc7827356016c145c1e097e745b4551457a1174c7`.
- Release is published, `draft=false`, `prerelease=false`; the permanent signing certificate SHA-256 is `22b4f71da5f543e933780f05f8573783ad4718036b5bfc1b36fe258695e30917`.
- Active visual-shell work: draft [PR #43](https://github.com/gonscharovd/rftv-launcher/pull/43), branch `feature/visual-shell-execution`, head `7f65d8a0c4acda26e416084ffdc065f23fb39402` before the current follow-up commits.
- PR #43 already contains shared glass tokens/materials, the two-column Control Center, five layered scenes (14 offline image assets), the clock shader, Auto/Photo/manual scene selection and Settings parent focus/scroll restoration.
- Current PR CI #43616405382 passed: wallpaper and pinned scene asset packaging, unit tests, Android lint, Debug build, package/SDK metadata and APK checks for all 20 wallpapers plus 14 scene assets. Signed Release verification is skipped on feature branches by design; signed artifact verification will run on main after merge.
- Scene teardown now detaches ImageViews/shaders before recycling bitmaps. Existing dialogs share the glass theme; secondary panels and Settings have consistent reveal transitions. Donor scene assets are pinned to immutable upstream commit `463fa6c2a9b7322c4407f1d00db935ef09d20f4f`. Documentation is reconciled with stable v0.9.4.
- Candidate version is being advanced to v0.9.5 / versionCode 17 so it can upgrade stable v0.9.4. No candidate Release has been published.
- Physical acceptance on Sber SDX-50U4123B has not been confirmed. Do not claim TV testing.

## Immediate work order

1. Finish Dock/Settings visual refinement and DPAD/focus regression review; existing dialogs and secondary-panel reveal transitions are now unified.
2. Run current-head GitHub Actions; fix any failing source, lint, asset or build checks.
3. Merge PR #43 only after required checks pass.
4. Verify the actual signed v0.9.5 APK: package, version, versionCode 17, ABI, size, SHA-256 and permanent certificate.
5. Publish the stable Release and verify its assets/OTA metadata. If signing or artifact verification fails, do not publish.
6. After the user updates the TV through OTA, complete the physical acceptance checklist in DEVICE_TESTING.md.

Historical roadmap and release notes follow. Treat dated status snapshots below as history, not as current repository state.

# RFTV Launcher — Product Roadmap

## Owner-approved direction — 2026-11-10

The primary product is a lightweight, premium-feeling Android TV launcher for a range of Android TV devices. The Sber SDX-50U4123B / StarOS 1.103.91 / Android 9 / API 28 / MT9632 / ARMv7 / approximately 1.4 GB RAM is the main physical acceptance target, not the only supported device.

### Product requirements

- Home: large iOS-style clock embedded between a full-screen photo background and a separate transparent foreground cut-out that overlaps the clock. Start with 3–5 layered scenes, not a 20-photo flat library.
- Weather beside the clock. City and provider sources must be configurable.
- Dock: favourites plus All Apps. All Apps opens a grid over a full-screen blurred background.
- Gear opens Control Center over a full-screen blurred background.
- Settings: tvOS-style sidebar and right content area, with nested focus position restored on return.
- Screensaver: clock/date, weather, CBR currency rates, Russia/world news; slow clock drift to reduce burn-in; launch manually from Control Center and through Android DreamService where firmware supports it; remote key dismisses it where key events are delivered.
- TV Manager remains in the repository but is lower priority and frozen for feature development while launcher work proceeds.

### Agreed implementation order

1. Measure current behavior on the target TV: memory, frame pacing, cold launch, overlay open time and blur cost. Distinguish measured values from estimates. No physical measurements are currently recorded.
2. Full-screen blur for All Apps and Control Center.
3. Layered photographic scenes and clock-between-layers composition.
4. Settings focus restoration.
5. Screensaver polish, including burn-in drift and DreamService remote behavior.

Stability, remote navigation and performance remain constraints throughout. No new TV Manager feature work until the primary launcher scope is complete.

## Historical release baseline — v0.9.0

- Stable Release: [v0.9.0 / versionCode 12](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.9.0), published 2026-11-10.
- Android CI run 42810753407 and publisher run 42811138590 are recorded as successful in the release audit.
- APK SHA-256: `d4a2941add01cfb6c7c56845005f02939eceef3ed9c05c2e04b8c0839539e31c`.
- Release certificate SHA-256: `22b4f71da5f543e933780f05f8573783ad4718036b5bfc1b36fe258695e30917`.
- Physical acceptance of v0.9.0 on Sber SDX-50U4123B has not been confirmed. The owner reported v0.8.0 visual, wallpaper and weather failures; v0.9.0 contains automated remediation, but no claim is made that those issues are resolved on the TV.
- Detailed artifact evidence: [docs/V0.9.0_RELEASE_AUDIT.md](docs/V0.9.0_RELEASE_AUDIT.md). Physical checklist: [DEVICE_TESTING.md](DEVICE_TESTING.md).

## Shipped v0.9.0 versus target design

v0.9.0 contains 20 flat bundled photographs, a centered clock over one photograph, a fixed Chelyabinsk weather location with three-provider fallback/cache, a snapshot blur implementation, a floating Control Center, a Settings sidebar, and a manual/system screensaver dashboard. These are existing implementation facts, not proof that the new owner-approved depth composition, configurable weather, measured blur performance, full nested focus restoration, or burn-in drift are complete.

The prior roadmap's statement that development should stop after v0.9.0 acceptance and only fix regressions is superseded by the owner-approved implementation order above. The v0.8.0 and earlier release audit material below is retained as historical evidence only.

## Safety and release rules

- Keep CI verification separate from physical TV acceptance.
- Never claim a physical test without owner confirmation.
- Measure memory, frame pacing and blur cost on target hardware; do not infer them from source code or CI.
- Keep minSdk 24 and native Kotlin Android Views unless a concrete compatibility reason requires change.
- Release only with increased versionCode, the persistent signing certificate, and verified package/version/SDK/ABI/size/SHA-256/certificate/artifact identity.
- Never replace the persistent signing key, delete user data, reset the TV or disable packages without the required safety process.
- TV Manager's existing code is retained but is not the current development priority.



## Архив предыдущих этапов

Ниже сохранена прежняя дорожная карта и release-аудиты как историческая запись. Её старые планы и формулировки статуса не заменяют актуальное направление и порядок работ в начале этого файла.

## Исторический Release Audit — RFTV 0.8.0

- **Исходники:** PR [#34](https://github.com/gonscharovd/rftv-launcher/pull/34) слит в `main` 2026-11-09. Merge commit: `86b9d3db913f3abe512770fcb88a7058b8d0a50c`.
- **Автоматические проверки:** GitHub Actions [Android CI #42619490305](https://github.com/gonscharovd/rftv-launcher/actions/runs/42619490305) — success. Успешны проверка 20 фотоисточников с декодированием, unit tests, Android lint, Debug build/metadata и сборка/проверка подписанного Release APK.
- **Публикация:** [стабильный Release v0.8.0](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.8.0), draft=false, prerelease=false. Публикация [workflow #42619792984](https://github.com/gonscharovd/rftv-launcher/actions/runs/42619792984) — success.
- **APK:** `com.rftv.launcher`, versionName `0.8.0`, versionCode `11`, minSdk `24`, targetSdk `28`, native-библиотеки отсутствуют (APK ABI-independent, ARMv7-совместимость не ограничивается отдельной ABI-библиотекой). Размер 817 577 байт.
- **SHA-256 APK:** `1ab18c47b66bf3b45636b7e4fa97c516397be5105a66aa71a790917ec2df2f0a`.
- **Release certificate SHA-256:** `22b4f71da5f543e933780f05f8573783ad4718036b5bfc1b36fe258695e30917` — постоянный сертификат.
- **Сверка CI → Release:** publisher скачал signed artifact именно из Android CI run #42619490305, сверил его SHA-256, метаданные, сертификат и размер; затем сверил digest/size загруженного Release APK с проверенным артефактом до публикации stable. Publish workflow завершился успешно.
- **OTA endpoint:** `/releases/latest` сейчас возвращает `v0.8.0`, draft=false, prerelease=false. Клиент RFTV читает этот endpoint, требует SHA-256 из asset digest, проверяет package, увеличенный versionCode, сертификат установленного приложения и наличие ARMv7 ABI при наличии native-библиотек, затем передаёт APK штатному Android PackageInstaller.
- **Физическая приёмка:** НЕ ПРОВЕДЕНА. Никакие DPAD/OEM/blur/performance/DreamService/Shizuku/OTA-install сценарии на физическом ТВ не отмечаются пройденными без подтверждения владельца.

Этот раздел — исторический аудит v0.8.0. Текущий стабильный релиз и физический статус приведены в начале документа.


### Этап 1. Release pipeline и интеграция PR #7 — ВЫПОЛНЕН

Фактически выполнено:
- Release publisher получает versionName/versionCode из проверенного APK metadata, не привязан к v0.2.0.
- Публикация защищена от перезаписи стабильного Release; versionCode сравнивается с последней стабильной версией.
- Проверяются package, minSdk, targetSdk, ABI, размер, SHA-256, постоянный сертификат и результат `apksigner`.
- APK публикуется сначала как draft; assets проверяются по SHA-256/размеру и только после этого Release становится stable.
- При повторной сборке workflow обновляет assets только внутри неопубликованного draft; уже опубликованный Release не перезаписывается.
- Повторный запуск с уже выпущенным versionCode безопасно пропускает публикацию.
- В CI включены unit tests, Android Lint, Debug metadata и отдельная signed Release verification.
- PR #7 слит; исправления баннера/Typeface включены; Control Center и заставка прошли автоматические проверки.
- Опубликован стабильный Release **v0.3.0 / versionCode 3**; APK и три контрольных файла доступны в GitHub Releases.

**Критерий этапа 1 выполнен:** новый стабильный Release опубликован, APK и assets проверены, OTA endpoint обнаруживает v0.3.0. Физический тест ТВ остаётся отдельным пунктом этапа 2.

### Этап 2. Приёмка на целевом ТВ и стабилизация
Цель: подтвердить работу уже реализованных функций на Sber SDX-50U4123B / StarOS / Android 9.

Проверить:
- запуск, часы, дата, погода и системные индикаторы;
- 10 дневных и 10 ночных обоев, выбор, карусель, автоматическая тема, кэш и офлайн;
- Dock, каталог, избранное, сортировка, скрытие и запуск приложений;
- Control Center: открытие справа, размер, все плитки, фокус внутри, возврат фокуса;
- DPAD, OK/Enter, Back, длинные сессии и отсутствие зависаний;
- ручной и автоматический запуск заставки, таймер, выход любой кнопкой, DreamService на данной прошивке;
- OTA и сохранность пользовательских настроек после обновления.

Все дефекты, обнаруженные при приёмке, исправлять в отдельном feature/fix PR и включать в ближайший Release.

Критерий завершения: пользователь подтвердил прохождение чек-листа на ТВ; критические дефекты отсутствуют.

### Этап 3. Главный экран и визуальная система
Цель: довести уже согласованную концепцию RFTV Liquid Glass до цельного премиального вида без потери производительности.

Работы:
- унифицировать токены цветов, прозрачности, размеров, отступов, радиусов, теней, типографики и фокуса;
- проверить часы по центру и их читаемость на всех дневных/ночных фонах;
- проверить корректное переключение день/ночь/авто/ручной режим;
- устранить повторные загрузки изображений при тиках часов, мерцание и лишнюю перерисовку;
- проверить кэш, очистку/ограничение хранения и fallback при недоступной сети;
- реализовать и измерить Liquid Glass backdrop blur: Android 9 snapshot/downsample/box-blur pipeline и нативный blur на поддерживаемых API; не выполнять дорогой blur на каждом кадре.

Критерий завершения: стабильная отрисовка, корректные настройки и плавная работа на целевом ТВ.

### Этап 4. Dock и каталог приложений
Цель: надёжное управление большим количеством приложений с пульта.

Работы:
- проверить обнаружение установки/удаления/обновления приложений;
- сохранить порядок и избранное после перезапуска и OTA;
- проверить скрытие, сортировку, заполнение Dock и переходы в каталог;
- проверить DPAD/OK, длинные списки, начальный и возвратный фокус;
- исключить запуск неактивных или отсутствующих пакетов и корректно обновлять каталог.

Критерий завершения: каталог остаётся актуальным, все действия доступны с пульта, настройки сохраняются.

### Этап 5. TV Manager — безопасный анализ устройств и пакетов
Цель: превратить справочную инвентаризацию в понятный и безопасный менеджер без риска сломать ТВ.

Работы:
- улучшить описание назначения пакетов, доказательства, уверенность и последствия; отделять факты от предположений;
- собирать профиль конкретной модели/прошивки; не применять чужие профили автоматически;
- исследовать реальные возможности Android 9/StarOS для привилегированного управления; не обещать операции, недоступные обычному приложению;
- до любых действий спроектировать предпросмотр, точную запись исходного состояния, журнал, пакетное поэтапное применение, проверку результата и rollback;
- защищать HOME/Launcher, System UI, Settings, Package Installer, сеть, аудио/видео, пульт и OEM/SoC-службы;
- не удалять пакеты и не отключать автоматически критические, неизвестные, зависимые или низкоуверенные компоненты;
- восстановление должно вернуть исходные состояния, включая пакеты, уже отключённые пользователем до работы RFTV.

Критерий завершения: сначала read-only анализ и тесты; изменение системных пакетов допускается только после доказанной совместимости, обратимого механизма и отдельной проверки на точной модели.

### Этап 6. Производительность, совместимость и безопасность OTA
Цель: укрепить продукт для широкого спектра Android TV.

Работы:
- профилировать память, CPU/GPU, фоновые задачи, сеть и утечки;
- проверять API 24–35, OEM-различия, ARMv7/ARM64 и отсутствие неподдерживаемых API;
- обрабатывать отсутствие сети, таймауты, ошибки провайдеров, пустые ответы и повреждённый кэш;
- проверять OTA на отсутствие релизов, prerelease/draft, неподходящую ABI, неверный hash/подпись, отказ пользователя и ошибки установки;
- сохранять настройки при каждом обновлении и не терять постоянный signing key;
- расширять автоматические тесты для критических сценариев.

Критерий завершения: повторяемый CI, безопасная публикация и OTA, отсутствие регрессий на поддерживаемых конфигурациях.

## Очерёдность и ближайшее действие

Работаем строго по порядку: **1 → 2 → 3 → 4 → 5 → 6**. Исправления безопасности и блокирующие OTA-дефекты имеют право приоритета над очередностью.

Историческая запись на момент v0.9.0: физическая приёмка этого релиза ожидалась отдельно. Актуальный статус и ближайшие действия см. в блоке Current project status в начале файла.


## Донорские проекты и анализ Aerial TV

### Aerial TV — https://github.com/pengchujin/aerial-tv
Дата первичного анализа: 2026-11-09. Репозиторий активен; проверенная на дату анализа версия — 1.88. Лицензия репозитория — MIT. Основной стек донора: Kotlin + Jetpack Compose, minSdk 21, targetSdk 34; RFTV остаётся на Kotlin + Android Views, поэтому переносим идеи и алгоритмы, а не UI-компоненты напрямую.

Что изучать и использовать как технические ориентиры:
- **Каталог и плитки приложений:** объединение LEANBACK_LAUNCHER/LAUNCHER без дублей; кэш готовых изображений плиток на диске с ключом от времени обновления приложения, размера, локали, стиля и версии; приоритетная подготовка первых видимых плиток до полной загрузки каталога.
- **Организация Dock/каталога:** сохранение порядка, папок и скрытых приложений по package name; временно отсутствующие приложения не теряют своё место и возвращаются в прежнюю позицию.
- **Пульт и фокус:** отдельные pressable-компоненты, контролируемая обработка DPAD/OK/Back, проверка повторных событий и удержания кнопок; изучать как UX/тестовые сценарии, не копировать Compose-код.
- **Control Center:** группировка системных действий, сигналы источников, настройки, звук/яркость и персонализация; каждое действие должно проверять реальную возможность на текущей модели и иметь честный fallback.
- **Обои и заставка:** единый источник/позиция воспроизведения для фонового видео и заставки, освобождение Media3/декодера при смене окон, переход без чёрного кадра; локальные видео и fallback при ошибках декодирования.
- **Производительность:** файловый кэш плиток, предварительная загрузка только видимой части, Baseline Profile, измерение frame time/p90/p99 и раздельная диагностика холодного запуска и стабильной работы.
- **Профили устройств:** данные по конкретным моделям/прошивкам отдельно от общей логики, возможность переопределения профиля конкретного ТВ, документирование источника и фактической проверки каждого workaround.
- **Диагностика:** формировать отчёт с моделью, прошивкой, API, ABI, разрешением, памятью, HOME-разрешением и состоянием функций; исключать личные данные, SSID, адреса, токены и полный список приложений.

Ограничения и запреты при адаптации:
- В Aerial есть HOME Guard на AccessibilityService, Notification Listener, Usage Stats, WRITE_SECURE_SETTINGS, SYSTEM_ALERT_WINDOW и механизмы повторного запуска. Это существенно повышает привилегии и может вмешиваться в работу OEM. Не переносить и не включать такую защиту автоматически; сначала доказать необходимость, совместимость, поведение с DPAD и возможность полного отключения.
- Сам Aerial документирует случаи, когда фильтрация клавиш вызывает двойное перемещение/неверное удержание кнопок, а OEM может очищать службы. Поэтому не считать guard универсальным решением и не вмешиваться в системный HOME на Sber/StarOS без отдельного исследования.
- В репозитории есть Android 9 / API 28 / 32-bit ARM опыт на отдельных устройствах, но это не подтверждает совместимость с Sber SDX-50U4123B, StarOS 1.103.91 или MediaTek MT9632. Нужен отдельный профиль и проверка на нашем ТВ.
- Вендорские операции яркости, HDMI/TV input, системных настроек и пробуждения применять только при подтверждённом совпадении модели/прошивки; названия бренда или SoC недостаточно.
- Не переносить целиком архитектуру, зависимости, Compose UI, updater или установочные скрипты. RFTV сохраняет свой стек, package name, minSdk/targetSdk, signing key, OTA и собственную навигацию.
- MIT разрешает адаптацию при соблюдении условий лицензии. При фактическом заимствовании исходного кода сохранить copyright/license notice и зафиксировать происхождение изменённого файла/фрагмента; отдельно проверить лицензии ресурсов, шрифтов, иконок и зависимостей.

**Решение для RFTV:** Aerial TV принят как третий донор для анализа. На ближайшем этапе используем его только для технического аудита и идей по кэшу плиток, каталогизации, фокусу, заставке, профилям устройств и диагностике. Это не согласие на добавление новых пользовательских разделов или на внедрение Home Guard. Новые функции по-прежнему согласуются отдельно.


## 0.9.0 — tvOS visual redesign and media reliability (released)

- Визуальный референс: tvOS Launcher (Apache-2.0) — крупная типографика, тёмные стеклянные поверхности, dock с чистыми иконками и выразительным фокусом, плавающий Control Center, двухколоночные Settings. Переносится дизайн-язык, не функциональность донора.
- Home: часы — главный центральный элемент на полноэкранной фотографии; погода — компактный интерактивный pill.
- Dock: единая стеклянная капсула; невыбранные иконки без индивидуальных рамок; подпись появляется у элемента в фокусе.
- Control Center: отдельная плавающая панель справа, нижняя граница выше Dock.
- Обои: все 20 настоящих Unsplash-фото пакуются в APK на этапе сборки и восстанавливаются в локальный кэш; picker preview создаётся из локального full image.
- Погода: Open-Meteo → MET Norway → wttr.in, общий кэш для Home/прогноза/заставки.
- versionName 0.9.0 / versionCode 12. Stable Release опубликован 2026-11-10. Физическая приёмка v0.9.0 не проводилась.
