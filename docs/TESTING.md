# Тесты

## Два уровня

| Уровень | Где | Что проверяет | Запуск |
|---|---|---|---|
| JUnit 5 + jqwik | `src/test/java` | чистая логика `core`, сторожа правил проекта | `scripts/test.sh unit [--tests …]` |
| GameTest | `src/main/java/.../gametest` | поведение в настоящем мире | `scripts/test.sh gametest [пачка…]` |

- Только через `scripts/test.sh`: полный лог — `TestResults/logs/`, на экран — итог. Прямой `./gradlew test` /
  `runGameTestServer` запрещён (GameTest-задача без пропуска от скрипта не стартует). Скрипт держит замок «один
  GameTest-сервер на машину», пределы времени и гашение своих процессов — устройство описано в его шапке,
  проверки — `scripts/testing/check-test-runner.sh`, `check-gametest-lock.sh`.
- Фильтр `gametest <имя…>` (пачка, класс, тест) отбирает тесты до старта; фильтр без совпадений — ошибка.
- Всё, что не требует мира, — JUnit в `core`. GameTest — только то, что без мира не проверить.

## GameTest: сцены

- Класс помечен `@GameTestHolder(ModIdentity.MOD_ID)` и `@PrefixGameTestTemplate(false)`. Шаблоны —
  `data/theminespirit/structure/*.nbt`: `empty` 3×3×3, `arena` 11×8×11 (пол 9×9, дух стоит и ходит),
  `mine_arena` 11×16×11. Сцена с духом — в своей пачке (`batch = …`), время — по тикам духа (`SpiritRun`).
- Мир между пачками не чистится: каждая сцена со спавном начинается с `GameTestFixtures.reset`/`sweepEntities`,
  время и погоду держит только `GameTestFixtures.holdDayTime` (сторожа `GameTestIsolationGuardTests`,
  `GameTestTimeGuardTests`).
- Доска и координатор на GameTest-сервере выключены; пачка, которой они нужны, помечается `@SettlementSystems`
  (`board`/`coordinator`). `@BeforeBatch`/`@AfterBatch` эти рубильники не трогают.
- Темп стройки на GameTest-сервере — 1 тик на блок; настоящий темп проверяет пачка `build_pace`.
- Тайм-аут > 400 тиков — только с обоснованием `[флак]` (сторож `GameTestTimeoutGuardTests`); `timeoutTicks ≈ 2×`
  фактических тиков. Без sleep и гонок тиков/HTTP.

## Property-based тесты (jqwik)

- Инварианты ядра — свойствами `@Property` рядом с примерами. Быстрый тир — `tries=200`
  (`junit-platform.properties`); `tries > 200` — только с jqwik-тегом `@Tag("slow")` (`net.jqwik.api.Tag`,
  сторож `JqwikTriesGuardTests`). Глубокий прогон — `./gradlew propertyTest`, медленный тир — `slowTest`.
- Упавший контрпример сначала оформляется отдельным примером (`@Example`/`@Test`).

## Имя теста и сообщение assertion

- Имя: `subject_Condition_ExpectedResult` (сторож `TestNamingGuardTests`).
- Сообщение assertion (по-русски, сторож `AssertionMessageGuardTests`): какой контракт, что сломается, что
  перепроверить при осознанной смене. `!= null` и «не упало» — не проверка (`WeakAssertionGuardTests`).
- В проверке предела ожидание — число в тесте, факт — из production; литерал против литерала — не проверка.

## Тест, который может упасть

- Баг сначала воспроизводится тестом, красным на старом коде; затем исправление; тест зелёный.
- Новый тест доказывается подлогом по существу: сломай поведение так, что код компилируется, — тест краснеет.
  «На родителе не компилируется» — слабое доказательство. Для `fix:` с JUnit — `scripts/prove-red.sh <коммит>`.
- До прогона скажи, что ожидаешь красным.

## Сторожа

Сторож проверяет правило проекта (`src/test/java/com/theminespirit/guards/`): сообщение — правило, цена, как надо;
у сторожа-шаблона — тест на шаблон. Сторож дополняет тест поведения, не заменяет его. Новое правило — по возможности
сторожем, а не текстом (AGENTS.md).

Гигиена тестов (нет `@Disabled`, рефлексии к private, изменяемых `static`-полей; исключение — кэш только для чтения
`guards.SourceFiles`) — сторож `TestHygieneGuardTests`. Подмена production через `Function`/`Supplier` в публичном API
запрещена и проверяется ревью, а не сторожем.

## Время

- Тяжёлая подготовка — один раз (`@BeforeAll`). JUnit-класс дольше ~2 с, GameTest дольше ~5 с — найти причину.
- Тест на масштаб считает операции, а не миллисекунды. Время тестов — отчёт `testCostReport`
  (`TestResults/unit/test-cost.csv`, пороги — `test-cost-thresholds.properties`), предупреждение, не гейт.
- Форки JUnit выключены по замеру (последовательно быстрее на этой машине); переизмерять при смене железа.
- Покрытие — `./gradlew jacocoTestReport` (`TestResults/coverage/`), отчёт, не гейт.
