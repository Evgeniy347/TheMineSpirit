# Производительность тестов: замеры и рекомендации

Цель: тысячи тестов идут быстро и не душат общую машину; долгий тест виден сразу после прогона.
Замеры 2026-10-11 на `main@2875905` (228 JUnit-тестов в 34 классах, 11 свойств jqwik ≈ 9,7 тыс. tries,
15 GameTest) во временном worktree. Машина: 4 ядра, 11 ГБ; во время замеров load average 10–20,
CPU pressure (`/proc/pressure/cpu`, some avg60) ≈ 85 %, свободно ~3,3 ГБ, swap занят на 1 ГБ.
Узкое место — процессор, а не диск: NVMe, IO pressure ≈ 5 %.

## 1. Что измерено

### JUnit (`./gradlew test --rerun`)
| Показатель | Значение |
|---|---|
| Wall всей команды / задачи `:test` | 14–18 с / 11,8–18,8 с |
| Gradle: старт демона / configuration (промах кэша) / попадание в configuration cache | 1,9–2,6 с / 0,5–0,7 с / 0 с |
| CPU тестовой JVM (utime+stime), 5 прогонов | 12,0–15,4 с, медиана 14,6 с |
| Пиковый RSS тестовой JVM | 272 МБ при `-Xmx512m` (дефолт Gradle) |
| Постоянная часть: JVM + платформа с одним пустым тестом | 4,0 с CPU |
| Первое свойство jqwik (инициализация движка) | +1,3–2 с CPU |
| Сумма `testcase time` на одном и том же коде | 3,5–12,6 с — разброс ×3,6 |

Самые долгие тесты (медиана 3 прогонов, wall):
| Тест | с | Почему |
|---|---|---|
| `ChunkSquareProperties.diff_AnyCenters_…` | 1,65 | 1000 tries × до 33² чанков в `TreeSet`; центры ±1000 почти никогда не пересекаются |
| `WorldObservationProperties.json_RoundTrip_…` | 1,14 | 1000 сериализаций Jackson |
| `ChunkSquareProperties.diff_WalkOfSteps_…` | 0,75 | 200 tries × до 64 шагов × (2R+1)² |
| `WorldObservationTests.fromJson_BadPosWrongLengthFour_…` | 0,51 | не медленный: первым загружает классы Jackson (прогрев) |
| `ChunkSquareProperties.chunks_AnyRadius_…` / `WorldObservationProperties.hash_…` / `ResourceResolverProperties` | ≈0,5 каждый | 1000 tries |

Ловушки в отчётах:
- `time` у `<testsuite>` врёт для классов, где есть и `@Test`, и `@Property`: `PlanValidatorRulesTests` — 6,9 с
  на уровне класса при сумме тестов 0,32 с. Это интервал от прогона Jupiter до прогона jqwik
  (движки идут друг за другом). Считать по сумме `testcase`, не по классу.
- Время одного теста на общей машине — шум (×3,6), плюс прогрев JIT и загрузка классов достаются первому тесту,
  который трогает библиотеку. Меньше tries — меньше CPU, но время теста падает не пропорционально.

Опыты с той же сборкой (CPU тестовой JVM, по 2–3 прогона):
| Вариант | CPU, с | Изменение |
|---|---|---|
| как есть | 12,0–15,4 | — |
| `jqwik.tries.default=200` | 11,1–11,7 | −20 % |
| `-XX:+UseSerialGC -XX:ActiveProcessorCount=2` | 9,4–10,1 | −33 % |
| то же + `-XX:TieredStopAtLevel=1` (только C1) | 5,8–6,3 | **−58 %** (сумма testcase 3,9–5,4 с) |
| `maxParallelForks = 2` | 20,9–21,1 | **+45 %** |

В `task/09-testing` (2 форка + `parallel.config.strategy=dynamic`) последний отчёт исполнителя: сумма времени
классов 45 с против 15 с на main — потоки дерутся за занятые ядра.

### GameTest (`./gradlew runGameTestServer`)
| Фаза | Как есть (4 прогона) | С флагами JVM (2 прогона)¹ |
|---|---|---|
| Gradle до запуска JVM игры | 10–33 с (configuration 3,8 с, `downloadAssets` 2,7 с) | — |
| Бутстрап FML (ModLauncher → «Started game test server») | 45–74 с | 16–28 с |
| Сами тесты, один батч из 15 | 8,5–13,4 с | 3,1–5,3 с |
| Остановка (сохранение мира) | 9–15 с | 7–11 с |
| Wall всей команды | 72–124 с | 37–48 с |
| CPU JVM игры / пиковый RSS | 75–81 с / 0,91–1,06 ГБ (без `-Xmx`, дефолт ¼ ОЗУ ≈ 2,9 ГБ) | 29–32 с / 0,65 ГБ |

