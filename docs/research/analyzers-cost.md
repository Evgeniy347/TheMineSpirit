# Цена анализаторов: замер и вывод (07.11)

Правило владельца: анализаторы вместе ≤ 30% времени (тесты + сборка). В main (`c7241388`) SpotBugs, PMD и
Checkstyle уже выключены по умолчанию (`theminespirit.analyzers=off`); вопрос — чем их можно ускорить, чтобы вернуть.
Spotless и Error Prone работают всегда.

## Как измерено

Сценарий: пробельная правка в классе `:mod` (`BuildSquadRegistry`) и в классе другого модуля (`:gametest`,
`BoardGameTests`), затем полный `scripts/test.sh unit` (как в финале исполнителя) и `./gradlew check --profile`.
Время групп — из профиля Gradle по задачам: компиляция (`compileJava`/`compileTestJava`), тесты (`test`), Spotless,
Checkstyle, PMD, SpotBugs. Анализаторы включаются флагом `-Ptheminespirit.analyzers=on`; кэш сборки выключен
(`--no-build-cache`), иначе повторная пробельная правка даёт `FROM-CACHE` и ноль. Вариант конфигурации — временный
init-скрипт, дерево не меняется. Число java-процессов записывается: ПК делят 5 исполнителей, поэтому два образца и
меньшее/медиана; абсолютные секунды шумят, сравнение — с A в тех же условиях.

```bash
scripts/testing/measure-analyzers.sh --variant A --samples 2                  # как сейчас
scripts/testing/measure-analyzers.sh --variant B --samples 2                  # SpotBugs effort=MIN
scripts/testing/measure-analyzers.sh --variant C --samples 2                  # SpotBugs выкл. :mod/:gametest
scripts/testing/measure-analyzers.sh --variant D --samples 1                  # PMD выкл. :mod
scripts/testing/measure-analyzers.sh --variant A --analyzers-only --samples 2 # только анализаторы :mod/:gametest
scripts/testing/measure-analyzers.sh --omit "Detector1,…"                     # вариант F
```

## Варианты A–D (меньший из образцов)

| Вариант | Прогон | Компиляция | Тесты | Spotless | Checkstyle | PMD | SpotBugs | Анализаторы | Доля | java |
|---|---|---|---|---|---|---|---|---|---|---|
| A как сейчас | unit | 10.0 с | 117.0 с | 0.2 | 27.8 | 30.0 | 109.1 | 167.1 с | **56.8%** | 3 |
| A как сейчас | check | 5.0 с | 37.2 с | 0.2 | 27.6 | 30.0 | 119.6 | 177.3 с | **80.8%** | 4 |
| B effort=MIN | unit | 10.1 с | 115.3 с | 0.2 | 28.4 | 29.3 | 103.9 | 161.8 с | **56.4%** | 6 |
| B effort=MIN | check | 4.6 с | 31.7 с | 0.1 | 29.2 | 33.4 | 111.9 | 174.6 с | **82.8%** | 7 |
| C SpotBugs выкл. | unit | 10.8 с | 132.3 с | 0.2 | 49.3 | 54.1 | 29.9 | 133.4 с | **48.2%** | 9 |
| C SpotBugs выкл. | check | 7.5 с | 43.7 с | 0.2 | 30.6 | 32.1 | 31.9 | 94.7 с | **64.9%** | 9 |
| D PMD выкл. :mod | анализаторы | 0.1 с | — | 0.9 | 41.6 | 20.8 | 113.8 | 176.1 с | — | 2 |

Чтение: A — анализаторы 56.8% (unit) и 80.8% (инкрементальный check), то есть в 1.3–4 раза больше правила 30%.
B: effort=MIN срезает SpotBugs `:mod` 109→104 с (−5 с, −3%) — почти ничего, потому что время уходит на общий разбор
байткода и инфраструктуру dataflow, а не на «дорогие» проходы. C убирает SpotBugs `:mod` 67–85 с и `:gametest`
17–23 с, но PMD и Checkstyle остаются: доля падает до 48.2% (unit) / 64.9% (check), до 30% не доходит.
D убирает PMD `:mod` (~14 с на незагруженной машине); под нагрузкой PMD `:mod` доходил до 120–205 с, и D его
действительно обнуляет (`:mod` PMD 0 с), но на общую долю влияет слабо.

Под нагрузкой 13–23 java-процессов полный unit упирался в предел 900 с (C3, код 124); такие образцы в таблицу не
берутся. Проекция по чистым числам A: C+D (SpotBugs выкл. `:mod`/`:gametest` + PMD выкл. `:mod`) даёт
≈ 33.5% unit; C+D+Checkstyle выкл. `:mod` — ≈ 26.5%, то есть только так выполняется правило 30%.

## Профиль детекторов SpotBugs (JFR, топ-15)

