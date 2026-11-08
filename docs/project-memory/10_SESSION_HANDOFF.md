# Session Handoff

**Последнее обновление:** 2026-11-09  
**Проект:** RFTV Launcher  
**Репозиторий:** https://github.com/gonscharovd/rftv-launcher

## Цель сессии

Проверить завершение CI на актуальном PR #15 HEAD, обновить переносимую память и подготовить следующий этап дорожной карты. PR #16 с off-device оптимизацией главного экрана прошёл CI и объединён. Физическая приёмка v0.3.0 не выполнялась.

## Прочитанные документы

Прочитан `00_START_HERE.md`, затем все документы `01_PROJECT.md`–`10_SESSION_HANDOFF.md`, включая `03_CURRENT_STATE.md`, `05_TASKS.md`, `09_AI_WORKING_RULES.md` и этот handoff.

## Актуальная точка GitHub

- Main HEAD: `3b871fd2916ffe8b05fc8c3ee57e0451bab8c1b2`.
- PR #15 открыт: [docs: add portable project memory system](https://github.com/gonscharovd/rftv-launcher/pull/15), ветка `docs/project-memory-system`, base main @ `0b1815b`; синхронизирован с main и намеренно не объединён. Текущий HEAD смотреть в live PR, не использовать исторический SHA из handoff.
- PR #15: 11 новых Markdown-файлов только в `docs/project-memory/`; app source, Gradle/build config и workflows не затронуты.
- PR #7, #9–#14, #16 и #17 — merged; PR #15 — open.
- CI [42520736798](https://github.com/gonscharovd/rftv-launcher/actions/runs/42520736798) на прежнем HEAD `1389488`: success.
- **Последний CI [42521038655](https://github.com/gonscharovd/rftv-launcher/actions/runs/42521038655) на текущем HEAD `53fff59`: completed / success.** Source tests and Debug verification — success; unit tests, Android Lint, Debug build и metadata verification — success. Signed Release APK verification — skipped для pull_request, ожидаемо.
- Main Android CI [42516761285](https://github.com/gonscharovd/rftv-launcher/actions/runs/42516761285) — success, включая signed Release verification.
- Main publisher [42516905346](https://github.com/gonscharovd/rftv-launcher/actions/runs/42516905346) — success, связан с публикацией v0.3.0. Повторный publisher [42520472135](https://github.com/gonscharovd/rftv-launcher/actions/runs/42520472135) пропущен из-за уже опубликованного versionCode 3.
- Stable Release остаётся v0.3.0 / versionCode 3. PR #16 не публиковал новый Release.

## Сверка реализации

- README подтверждает 20 Unsplash wallpaper images, выбор/карусель, private cache, Control Center, Dock, read-only TV Manager и ограничения.
- `TvManagerProfiles.kt`/TV_MANAGER подтверждают reference profiles; соответствие профиля целевому SDX-50U4123B не подтверждено.
- `MainActivity.kt`, `RftvDreamService.kt`, `ScreensaverActivity.kt` и manifest подтверждают UI/screensaver implementation structure; физическая работа DreamService на StarOS — NOT VERIFIED.
- Gradle/manifest: Kotlin Android Views, applicationId `com.rftv.launcher`, minSdk 24, targetSdk 28, compileSdk 35.
- Release workflow: draft → asset checks → stable publish, versionCode и постоянный сертификат; повторный versionCode безопасно пропускается.

## Изменения в этой сессии

Изменения продукта:
- PR #16 merged: wallpaper drawable/image pipeline не затрагивается на неизменившемся clock tick; добавлены unit tests.
- PR #17 merged: home layout tokens централизованы в `RftvDesignSystem`; дублирующие initial typography assignments удалены.
- Root `ROADMAP.md` разрешает off-device этап 3 при сохранении физической приёмки этапа 2 как NOT VERIFIED.
- Project Memory PR #15 обновляется отдельно; после PR #17 ветка синхронизируется с новым main; PR остаётся открытым.

## Проверки и границы

- PR #15 CI run 42521332412 завершён success на прежнем HEAD.
- PR #16 CI run 42521796760 завершён success: unit tests, Android Lint, Debug build и metadata verification.
- Push CI run 42521760732 завершён success, включая signed Release verification.
- Main CI run 42521971740 завершён success, включая signed Release verification.
- PR #17 CI run 42522491746 завершён success; main CI после merge PR #17 [42522675900](https://github.com/gonscharovd/rftv-launcher/actions/runs/42522675900) был in progress.
- Signed Release job skipped на pull_request; run не создавал и не проверял подписанный Release APK.
- Локальная сборка не запускалась.
- Физическая установка/OTA и приёмка на Sber SDX-50U4123B — NOT VERIFIED. CI не является тестом телевизора.
- Secrets/keystore не читались и не выводились.

## NEXT ACTION

Продолжать off-device этап 3 (главный экран/визуальная система) отдельными PR с unit tests, lint и CI. Не объявлять этап 2 завершённым: после появления доступа к Sber SDX-50U4123B установить stable v0.3.0 через OTA, пройти [DEVICE_TESTING](../../DEVICE_TESTING.md) и устранить P0-дефекты. После этапа 3 перейти к этапу 4 Dock/каталога. PR #15 оставить OPEN до отдельного решения Product Owner; CI не считать физической приёмкой.
