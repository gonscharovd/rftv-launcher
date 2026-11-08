# Текущее состояние RFTV

**Дата проверки:** 2026-11-09 02:15 (UTC+05:00)  
**Репозиторий:** https://github.com/gonscharovd/rftv-launcher  
**Основная ветка:** `main`  
**Main HEAD:** `187239dd7f7518286fd0ffede03e6d5d54893ed5`  
**Последний commit main:** `docs/ci: mark v0.3.0 complete and skip duplicate releases (#14)`

## Версия и Release

Последний stable GitHub Release: [v0.3.0](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.3.0), опубликован 2026-11-08.

| Поле | Значение |
|---|---|
| applicationId | `com.rftv.launcher` |
| versionName / versionCode | `0.3.0 / 3` |
| minSdk / targetSdk | 24 / 28 |
| APK asset | `app-release.apk` |
| Размер | 711 225 bytes |
| SHA-256 | `8c1ebb2d574959badd56f7b88104cea936ff5b48d3b7f6c25cfe754400e0a905` |
| ABI | В APK нет native libraries; ABI-independent |
| Подпись | Publisher проверил постоянный сертификат; секреты и ключи не раскрываются |

Assets: `app-release.apk`, `app-release.apk.sha256`, `apk-metadata.txt`, `signature.txt`. Перед новой публикацией все параметры нужно перепроверить по текущим assets и CI.

## GitHub: ветки, PR и commits

- `main` HEAD: `187239dd7f7518286fd0ffede03e6d5d54893ed5`.
- PR #7 (Control Center + заставка), #9 (Aerial TV analysis), #10 (banner/Lint fix) и #11–#14 (Release workflow/roadmap) — merged.
- **PR #15 открыт:** [docs: add portable project memory system](https://github.com/gonscharovd/rftv-launcher/pull/15), ветка `docs/project-memory-system`, HEAD `53fff592d22f00b24f5a1f5a34f0d466ac39af28`, base `main` @ `187239d`. 11 новых Markdown-файлов только в `docs/project-memory/`; Android-код, build files и workflows не менялись. PR намеренно не объединён.
- Старые ветки не считать активными без свежей проверки GitHub.

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
- DreamService зависит от поддержки в StarOS и требует физической проверки.
- Конфигурация города/погодных/новостных источников заставки частично жёстко задана.
- OTA-установка и пользовательская приёмка v0.3.0 на Sber TV не подтверждены.

## Автоматические проверки

- Main Android CI [42516761285](https://github.com/gonscharovd/rftv-launcher/actions/runs/42516761285), SHA `187239d`: success; source tests/Debug verification и signed Release APK verification прошли.
- Main publisher [42516905346](https://github.com/gonscharovd/rftv-launcher/actions/runs/42516905346): success, связан с публикацией v0.3.0.
- Publisher [42520472135](https://github.com/gonscharovd/rftv-launcher/actions/runs/42520472135): skipped, поскольку versionCode 3 уже покрыт stable Release; это не новая сборка/публикация.
- PR #15 CI [42520736798](https://github.com/gonscharovd/rftv-launcher/actions/runs/42520736798), HEAD `1389488`: success.
- **Последний PR #15 CI [42521038655](https://github.com/gonscharovd/rftv-launcher/actions/runs/42521038655), HEAD `53fff59`: completed / success.** Unit tests, Android Lint, Debug build и Debug metadata verification прошли. Signed Release APK verification job skipped, ожидаемо для pull_request.
- CI подтверждает автоматические проверки исходников/Debug, но не подписанный APK для этого PR и не физическое поведение на ТВ.

## Физическое тестирование

**Статус: NOT VERIFIED.** Целевое устройство: Sber SDX-50U4123B / Salute TV StarOS 1.103.91-b32273, Android 9/API 28, MT9632, ARMv7, около 1.36 GB RAM. Требуется установить stable v0.3.0 через OTA и пройти [DEVICE_TESTING](../../DEVICE_TESTING.md). CI не заменяет тест на устройстве.

## Ограничения аудита

Проверены GitHub tree, main/PR branches, commits, PR metadata/files, workflow runs/jobs, Release API/assets и ключевые исходники/документы. Локальный checkout, Actions secrets/keystore и физическое устройство недоступны. Локальная сборка в этой сессии не запускалась.
