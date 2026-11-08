# Значимые изменения проекта

Краткий журнал собран по Git history, PR, существующему [CHANGELOG.md](../../CHANGELOG.md) и stable Releases. Это не замена официальному changelog.

## 2026-11-08 — v0.3.0

- Control Center: исправления DPAD-навигации, переработанный интерфейс.
- Добавлен интерактивный прототип заставки / DreamService.
- Проведён анализ Aerial TV donor; документация о доноре в репозитории.
- Android TV banner и lint fixes.
- Release workflow: явный repo context для gh CLI, корректная работа с draft по ID, обновление assets только в unpublished draft, безопасный skip уже выпущенного versionCode.
- PR #7, #9, #10–#14 включены в историю main (см. PR и commits).
- Stable Release: [v0.3.0](https://github.com/gonscharovd/rftv-launcher/releases/tag/v0.3.0).

## 2026-11-09 — P1 home performance

- PR #16 merged: clock tick больше не пересоздаёт wallpaper drawable и не запускает image pipeline, если выбранная day/night wallpaper не изменилась.
- Добавлены unit tests для selection key и отрицательного сохранённого индекса.
- Физическая производительность и поведение на Sber TV остаются NOT VERIFIED.

## Ранее — v0.2.0

- Ранее опубликованный stable Release v0.2.0; заменён v0.3.0 как latest stable.
- Подробности сравнения: https://github.com/gonscharovd/rftv-launcher/compare/v0.2.0...v0.3.0.

## Источники истории

- [GitHub commits](https://github.com/gonscharovd/rftv-launcher/commits/main)
- [Pull requests](https://github.com/gonscharovd/rftv-launcher/pulls?q=is%3Apr)
- [Releases](https://github.com/gonscharovd/rftv-launcher/releases)
- [Root changelog](../../CHANGELOG.md)

При каждом релизе добавлять версию, дату, пользовательские изменения, технические/архитектурные изменения и ссылки на PR/commit. Не дублировать все строки официального changelog.

## 2026-11-09 — Сверка правил и продуктовых границ

- Уточнены полномочия ИИ: полная техническая и операционная автономность; единственное предварительное согласование — новая пользовательская функциональность/разделы/сценарии и изменение концепции.
- Уточнён статус доноров: исследовательские источники, не backlog и не спецификация; автоматический перенос функций исключён.
- Синхронизирован README по избранному и скрытым приложениям с фактической реализацией MainActivity.kt.
- Синхронизирован Roadmap: независимая инженерная работа не блокируется очередностью этапов, физическая приёмка остаётся отдельным acceptance gate.

## 2026-11-09 — Этап 3: централизация layout tokens

- Commit `3b871fd`: часть размеров/отступов главного экрана перенесена в RftvDesignSystem; добавлены проверки токенов. MainActivity использует общие значения для Dock, wallpaper button и верхних действий.
- Изменение относится к внутренней консолидации дизайн-системы, не добавляет пользовательский функционал.
