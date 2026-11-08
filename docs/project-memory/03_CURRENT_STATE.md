# Текущее состояние RFTV

**Дата проверки:** 2026-11-09  
**Репозиторий:** https://github.com/gonscharovd/rftv-launcher  
**Ветка:** `main`  
**HEAD:** `187239dd7f7518286fd0ffede03e6d5d54893ed5`  
**Последний commit:** `docs/ci: mark v0.3.0 complete and skip duplicate releases (#14)`

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
| ABI | ABI-independent по release metadata; native libraries не заявлены |
| Подпись | CI подтвердил постоянный Release certificate; секреты не хранятся в документации |

В Release также приложены `apk-metadata.txt`, `app-release.apk.sha256`, `signature.txt`. Источник: GitHub Release API/assets и существующий [ROADMAP](../../ROADMAP.md). Эти параметры — снимок; перед установкой/публикацией сверить актуальный Release.

## Реализовано по коду/документации

- Native Kotlin Android Views, launcher HOME/LEANBACK.
- Часы/дата, Dock и базовый домашний экран; Control Center.
- Каталог 20 Unsplash wallpaper URLs (10 дневных/10 ночных), выбор/карусель, локальный кэш описаны в README.
- Сохранение темы и размеров Dock.
- Read-only TV Manager inventory, fingerprint export, reference profiles.
- DreamService/screensaver prototype.
- OTA release lookup/verification/installer flow.
- Unit tests для дизайн-системы и wallpaper URL.

## Не реализовано / ограничено

- Нет привилегированного package disable/restore, action journal и rollback.
- Нет избранного, полноценной сортировки/скрытия и расширенного управления каталогом.
- Прямые Wi-Fi/Bluetooth/brightness toggles не подтверждены; элементы ведут к Android settings.
- Физическая работа заставки зависит от поддержки DreamService в StarOS.
- Конфигурация погоды/новостей/города для заставки частично жёстко задана.
- Нет подтверждения физической установки v0.3.0 через OTA на Sber TV и прохождения пользовательского чек-листа.

## Автоматические проверки

На HEAD `187239d`:
- Android CI run [42516761285](https://github.com/gonscharovd/rftv-launcher/actions/runs/42516761285): completed / success.
- Publisher run [42516905346](https://github.com/gonscharovd/rftv-launcher/actions/runs/42516905346): completed / success. По текущему workflow он может пропускать публикацию, если versionCode уже покрыт stable Release; success не означает новый APK.
- Release v0.3.0 ранее опубликован после успешного проверочного publisher run [42516320220](https://github.com/gonscharovd/rftv-launcher/actions/runs/42516320220) и CI [42516182919](https://github.com/gonscharovd/rftv-launcher/actions/runs/42516182919).

## Физическое тестирование

**Статус: НЕ ПОДТВЕРЖДЕНО.** Целевое устройство: Sber SDX-50U4123B / Salute TV (StarOS 1.103.91-b32273), Android 9/API 28, MT9632, ARMv7, около 1.36 GB RAM. Требуется пользовательская проверка v0.3.0 по [DEVICE_TESTING](../../DEVICE_TESTING.md) и чек-листу [ROADMAP](../../ROADMAP.md).

## Git / незавершённая работа

Ветки, найденные на момент аудита, включали несколько feature/fix/docs веток. Они не равны активным задачам: сравнивать каждую с main перед использованием. Все просмотренные PR были закрыты/слиты; открытых PR на момент запроса не выявлено по списку PR API. Issues: открытых issues не было в метаданных репозитория.

## Ограничения аудита

Аудит основан на доступном GitHub tree, файлах, commits, PR, workflow runs и Releases. Локальный checkout, локальные uncommitted changes, секреты Actions, реальные значения signing key и физическое устройство не доступны для проверки. Не утверждается, что тесты запускались локально в этой сессии.
