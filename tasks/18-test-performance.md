# 18 — производительность тестов

Источник: `docs/research/test-performance.md` (замеры + проверка Fable). Цель владельца: тысячи тестов быстро и без
нагрузки на систему; долгие тесты подсвечиваются сразу. Перед работой прочитай этот документ целиком, `docs/TESTING.md`,
`AGENTS.md`, `docs/GIT.md`. Каждый пункт — отдельный коммит; в отчёте для каждого — замер до/после (CPU потока и/или
время по документу) на одной и той же нагрузке, несколько прогонов, медиана.

## Чеклист
- [ ] 1. Флаги тестовой JVM в `tasks.test` (не в `slowTest`): `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC
      -XX:ActiveProcessorCount=2` (рекомендация 1).
- [ ] 2. Флаги JVM игры для `gameTestServer` (C1, SerialGC, `-Xmx1536m`, `-XX:+ExitOnOutOfMemoryError`) — рекомендация 2;
      перепроверь выигрыш минимум на 3 прогонах.
- [ ] 3. `scripts/gametest.sh` — запуск `runGameTestServer` под `flock` (один GameTest на машину), строка в README;
      `docs/TESTING.md` и памятка исполнителя: GameTest — только через скрипт.
- [ ] 4. jqwik: 200 прогонов в быстром тире, 1000 в `slowTest`; генератор `ChunkSquareProperties.diff_AnyCenters` —
      близкие центры, чтобы проверялся нетривиальный случай (рекомендация 4).
- [ ] 5. `gradle.properties`: `org.gradle.daemon.idletimeout` 15 мин и `org.gradle.workers.max=2` (рекомендация 5).
- [ ] 6. `SourceFiles` — один обход исходников на прогон (кэш), все сторожа на нём; сторожа остаются зелёными и
      краснеют на подлоге (рекомендация 6).
- [ ] 7. GameTest: свой каталог и чистый мир на каждый прогон (не копить духов и билеты в `run/world`);
      `ChunkLoadingGameTests` — `succeedWhen` вместо фиксированной задержки (но не `FollowGameTests`, см. документ).
      Флак `chunkLoading_SpiritSpawnedMovedAndDiscarded_KeepsTickingSquare` — найти причину, а не повторять прогон.
- [ ] 8. Подсветка долгих: после `test` печатать топ-10 по CPU потока; красным — только детерминированные метрики
      (tries, `timeoutTicks`, счётчики операций) по порогам из документа; пороги — в одном файле. Описать в TESTING.md.
- [ ] 9. `./gradlew test`, `./gradlew slowTest`, `scripts/gametest.sh` зелёные. Отчёт: таблица до/после по пунктам.
