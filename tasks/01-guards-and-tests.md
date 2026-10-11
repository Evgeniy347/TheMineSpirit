# 01 — сторожа стиля тестов и кода

Источник: ревью кода от 2026-10-11 (HEAD `0ec3bd9`). Перед работой прочитай `AGENTS.md`, `docs/TESTING.md`,
`docs/CONVENTIONS.md` → «Ошибки», `docs/GIT.md`.

## Контекст

Главный агент написал трёх сторожей в `src/test/java/com/theminespirit/guards/` (файлы уже лежат в дереве,
не закоммичены): `SourceFiles` (общий обход исходников), `TestNamingGuardTests`, `WeakAssertionGuardTests`,
`SilentCatchGuardTests`. Их ещё никто не запускал. Твоя задача — довести их до зелёного состояния вместе
с исправлением нарушений и закоммитить.

## Чеклист

- [ ] 1. Запусти `./gradlew test` и запиши, что красное. Ожидается красным: `TestNamingGuardTests`
      (имена не по шаблону) и `WeakAssertionGuardTests` (`assertNotNull`, `assertDoesNotThrow`).
      `SilentCatchGuardTests` ожидается зелёным. Если красное что-то другое или сторож не компилируется —
      исправь сторожа, сохранив смысл их сообщений.
- [ ] 2. Докажи, что каждый сторож умеет краснеть: временно подложи нарушение (пустой `catch` в любом
      production-классе, тест с именем из двух частей, вызов проверки «не null»), убедись в красном,
      убери подложенное. В отчёте — какие три подлога сделал и что увидел.
- [ ] 3. Переименуй тесты, которые сторож назвал нарушителями, по шаблону `subject_Condition_ExpectedResult`.
      Известные сейчас: `ScanRadiusTests.defaultRadius_WithinLimits`, `ScriptRegistryTests.contains_ReportsPresenceAndAbsence`,
      свойства с условием с маленькой буквы (`run_randomTree_…`, `hash_inventoryOrderChanged_…`,
      `json_roundTrip_…`, `parse_waitScriptWithAnyTicks_…`, `parse_sequenceWithinDepthLimit_…`,
      `parse_sequenceOverDepthLimit_…`). Список — по выводу сторожа, а не по этому перечню.
- [ ] 4. Замени слабые проверки на проверки поведения:
      - `ResourceResolverProperties`: вместо проверки, что план не null, — проверь структуру плана
        (последний шаг — `craft`/`smelt`/`gather` нужного предмета с нужным количеством). Пустой
        `catch (UnplannableGoalException expected) {}` замени явной проверкой: исключение допустимо только
        если в графе знаний у предмета действительно нет способа добычи — проверь это по самому графу.
      - `ScanRadiusTests`: вместо «не упало» — проверь, что `new ScanRadius(MIN).value() == MIN` и то же для MAX.
- [ ] 5. `./gradlew test` зелёный целиком. Коммиты по `docs/GIT.md` (хук проверяет формат):
      сторожа вместе с исправлениями нарушений — `test: сторожа имён тестов, слабых проверок и пустых catch`;
      можно разбить на 2–3 коммита, но каждый коммит — с зелёным `./gradlew test`.

## Границы

- Трогаешь только `src/test/java/**`. Production-код не меняй (кроме временного подлога из пункта 2,
  который не коммитится).
- Не меняй смысл существующих тестов, кроме пункта 4.

## Отчёт

Хэши коммитов; итог `./gradlew test` (сколько тестов, сколько упало); три подлога из пункта 2 и их результат;
что не сделано и почему.