¹ `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xmx1G`, все 15 тестов зелёные.

- `GameTestServer.waitUntilNextTick()` не спит, а только выполняет задачи: тики идут без паузы 50 мс.
  Время теста = число тиков × стоимость тика (≈50–80 мс под нагрузкой), то есть тики в тесте — это CPU,
  а не ожидание. Самый длинный тест задаёт время батча: `FollowGameTests` ждёт фиксированно 20+120 тиков,
  `SpiritCommandGameTests` — 40.
- Флак под нагрузкой: `chunkLoading_SpiritSpawnedMovedAndDiscarded_KeepsTickingSquare` упал 1 раз из 6
  при load 20 («ожидалось 9 чанков вокруг (…), удержано 9»: набор не успел сдвинуться за 20 тиков).
- `run/world` не чистится, а тесты каждый раз встают в случайную точку мира: +5–8 МБ за прогон
  (в main `run/` уже 69 МБ).
- Шаблон один (`empty`, 3×3×3), фикстуры дешёвые; тяжёлых `@BeforeEach`, `Thread.sleep` и
  `new ObjectMapper()` на вызов в тестах и `core` нет.

### Сторожа
Пять сторожей и `CoreIsolationTests` обходят и читают исходники каждый сам: `src/test/java` — 4 раза,
`src/main/java` — 3 раза (133 файла, 390 КБ). Сейчас это 1–1,7 с CPU сверх пустого прогона;
растёт как (число файлов × число сторожей).

### Демоны Gradle
Демоны общие для всех worktree (`~/.gradle`), но при занятости запускаются новые: замерено 5 живых
(`-Xmx2G`, RSS 0,3–1,0 ГБ каждый) и полтора десятка остановленных за час. `./gradlew --stop` гасит и чужие
занятые демоны, поэтому на общей машине его использовать нельзя.

## 2. Рекомендации (по убыванию выигрыша)

| # | Файл — изменение | Ожидаемый выигрыш |
|---|---|---|
| 1 | `build.gradle`, `tasks.test`: `jvmArgs '-XX:TieredStopAtLevel=1', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=2'`, `maxHeapSize = '512m'` явно. В `slowTest` C1-only не ставить: долгие вычисления выигрывают от C2 | CPU быстрого тира −58 % (14,6 → 6 с); меньше потоков GC/JIT на чужих ядрах |
| 2 | `build.gradle`, `neoForge.runs.gameTestServer`: `jvmArguments.addAll('-XX:TieredStopAtLevel=1', '-XX:+UseSerialGC', '-Xmx1G')`; перепроверить на 3+ прогонах | wall 72–124 → 37–48 с, CPU −60 %, RSS −35 % |
| 3 | Новый `scripts/gametest.sh`: `flock -w 1800 /tmp/theminespirit-gametest.lock ./gradlew runGameTestServer`; исполнители и хуки зовут только его | на машине одна JVM игры (~1 ГБ, ~75 с CPU) вместо нескольких; меньше флаков под нагрузкой |
| 4 | Ветка 09: `maxParallelForks = 1`; в `junit-platform.properties` `parallel.config.strategy=fixed`, `fixed.parallelism=2` (или параллель выключить, пока CPU тира < 30 с). Там же баг: `"…-fork-\${System.getProperty(…)}"` даёт буквальное имя файла — все форки пишут одну базу jqwik | не тратить +45 % CPU на второй форк; база seed не портится |
| 5 | `junit-platform.properties`: `jqwik.tries.default=200` в быстром тире; `slowTest` / ночной прогон — `systemProperty 'jqwik.tries.default', '1000'`. `ChunkSquareProperties.diff_AnyCenters`: новый центр = старый + сдвиг в `[-2R-1, 2R+1]`, R ≤ 8 | −20 % CPU сейчас и линейно на каждое новое свойство; diff начинает проверять пересечения квадратов, а не почти всегда непересекающиеся |
| 6 | `gradle.properties`: `org.gradle.daemon.idletimeout=900000` (15 мин вместо 3 ч), `org.gradle.workers.max=2`; `-Xmx2G` → `-Xmx1536m` после замера пика демона на `createMinecraftArtifacts` | меньше спящих демонов по 0,3–1 ГБ; сборки не забирают все 4 ядра |
| 7 | `guards/SourceFiles.java`: `javaUnder` кэширует результат в `static ConcurrentHashMap<Path, Map<Path,String>>`; `CoreIsolationTests` берёт файлы оттуда же | один обход на корень за JVM вместо 7; при 1000+ файлов это секунды CPU |
| 8 | GameTest: `gameTestServer` получает свой `gameDirectory` в `build/gametest-run`, его `world` удаляется перед прогоном; `logLevel` INFO вместо DEBUG; фиксированные `runAfterDelay(120)` → `succeedWhen` с проверкой «путь построен ≤ 2 раз»; `timeoutTicks` ≈ 2× фактического числа тиков | мир не растёт; батч заканчивается по последнему условию, а не по таймеру |