Способ: JFR-запись JVM SpotBugs (`-XX:StartFlightRecording`, `settings=profile`), выборки `jdk.ExecutionSample`,
`jfr print --stack-depth 256`; образец относится к детектору по первому кадру `edu.umd.cs.findbugs.detect.*`.
SpotBugs 4.10.4 **не умеет** печатать время по детекторам: есть `-progress` (классы) и `-timestampNow` (метка
отчёта), но не таймеры; поэтому профиль снят сэмплированием. Секунды = доля выборок детектора × время `spotbugsMain`
(:mod 47 с, 874 выборки; :gametest 20 с, 265 выборок). «Инфраструктура» (разбор байткода, CFG, dataflow-движок) —
11 с :mod и 2 с :gametest, в таблицу не входит.

| Детектор | :mod, с | :gametest, с | Что проверяет | Находки у нас | Нужен |
|---|---|---|---|---|---|
| FindNullDeref | 5.8 | 2.0 | null-пути, NPE | NP_* (история) | да |
| BuildUnconditionalParamDerefDatabase | 4.9 | 2.8 | обучение dataflow для null | инфраструктура null | да |
| BuildNonnullReturnDatabase | 3.2 | 1.0 | обучение dataflow для null | инфраструктура null | да |
| FindUseOfNonSerializableValue | 2.4 | 1.0 | Java-сериализация, J2EE-сессия | никогда | нет |
| UnreadFields | 1.0 | 0.3 | нечитаемые поля | никогда | предложить |
| FindRefComparison | 1.7 | 0.7 | сравнение ссылок `==` | косвенно (equals) | да |
| FindImproperSynchronization | 1.0 | 0.2 | синхронизация | USO_* (наши баги) | да |
| LoadOfKnownNullValue | 0.7 | 0.2 | null после проверки | NP_* | да |
| MethodReturnCheck | 0.7 | 0.1 | игнорируемый результат | RV_* | да |
| FieldItemSummary | 0.6 | 0.8 | сводка полей для dataflow | инфраструктура | да |
| DumbMethods | 0.6 | 0.3 | Random, exit, строки | DMI_* (история) | да |
| FindInconsistentSync2 | 0.6 | 0.1 | согласованность блокировок | IS2_* | да |
| FindNoSideEffectMethods | 0.5 | 0.5 | методы без побочных эффектов | инфраструктура | да |
| ResourceInMultipleThreadsDetector | 0.5 | 0.2 | ресурсы между потоками | никогда | предложить |
| RuntimeExceptionCapture | 0.4 | 0.2 | потеря исключения | никогда | да (дешёвый) |
| LazyInit | 0.2 | 0.1 | ленивая инициализация | LI_* (наш DoorWatchService) | да |

Дорогие детекторы — это null/dataflow и потоки; они полезны и по решению владельца не отключаются. Единственный
дорогой и неактуальный — `FindUseOfNonSerializableValue` (сериализация): ~2–3 с, ни одной находки за историю.

## История находок: какие детекторы ловили наши баги

Проверено воспроизведением багов `b1523e0f` на текущем дереве: возвращены `static synchronized` в
`BuildSquadRegistry`/`WorkReserveHub` и публикация `DoorWatchService` до регистрации, запущен `spotbugsMain`.

| Детектор | Паттерн | Где поймал | Нужен |
|---|---|---|---|
| FindImproperSynchronization | USO_UNSAFE_STATIC_METHOD_SYNCHRONIZATION ×4 | BuildSquadRegistry, WorkReserveHub (b1523e0f) | да |
| LazyInit | LI_LAZY_INIT_UPDATE_STATIC | DoorWatchService (b1523e0f) | да |
| FindPuzzlers | IM_AVERAGE_COMPUTATION_COULD_OVERFLOW | b003b3b2 | да |
| MutableStaticFields | MS_OOI_PKGPROTECT | db48ed92 | да |
| FindReturnRef | EI/MS_EXPOSE_REP* | b1523e0f (исключены правилом) | да |
| FindNullDeref | NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | b1523e0f (исключён правилом) | да |
| FormatStringChecker | VA_FORMAT_STRING_USES_NEWLINE | b1523e0f (исключён правилом) | да |
| FindOpenStream | OS_OPEN_STREAM | живая фикстура ямы (07.11) | да |
| AtomicityProblem / SharedVariableAtomicityDetector | AT_STALE_THREAD_WRITE_OF_PRIMITIVE | b1523e0f (volatile) | да |

`effort=MIN` на этих же баговых классах видит все 5 находок (`USO_*` ×4, `LI_*`): потери покрытия на известных
багах нет. Но и выигрыш −5 с, поэтому effort как способ вернуть анализаторы не годится.

## Вариант F: отключить неактуальные детекторы

Список F: `SerializableIdiom`, `ExplicitSerialization`, `ComparatorIdiom`, `FindUseOfNonSerializableValue`,
`FindNonSerializableStoreIntoSession`, `FindNonSerializableValuePassedToWriteObject`,
`AppendingToAnObjectOutputStream`, `BadResultSetAccess`, `FindSqlInjection`, `BadAppletConstructor`,
`CrossSiteScripting`, `BadlyOverriddenAdapter`, `DoInsideDoPrivileged`, `PermissionsSuper`,
`FindVulnerableSecurityCheckMethods`, `FindPotentialSecurityCheckBasedOnUntrustedSource`, `UnsafeDetector`,
`DontUseEnum`, `XMLFactoryBypass` — JNI/нативный код, веб/сервлеты/JSP, JDBC/SQL, Swing/AWT, Serializable,
SecurityManager и устаревшие ключевые слова. Ни один из них не ловил наших находок (см. историю), и почти все они
дешёвые.

