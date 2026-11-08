# Текущее состояние RFTV

**Дата проверки:** 2026-11-09 02:05 (UTC+05:00)  
**Репозиторий:** https://github.com/gonscharovd/rftv-launcher  
**Основная ветка:** `main`  
**Main HEAD:** `187239dd7f7518286fd0ffede03e6d5d54893ed5`  
**Последний commit main:** `docs/ci: mark v0.3.0 complete and skip duplicate releases (#14)`

## Версия и Release

Последний stable GitHub Release (не draft/prerelease): [v0.3.0](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.3.0), опубликован 2026-11-08.

| Поле | Значение |
|---|---|
| applicationId | `com.rftv.launcher` |
| versionName / versionCode | `0.3.0 / 3` |
| minSdk / targetSdk | 24 / 28 |
| APK asset | `app-release.apk` |
| Размер | 711 225 bytes |
| SHA-256 | `8c1ebb2d574959badd56f7b88104cea936ff5b48d3b7f6c25cfe754400e0a905` |
| ABI | В APK нет native libraries; ABI-independent |
| Подпись | Publisher проверил постоянный сертификат с ожидаемым SHA-256 digest; значения ключа/секретов не раскрываются |

Assets релиза: `app-release.apk`, `app-release.apk.sha256`, `apk-metadata.txt`, `signature.txt`. Эти сведения — снимок; перед новой публикацией снова сверять API Releases и CI artifacts.

## GitHub: ветки, PR и commits

- `main` на HEAD `187239d`.
- PR #7 (Control Center + заставка) — merged в main.
- PR #9 (анализ Aerial TV) — merged.
- PR #10 (Android TV banner / MissingTvBanner lint fix) — merged в PR #7, итоговый код в main.
- PR #11–#14 (Release workflow fixes, idempotent versionCode handling и roadmap update) — merged.
- **PR #15 — открыт:** [docs: add portable project memory system](https://github.com/gonscharovd/rftv-launcher/pull/15), ветка `docs/project-memory-system`, HEAD `138948825ea50235f2c54b211c2f96ba51f1761f`, base `main` @ `187239d`. В нём 11 новых Markdown-файлов `docs/project-memory/`, без изменений Android-кода и workflows. Merge ещё не выполнен.
- Найденные старые feature/fix/docs ветки не считать активными без сравнения с main.

## Реализовано по коду/документации

- Нативный Kotlin Android Views launcher HOME/LEANBACK.
- Главный экран с часами, датой, погодным entry point, Dock и темами.
- 20 фотографических Unsplash wallpaper URLs (10 дневных/10 ночных), выбор/карусель и локальный кэш.
- Control Center, системные shortcuts и настройка темы/размеров Dock.
- Read-only TV Manager inventory, fingerprint export и reference profiles.
- DreamService/screensaver prototype + отдельная ScreensaverActivity.
- OTA release lookup/verification и запуск штатного Android installer.
- Unit tests для дизайн-системы и wallpaper URL/cache logic.

## Не реализовано / ограничения

- Нет привилегированного package disable/restore, action journal и rollback.
- Нет избранного, пользовательской сортировки/скрытия приложений и полноценного редактора внешнего вида.
- Wi-Fi/Bluetooth/brightness shortcuts открывают системные настройки; прямые toggles не обещаются.
- DreamService зависит от поддержки в StarOS и требует проверки на устройстве.
- Конфигурация города/погодных/новостных источников заставки частично жёстко задана.
- Нет подтверждения физической установки v0.3.0 через OTA на Sber TV и прохождения пользовательского чек-листа.

## Автоматические проверки

- Main Android CI [42516761285](https://github.com/gonscharovd/rftv-launcher/actions/runs/42516761285), SHA `187239d`: completed / success; source tests + Debug verification и signed Release APK verification прошли.
- Main publisher [42516905346](https://github.com/gonscharovd/rftv-launcher/actions/runs/42516905346), SHA `187239d`: completed / success; относится к публикации v0.3.0.
- Последний publisher запуск [42520472135](https://github.com/gonscharovd/rftv-launcher/actions/runs/42520472135), SHA `187239d`: skipped. Это безопасное поведение повторного запуска, когда versionCode уже покрыт stable Release; это не новая сборка и не новый Release.
- PR #15 CI [42520736798](https://github.com/gonscharovd/rftv-launcher/actions/runs/42520736798), HEAD `1389488`: completed / success; Source tests and Debug verification прошли, включая unit tests, Android Lint, Debug build и metadata verification. Signed Release job skipped, как ожидается для pull_request.
- Предыдущий CI PR #15 [42520315995](https://github.com/gonscharovd/rftv-launcher/actions/runs/42520315995) на HEAD `04bda5b` — success; ещё более ранний run `42520304453` на `5b107fa` — success.
- CI PR #15 проверяет исходники/Debug и не является проверкой подписанного Release APK или физическим тестом ТВ.

## Физическое тестирование

**Статус: НЕ ПОДТВЕРЖДЕНО.** Целевое устройство: Sber SDX-50U4123B / Salute TV StarOS 1.103.91-b32273, Android 9/API 28, MT9632, ARMv7, около 1.36 GB RAM. Нужна установка стабильного v0.3.0 через OTA и прохождение [DEVICE_TESTING](../../DEVICE_TESTING.md). CI не заменяет физическую проверку.

## Ограничения аудита

Проверены GitHub tree, main/PR branches, commits, PR metadata/files, workflow runs/jobs, Release API/assets и ключевые исходники/документы. Локальный checkout, пользовательские незакоммиченные изменения, значения Actions secrets/keystore и физическое устройство недоступны для проверки. Локальная сборка в этой сессии не запускалась.