Сохранение мира на остановке (7–15 с) упирается в процессор, а не в диск, поэтому tmpfs почти ничего не даст.
Это не приоритет.

## 3. Как сразу подсвечивать долгие тесты

Мерить CPU потока, а не wall. Шум wall на общей машине ×3,6, CPU всей JVM — ±13 %.
- `src/test/java/.../testing/TestCostListener` (JUnit Platform `TestExecutionListener`, подключается через
  `META-INF/services`): для каждого теста записывает `ThreadMXBean.getCurrentThreadCpuTime()` до и после
  и wall-время в `TestResults/unit/test-cost.csv` (класс, тест, cpu_ms, wall_ms). Свойство jqwik
  выполняется в одном потоке, поэтому учитывается целиком. Первый тест, который загрузил Jackson или jqwik,
  помечается как прогрев и в пороги не идёт.
- `build.gradle`: `tasks.test { finalizedBy 'testCostReport' }`. Задача печатает топ-10 по CPU и строки выше
  порога с пометкой `МЕДЛЕННЫЙ`. Хук pre-commit показывает этот вывод и при зелёных тестах.
- GameTest: свой `TestReporter` через `GlobalTestReporter.replaceWith(...)` при `GameTestHooks.isGametestServer()`.
  Пишет `TestResults/gametest/report.xml`: `getRunTime()`, тики теста (`level.getGameTime()` при завершении
  минус тик старта батча) и мс на тик по батчу. Основная метрика — тики: они не зависят от нагрузки.

Пороги (CPU одного теста, предупреждение / «долгий»):
| Что | Предупреждение | Долгий: разобрать до коммита или в slow |
|---|---|---|
| `@Test`, `@Example`, строка параметризованного теста | ≥ 100 мс | ≥ 500 мс |
| `@Property` (все tries) | ≥ 300 мс | ≥ 1 с |
| JUnit-класс (сумма testcase) | ≥ 500 мс | ≥ 2 с |
| GameTest | ≥ 100 тиков | ≥ 200 тиков или `timeoutTicks` > 400 |
| Бутстрап GameTest-сервера | ≥ 40 с | ≥ 60 с (смотреть флаги JVM и новые моды) |

Что валит сборку, а что только предупреждает:
- Падение по времени на общей машине ненадёжно: одинаковый код даёт 3,5–12,6 с. Время только предупреждает.
- Красным делают детерминированные метрики:
  - сторож: `@Property` с `tries` > 200 без `@Tag("slow")`;
  - сторож: GameTest с `timeoutTicks` > 400 без комментария-обоснования;
  - тесты на масштаб считают операции (вызовы, посещённые узлы, построенные пути), как уже требует `docs/TESTING.md`.
- Порог по времени может валить только ночной прогон на тихой машине: медиана 3 прогонов, лимит 2× от
  порога «долгий».

## 4. Бюджеты и нагрузка

- Быстрый тир (`./gradlew test`, его гоняет pre-commit): CPU тестовой JVM ≤ 20 с, wall команды ≤ 60 с.
  Постоянная часть 4 с, в среднем ≤ 5 мс CPU на тест: 2000 тестов ≈ 4 + 10 с. Сейчас 14,6 с на 228 тестов
  (≈ 46 мс на тест — съедают tries jqwik и прогрев). С рекомендациями 1 и 5 — около 5 с.
- Slow-тир и GameTest не идут в pre-commit: GameTest — перед слиянием ветки, slow — ночью или по запросу.
- Одна тестовая JVM на исполнителя, не больше 2 потоков JUnit; GameTest-сервер — один на машину (flock).
  Пять исполнителей × (демон до 1 ГБ + тестовая JVM 0,3 ГБ) занимают до ~6,5 ГБ из 11 ГБ, вторая JVM игры
  (~1 ГБ) уводит машину в swap.
- Не держать после себя: worktree для замеров удалять. Демоны не гасить через `--stop` (он гасит и занятые
  чужие); короткий `idletimeout` (рекомендация 6) убирает лишние демоны сам.