Замер `:mod:spotbugsMain` + `:gametest:spotbugsMain` подряд (java 2–23): без omit — `:mod` 93–124 с, `:gametest`
25–28 с; с omit — `:mod` 103–148 с, `:gametest` 25–28 с. Разница в пределах шума: **выигрыш F ≈ 0 с**. Причина —
`FindUseOfNonSerializableValue` даёт ~2–3 с, остальные детекторы списка вместе меньше 1 с. Потеря покрытия — ноль на
нашей истории и на воспроизведённых багах; но и возвращать анализаторы F не помогает.

## Вывод и рекомендация

1. Правило 30% при полном наборе не выполняется: A = 56.8% (unit) и 80.8% (check). Главный расход — SpotBugs
   `:mod` (67–85 с) и `:gametest` (17–23 с), затем PMD и Checkstyle `:mod` (по ~14–18 с на чистой машине).
2. `effort=MIN` не решение: −5 с SpotBugs `:mod` (3%), покрытие известных багов сохраняется.
3. Отключение SpotBugs на `:mod`/`:gametest` (C) снимает ~90 с, доля 48%; вместе с PMD `:mod` (C+D) — ~33.5%;
   правило 30% достигается только с отключением ещё и Checkstyle `:mod` (~26.5%). Чем платим: два самых больших
   модуля без байткод-анализа (null, синхронизация, потоки) и без PMD; на приёмке их всё равно надо гонять.
4. Отключать неактуальные детекторы (F) не стоит: выигрыша нет, а список надо поддерживать. Единственный кандидат —
   `FindUseOfNonSerializableValue` (~2–3 с), если хочется убрать заведомо мёртвую проверку.
5. Рекомендация: оставить анализаторы выключенными по умолчанию (как в main) и включать полный набор только на
   приёмке (`-Ptheminespirit.analyzers=on`); в обычном цикле — Spotless и Error Prone. Если нужен компромисс,
   вернуть «быстрые» (Spotless, Checkstyle, PMD) всегда, а SpotBugs — на приёмке: это укладывается в 30% и сохраняет
   строгий байткод-анализ там, где он ловит баги.

## Отключено вторым коммитом

Ничего: по решению владельца 07.11 анализаторы выключены целиком по умолчанию, а отключение отдельных детекторов
не даёт выигрыша (F ≈ 0 с). Кандидаты ниже перечислены для решения владельца, в конфигурации не применены.

| Детектор | Область | Находки у нас | Отключён сейчас |
|---|---|---|---|
| `FindUseOfNonSerializableValue` | Serializable/J2EE | нет | нет |
| `SerializableIdiom` | Serializable | нет | нет |
| `ExplicitSerialization` | Serializable | нет | нет |
| `ComparatorIdiom` | Serializable-компаратор | нет | нет |
| `FindNonSerializableStoreIntoSession` | J2EE-сессия | нет | нет |
| `FindNonSerializableValuePassedToWriteObject` | Serializable | нет | нет |
| `AppendingToAnObjectOutputStream` | Serializable | нет | нет |
| `BadResultSetAccess` | JDBC/SQL | нет | нет |
| `FindSqlInjection` | SQL | нет | нет |
| `BadAppletConstructor` | апплеты | нет | нет |
| `CrossSiteScripting` | веб/JSP/сервлеты | нет | нет |
| `BadlyOverriddenAdapter` | Swing/AWT | нет | нет |
| `DoInsideDoPrivileged` | SecurityManager | нет | нет |
| `PermissionsSuper` | SecurityManager | нет | нет |
| `FindVulnerableSecurityCheckMethods` | SecurityManager | нет | нет |
| `FindPotentialSecurityCheckBasedOnUntrustedSource` | SecurityManager | нет | нет |
| `UnsafeDetector` | sun.misc.Unsafe/JNI | нет | нет |
| `DontUseEnum` | ключевые слова Java 1.4 | нет | нет |
| `XMLFactoryBypass` | XML-фабрики | нет | нет |

Сторож `SpotBugsDetectorConfigGuardTests` держит согласие: если `omitVisitors` появится в конфигурации, он обязан
совпадать со строками «да» этой таблицы; `effort`/`reportLevel`/`visitors`/`chooseVisitors` и набор исключений
`exclude.xml` меняться не должны.

## Повторный замер после отключения

Не применимо: конфигурация не менялась. С включёнными анализаторами (`-Ptheminespirit.analyzers=on`) повторный
замер даёт те же A/B/C/D; после коммита доля та же. В обычном цикле (без флага) анализаторы не запускаются вовсе —
0% времени.
