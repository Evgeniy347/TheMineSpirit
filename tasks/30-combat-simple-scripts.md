# 30 — Бой и простые скрипты: `attack`, `defend`, `flee`, `retreat`, `say`, `wait`, `meet`, `deliver`

Источник: `todo.md` → «Бой и охота»: «`attack` (цель), `shoot`; `set_combat_mode`; `defend` (себя), `guard_area`»;
«Движение и навигация»: «`escort`, `meet`; `flee_from`, `retreat`, `disengage`»; «Социум и мета»:
«`say`, `report`, `deliver`, `give`, `take`; `wait`, `stop`, `cancel`». Решение владельца 13.10 («Дом и базы»):
отход тактики HOME выбирает ближайшую безопасную точку из «дом» и «лагерь похода»; решение владельца 13.10
(«Быстрые укрепления в бою»): замуроваться/укрытие 4×4 — отдельный чеклист, но интерфейсы отхода и обороны его
не закрывают. В каталоге все восемь скриптов уже описаны (`CoreScripts`, строки 135–136, 289–296, 339–352),
реализаций нет: `SpiritScripts.implemented()` их не содержит, `catalog()` ограничен реализованными, план с ними
отвергается валидатором.

Перед работой прочитай `AGENTS.md`, `docs/CONVENTIONS.md` (Быстрый путь — чистая логика в `core`, адаптер тонкий;
Структура — решение в одном месте; Производительность — тик стоит дорого, тест на масштаб считает операции;
Ошибки — тихий fallback запрещён), `docs/TESTING.md` (JUnit и GameTest, имена тестов, сторожа,
`GameTestTimeoutGuardTests`, время тестов), `docs/GIT.md`, `docs/TACTICS.md` (ядро тактики, режимы, схема четырёх
JSON, trace), `docs/API.md` (каталог скриптов, поля `progress`/`result` задачи).

Код (карта для чтения): `core/script/{CoreScripts,CatalogLimits,ParamSpec,ScriptArgs}` (спеки и числа),
`core/tactics/{CombatMode,Orders,RetreatMethod,TacticsEngine}`,
`core/tactics/adapter/{OrdersBuilder,TargetDesignation,IntentActionExecutor,RetreatPlanner,RetreatCommitment,
RetreatTracker,StoredTactics,TacticsAdapterSettings}`, `core/policy/{LayerArbiter,PolicyExemptions,PolicyId}`,
`core/engine/{ScriptInstance,ScriptStatus,ScriptFactory,ScriptRegistry,TaskRunner,TaskSnapshotCodec}`,
`neoforge/tactics/{TacticsService,WorldFactCollector,CombatActuator,SpiritGear,RetreatExecutor}`,
`neoforge/Approach`, `neoforge/movement/{ApproachScript,ComeToPlayerScript,KeepDistanceScript}` (образец разрешения
цели `resolveTarget` и подхода), `neoforge/DropCollector`, `neoforge/inventory/{TransferRunner,StackMapper,
ContainerAccess}`, `neoforge/SpiritEntity` (`tactics()`, `home()`, `getInventory()`, `level()`, `tickCount`),
`neoforge/SpiritBrain` (`suspend`/`resume`, `start`), `neoforge/SpiritScripts` (реестр),
`gametest/{GameTestFixtures,SpiritRun,TacticsScene,TacticsModesGameTests,TacticsRetreatGameTests,
TacticsPersistenceGameTests,TacticsScaleGameTests}`. Публикацию и перезапуск сервера не делать.

**Зависимости.** Часть А (ядро, пп. 1–11) не зависит ни от кого — делается сразу. Часть Б (адаптер, пп. 12–20) —
после А; п. 20 (итог) — после слияния чеклиста 27 (`ScriptResult`); п. 16 (`deliver` через `acquire`) удобнее после
слияния чеклиста 28. Часть В (GameTest, пп. 21–30) — после Б; пп. 29–30 (перезапуск и масштаб) — после
стабилизации Б. Чеклисты 19/23 (тактика), 20 (политики и `LayerArbiter`), 24 (движение) уже в main и нужны как
есть. `SpiritHome`/лагерь похода (`todo.md`) в main ещё нет — п. 5 и H4 учитывают это заглушкой. Если 27 или 28
к моменту финала не в main, всё остальное доводится, а зависящие пункты честно помечаются «ждёт 27»/«ждёт 28».

## Что должно получиться

Восемь скриптов исполняются в мире. Боевые четыре — тонкие команды стратегу поверх уже работающей тактики
(`core/tactics` + `neoforge/tactics`), а не второй боевой движок: скрипт задаёт приказ (режим, цель, способ
отхода) и уступает бой тактике; успех/провал определяется по миру. Простые четыре — `say`, `wait`, `meet`,
`deliver` — отдельные `ScriptInstance`; прогресс `wait`/`deliver` переживает перезапуск, `say` не спамит и не
раздувает чат, `meet`/`deliver` используют существующий подход. Итог идёт общим каналом (чеклист 27).

- **`attack`** («атаковать цель»: `target` необязателен, по умолчанию `hostile`) — на время задачи задать тактике
  режим `AGGRESSIVE` и указать цель (`hostile`/`animal`/игрок по имени/сущность по UUID). Бой ведёт тактика, скрипт
  только держит приказ и ждёт: цель мертва/пропала — `SUCCEEDED`; истёк `ATTACK_TIMEOUT_TICKS` — `FAILED` с
  причиной и числом тиков. По завершении/отмене приказ снимается, прежний режим и цель восстанавливаются.
- **`defend`** («защитить цель»: `target` обязателен) — режим `PROTECT` с подопечным (`setWard`): тактика держится
  рядом и бьёт угрожающих подопечному. `SUCCEEDED`, когда подопечный вне угрозы `DEFEND_SAFE_TICKS` подряд;
  истёк `DEFEND_TIMEOUT_TICKS` — `FAILED`. Подопечный исчез — `FAILED` с текстом.
- **`flee`** («убежать от угрозы») — спастись от близкой угрозы (конкретной цели нет): приказ на отход всеми
  способами отхода тактики (`AWAY`/`COVER`/`KITE`/`WATER`), приоритет — выйти из опасной зоны. `SUCCEEDED`, когда
  врагов в радиусе опасности нет `FLEE_SAFE_TICKS` подряд; тайм-аут `FLEE_TIMEOUT_TICKS` — `FAILED` с числами.
- **`retreat`** («отступить») — организованный отход к безопасной точке: приказ на отход способом `HOME` к
  ближайшей безопасной точке из «дом» и «лагерь похода» (`SafePointChoice`). `SUCCEEDED` по прибытии
  (`RETREAT_ARRIVAL`); нет ни одной точки — `FAILED` «точки отхода нет». Лагерь похода в этот чеклист не входит, но
  интерфейс выбора точки его принимает (H4).
- **`say`** («произнести сообщение»: `text`) — сообщение в чат от имени духа: текст нормализуется и урезается до
  `SAY_LENGTH_MAX`, пустой и управляющие символы — отказ с текстом; повтор чаще `SAY_MIN_INTERVAL_TICKS` —
  `FAILED` с числом (анти-спам). Доставка — H5.
- **`wait`** («ждать заданное число тиков»: `ticks`, 1..`WAIT_TICKS_MAX`) — отсчёт по тикам духа, без `sleep`; по
  истечении `SUCCEEDED`. Остаток переживает перезапуск.
- **`meet`** («встретиться с целью»: `target`) — подойти к цели (игрок/дух) на дистанцию `MEET_ARRIVAL`, используя
  существующий `Approach` (образец `ComeToPlayerScript`); цель пропала — `FAILED` с текстом.
- **`deliver`** («доставить предмет адресату»: `item`, `count`, `to`) — поднести предмет адресату и передать:
  игроку — в инвентарь (`addItem`), не влезло — выбросить рядом (`DropCollector`); духу-адресату — выбросить
  рядом. Нет предмета — H6 (отказ или `acquire`); передано `count` — `SUCCEEDED`.
- **Итог общим каналом (27).** Боевые отдают `ScriptResult.Data` (`mode`, `target`, `ticks`, `outcome`), простые —
  `Text`/`Data` (`wait` — `ticks`; `deliver` — `item`, `count`, `delivered`, `to`); при провале — `failureReason()`
  с числами. После слияния 27 путь до `GET /v1/tasks/{id}` — без новых полей.
- **GameTest на каждый скрипт и тест на масштаб боя.** Сквозные сцены в `arena`: атака моба, оборона подопечного,
  бегство, отход домой; чат, ожидание, встреча, доставка; перезапуск `wait`/`deliver`; бой 20 духов считает
  операции за тик (как `TacticsScaleGameTests`).

### Как боевые скрипты работают поверх тактики (предлагаемый механизм)

Боевые четыре — обычные `ScriptCall` в каталоге; их `ScriptInstance` не ведёт бой сам, а выдаёт приказ тактике:

1. **Приказ задаётся один раз на первом тике** (`CombatDirective`: режим, `TargetSelector`, способ отхода,
   безопасная точка). `SpiritEntity.tick` во время боя не вызывает `brain.tick()` (`!tactics.wantsControl()`),
   поэтому отдавать приказ нужно до того, как тактика забрала управление, а дальше — только следить за миром.
2. **Бой ведёт существующая тактика.** Движок решает бой/отход, `IntentActionExecutor`/`CombatActuator` действуют,
   `LayerArbiter` даёт тактике приоритет над задачей (STRATEGY). Второго набора правил боя не пишем.
3. **Скрипт наблюдает исход** по миру (цель мертва/пропала, подопечный в безопасности, врагов нет, достигнута
   точка), а не по внутреннему счёту тактики; `FAILED` — по тайм-ауту. По завершении/отмене приказ снимается,
   `StoredTactics`-снимок возвращает прежний режим и подопечного.
4. **`flee` и `retreat` — не второй планировщик отхода.** Оба переиспользуют `RetreatPlanner`/`RetreatExecutor`
   (метод и точка — часть приказа); отличие — H3. `retreat` дополнительно передаёт безопасную точку
   (`SafePointChoice`) вместо `WorldFactCollector.home()` (H4).

## Общие точки с другими чеклистами (merge)

- `core/script/CoreScripts` — спецификации восьми скриптов уже есть. Правки: при необходимости уточнить текст
  `effects`; у `wait.ticks` границы уже стоят (`WAIT_TICKS_MIN/MAX`). Наборы параметров не меняются — конфликтов
  почти нет.
- `core/script/CatalogLimits` — новый блок `SOCIAL_*`/`COMBAT_*` в конце файла (после `HERD_SIGHT_MARGIN`).
  Чеклисты 28/29 тоже добавляют блоки в конце — при слиянии «оставить обе стороны».
- `core/script/ScriptCatalogTests` — восемь скриптов уже в таблице обязательных параметров; строки не меняются,
  если параметры не тронуты. `guards/CatalogDefaultGuardTests` — новых умолчаний нет (кроме уже объявленного
  `attack.target="hostile"`), список `OPTIONAL_WITHOUT_DEFAULT` не трогаем.
- `core/tactics/*` — `CombatMode`, `RetreatMethod`, `Orders` используются как есть; `RetreatPlanner.Input.home`
  остаётся одним `Optional<Point>` (точку выбирает `SafePointChoice`). Новых правил в движок тактики не пишем.
- `neoforge/tactics/TacticsService` — новые тонкие хуки (п. 12): применить/снять приказ, задать подопечного,
  запросить способ/точку отхода. `snapshot()`/`restore(StoredTactics)` уже есть — переиспользуются для
  восстановления прежнего режима. Существующие `setCombatMode`/`setWard`/`setGuardZone` не ломаются.
- `neoforge/movement/{Approach,ComeToPlayerScript,KeepDistanceScript}` — образцы подхода и разрешения цели
  (`resolveTarget`: игрок по имени, сущность по UUID); `approach` не меняется. `deliver`/`meet` используют тот же
  путь.
- `neoforge/{DropCollector,SpiritEntity.getInventory,SimpleContainer}` — `deliver`/передача игроку только
  вызываются; `DropCollector` не меняется.
- `core/policy/LayerArbiter` — не меняется: приоритет TACTICS над STRATEGY уже есть (иначе `attack` не смог бы
  вести бой). Если всплывёт нехватка — правка минимальна и помечается в отчёте.
- `neoforge/SpiritScripts` — восемь новых записей `table.put(...)`; `catalog()` = `CoreScripts` ∩ реализованные —
  скрипты появляются в `capabilities`. Конфликт с 28/29 (свои записи) — «оставить обе стороны».
- `docs/API.md` — поведение восьми скриптов в разделе каталога; поля `result` (после 27).
- `gametest/Tactics*`, `gametest/GameTestFixtures`, `SpiritRun` — переиспользуются; новые классы боя и простых
  скриптов отдельные, существующие не меняются.
- Чеклист 27 (`ScriptResult`) — итог (п. 20); чеклист 28 (`acquire`) — `deliver` «с нуля» (п. 16, H6).
- Дом/лагерь (`SpiritHome`, `todo.md`) и «быстрые укрепления в бою» — отдельные чеклисты; здесь только `SafePointChoice`
  и хук точки отхода, чтобы их не закрыть.

## Часть А — ядро (`core`, без Minecraft)

- [ ] 1. **Параметры боевых скриптов** (`core/script`) — записи по образцу `ApproachParams`/`GatherParams`:
  `AttackParams(target)` (`target` необязателен, по умолчанию `"hostile"`, непустая строка),
  `DefendParams(target)` (`target` обязателен), `FleeParams()` и `RetreatParams()` (пустые записи — реестру нужен
  тип). У каждой `from(Map)` через `ScriptArgs`. Тесты: значения читаются; пустой `defend.target` — исключение с
  текстом; `attack` без `target` даёт `"hostile"`; умолчаний сверх этого нет (`CatalogDefaultGuardTests`).
- [ ] 2. **Параметры простых скриптов** (`core/script`) — `SayParams(text)`, `WaitParams(ticks)`,
  `MeetParams(target)`, `DeliverParams(item,count,to)`. Проверки: `text`/`item`/`to`/`target` непусты; `ticks` в
  `WAIT_TICKS_MIN..WAIT_TICKS_MAX`; `count` в `COUNT_MIN..COUNT_MAX`. Тесты: значения читаются; пустые строки и
  числа вне границ — исключение с текстом; умолчаний нет.
- [ ] 3. **`TargetSelector`** (`core/tactics` или `core/social`) — разбор строки цели в чистый вид:
  `record Target(Kind kind, Optional<String> name)` с `enum Kind { HOSTILE, ANIMAL, PLAYER, SPIRIT, NEAREST, ANY }`;
  `TargetSelector.parse(String)` понимает `hostile`/`animal`/`any`/`nearest`, `player:<имя>`, `spirit:<uuid>`.
  Неизвестная строка — исключение с допустимыми значениями. Тесты (таблица): каждое значение → вид и имя;
  `player:Имя`/`spirit:<uuid>` разбираются; пустая/неизвестная строка — исключение; регистр ключевых слов.
- [ ] 4. **`SafePointChoice`** (`core/tactics`) — чистая функция выбора точки отхода:
  `nearest(Point self, Optional<Point> home, Optional<Point> camp)` → `Optional<Point>`; ближайшая по расстоянию,
  при равенстве — `home`; обе пусты — `Optional.empty()`. Тесты: только дом → дом; только лагерь → лагерь;
  оба → ближний; ничья → дом (устойчивый тай-брейк); обе пусты → пусто; `null`-self — исключение.
- [ ] 5. **Интеграция безопасности отхода (заглушка лагеря)** (`core/tactics`) — точка отхода (`SafePointChoice`)
  встраивается в путь отхода так, чтобы лагерь (`todo.md`) подключился без правок: вход — `home` из
  `WorldFactCollector`/`SpiritEntity.home()` и `camp` из заглушки `Optional.empty()`; сам `SpiritHome`/лагерь не
  пишем. Тесты: заглушка лагеря ничего не ломает (нет лагеря — поведение как раньше); обе точки — выбирается
  ближняя.
- [ ] 6. **`SayText`** (`core/social`) — `normalize(text, maxLength)`: обрезка пробелов по краям, запрет управляющих
  символов (`\n`, `\r`, `\t` схлопываются в пробел), непустой результат, урезание до `maxLength` (обрезка, а не
  отказ, если длиннее; пустое после нормализации — исключение с текстом). Тесты: обычный текст не меняется;
  текст с `\n`/`\t` схлопывается; длинный урезан ровно до `maxLength`; пустой/из пробелов — исключение;
  `maxLength < 1` — исключение.
- [ ] 7. **`SaySpacing`** (`core/social`) — анти-спам: `due(long lastTick, long now, int minInterval)` →
  `boolean`; не было сообщений (`lastTick` = «никогда») → `true`; интервал не прошёл → `false`. Тесты: первый раз
  — да; раньше интервала — нет; ровно интервал — да; время назад — исключение или `false` (обосновать).
- [ ] 8. **`WaitCounter`** (`core/social`) — чистый отсчёт тиков: `start(ticks)`, `tick()` → `boolean done`,
  `remaining()`, `elapsed()`; `toMap()`/`fromMap()` для `saveState()`/`restoreState()` (совместимость со старым
  снимком без ключей — остаток 0/старт заново, обосновать). Тесты: отсчёт ровно `ticks`; `remaining` убывает;
  round-trip карты; отрицательный старт — исключение.
- [ ] 9. **`CombatDirective`** (`core/tactics`) — чистая запись приказа боевого скрипта:
  `record CombatDirective(CombatMode mode, Optional<TargetSelector.Target> target, Optional<RetreatMethod> retreat,
  Optional<Point> safePoint)`; статические фабрики `attack(target)`, `defend(target)`, `flee()`, `retreat(point)` —
  решение «какой режим и способ» в одном месте. Тесты: `attack` даёт `AGGRESSIVE`+цель; `defend` — `PROTECT`+цель;
  `flee` — отход без точки; `retreat` — `HOME`+точка; нужные поля непусты (валидация).
- [ ] 10. **Числа в `CatalogLimits`** (`core/script`) — блок `SOCIAL_*`/`COMBAT_*`: `SAY_LENGTH_MAX=256`,
  `SAY_MIN_INTERVAL_TICKS=20`, `ATTACK_TIMEOUT_TICKS=1200`, `DEFEND_TIMEOUT_TICKS=1200`,
  `DEFEND_SAFE_TICKS=60`, `FLEE_TIMEOUT_TICKS=600`, `FLEE_SAFE_TICKS=40`, `RETREAT_TIMEOUT_TICKS=1200`,
  `MEET_TIMEOUT_TICKS=1200`, `DELIVER_TIMEOUT_TICKS=1200`. Тесты равенства production-константам
  (`assertEquals(256, CatalogLimits.SAY_LENGTH_MAX)`), сравнение литерала с литералом — не проверка.
- [ ] 11. **`SocialTuning` + `CombatTuning`** (`core/social`, `core/tactics`) — дистанции прибытия и допуски:
  `MEET_ARRIVAL≈1.5` (как `TacticsService.RETURN_ARRIVAL`), `DELIVER_ARRIVAL≈2.5`, радиус «в опасности» для `flee`;
  тайм-ауты — из `CatalogLimits`. Тесты: значения читаются; прибытия положительны и конечны; нулей нет.

## Часть Б — адаптер (после А; п. 16 — после 28, п. 20 — после 27)

- [ ] 12. **Хуки `TacticsService`** (`neoforge/tactics`) — применить/снять приказ боевого скрипта без правки
  ядра: `applyDirective(CombatDirective)`, `clearDirective()`; для `attack`/`defend` — принудительная цель
  (обёртка над `spirit.setTarget`) и подопечный (`setWard`); для `flee`/`retreat` — принудительный способ отхода и
  точка (`setForcedRetreat(RetreatMethod, Optional<Point>)`). Снимок прежнего режима/цели берётся через
  `snapshot()` (`StoredTactics`) и возвращается `restore(...)`. Тесты (JUnit, если возможно; иначе GameTest):
  приказ задаёт режим/цель; снятие возвращает прежние; `clearDirective` не роняет тактику.
- [ ] 13. **`SpiritTargets`** (`neoforge`) — разрешение `TargetSelector.Target` в сущность/игрока по образцу
  `KeepDistanceScript.resolveTarget`: `HOSTILE`/`ANIMAL` — ближайший подходящий `LivingEntity`, `PLAYER:<имя>` —
  `ServerPlayer`, `SPIRIT:<uuid>` — дух по UUID, `NEAREST`/`ANY`. Не найдено — `Optional.empty()` (не исключение).
  Счётчик `countEntityQuery` инкрементируется (как в образце). Тесты: игрок по имени; UUID; ближайший враждебный;
  нет цели — пусто; запрос считается.
- [ ] 14. **`AttackScript` / `DefendScript`** (`neoforge/combat`) — `ScriptInstance` поверх `TacticsService`:
  на первом тике `applyDirective`; каждый тик — проверка мира (`attack` — цель мертва/пропала; `defend` —
  подопечный вне угрозы `DEFEND_SAFE_TICKS`); тайм-аут из `CatalogLimits` → `FAILED` с числом; по
  завершении/отмене — `clearDirective`+`restore`. Регистрация в `SpiritScripts`. Тесты: GameTest пп. 21–22;
  JUnit на условие успеха, если вынести чистую часть.
- [ ] 15. **`FleeScript` / `RetreatScript`** (`neoforge/combat`) — то же по образцу п. 14: `flee` — приказ отхода
  без точки, успех по отсутствию врагов `FLEE_SAFE_TICKS`; `retreat` — точка через `SafePointChoice.nearest(...)`
  (home + заглушка лагеря) и метод `HOME`, успех по прибытии `RETREAT_ARRIVAL`, нет точки — `FAILED` «точки отхода
  нет». Регистрация. Тесты: GameTest пп. 23–24; JUnit на выбор точки — в п. 4.
- [ ] 16. **`DeliverScript`** (`neoforge/combat`/`neoforge/social`) — адресат через `SpiritTargets`; предмет из
  инвентаря духа (`SimpleContainer`), при отсутствии — H6 (отказ `FAILED` с числами или шаг `acquire` при слиянии
  28); подход `Approach` до `DELIVER_ARRIVAL`; передача игроку `addItem`, остаток — `DropCollector`; духу — выброс
  рядом; счётчик переданного, `SUCCEEDED` при `delivered ≥ count`. `saveState`/`restoreState` (передано,
  незавершённость). Регистрация. Тесты: GameTest п. 27.
- [ ] 17. **`MeetScript`** (`neoforge/movement`) — подойти к цели (игрок/дух) на `MEET_ARRIVAL` через `Approach`
  (образец `ComeToPlayerScript`); цель пропала — `FAILED` с текстом; тайм-аут `MEET_TIMEOUT_TICKS`. Регистрация.
  Тесты: GameTest п. 26.
- [ ] 18. **`WaitScript`** (`neoforge/social`) — `WaitCounter` п. 8: отсчёт по `spirit.tickCount`, без `sleep`;
  `saveState`/`restoreState` — остаток. Регистрация. Тесты: GameTest п. 25; JUnit — условие в п. 8.
- [ ] 19. **`SayScript`** (`neoforge/social`) — `SayText.normalize(text, SAY_LENGTH_MAX)`; анти-спам
  `SaySpacing.due(lastTick, now, SAY_MIN_INTERVAL_TICKS)`, `lastTick` хранится в духе/скрипте; доставка — H5
  (предлагается: сообщение всем игрокам сервера от имени духа, `<Имя духа> text`). Пустой/спам — `FAILED` с
  числом. Регистрация. Тесты: GameTest п. 25 (перехват через мок-игрока `sendSystemMessage`, как
  `GameTestFixtures.mockPlayer`).
- [ ] 20. **Итог до REST** (после 27) — `result()` боевых — `ScriptResult.Data` (`mode/target/ticks/outcome`),
  `wait` — `ticks`, `deliver` — `item/count/delivered/to`; движок и мост 27 доносят до `GET /v1/tasks/{id}` без
  новых полей. Тесты: успешный `wait` кладёт итог; провал `attack` — `failureReason` с числом, `result` пустой.
  **Без п. 27 не делается.**

## Часть В — GameTest (`gametest/CombatGameTests`, `SocialGameTests`)

Шаблон `arena` 11×8×11, всё внутри площадки, духи/мобы/предметы удаляются (`GameTestFixtures.reset`), без `sleep`,
время по тикам духа (`SpiritRun`), своя пачка (`batch`) на группу, `timeoutTicks ≤ 400` (иначе — комментарий
`[флак]` с обоснованием, как `GameTestTimeoutGuardTests`). Мобы — вручную поставленные цели (ванильные монстры
сами не целятся в духов), полдень, если время суток влияет; выбор духов — по тегу, не по расстоянию. Сцена —
`TacticsScene`/`GameTestFixtures`.

- [ ] 21. **`attack` — атака цели** (пачка `combat_attack`): враждебный моб рядом; план `{"script":"attack",
  "params":{}}`; моб убит, задача `SUCCEEDED`, израсходованы удары по `SpiritGear.ATTACK_COOLDOWN_TICKS`; после
  `SUCCEEDED` режим духа вернулся к прежнему. Подлоги: второй боевой движок (моб не убит/дух не бьёт); приказ не
  снимается (режим остался `AGGRESSIVE`).
- [ ] 22. **`defend` — защита подопечного** (пачка `combat_defend`): мок-игрок и моб, угрожающий ему; план
  `{"script":"defend","params":{"target":"player:<имя>"}}`; защитник бьёт моба, подопечный цел
  (`DEFEND_SAFE_TICKS` вне угрозы) → `SUCCEEDED`; подопечный не найден — `FAILED` с текстом. Подлог: подопечный не
  задаётся (`setWard` не вызван) — дух не защищает.
- [ ] 23. **`flee` — бегство** (пачка `combat_flee`): моб вплотную; план `{"script":"flee","params":{}}`;
  дух отошёл, врагов в радиусе опасности нет `FLEE_SAFE_TICKS` → `SUCCEEDED`; в ловушке (стены) — `FAILED` по
  тайм-ауту с числом. Подлог: скрипт ведёт бой вместо отхода — тест краснеет.
- [ ] 24. **`retreat` — отход к точке** (пачка `combat_retreat`): дом духа поставлен (`/spirit home` или
  `setHome`), дух в 15 блоках; план `{"script":"retreat","params":{}}`; дух на `RETREAT_ARRIVAL` от дома →
  `SUCCEEDED`; дома нет — `FAILED` «точки отхода нет». Подлог: точка не выбирается (бесконечный отход/тайм-аут);
  отход идёт не к дому.
- [ ] 25. **`say`, `wait`** (пачка `social_say_wait`): (а) `say` короткий текст — мок-игрок получил сообщение от
  имени духа, `SUCCEEDED`; (б) второй `say` сразу — `FAILED` с числом (анти-спам); (в) длинный текст урезан до
  `SAY_LENGTH_MAX` (проверка длины в перехваченном сообщении); (г) `wait` 40 тиков — `SUCCEEDED` ровно через
  `spiritTicks ≥ 40`, мир не тронут. Подлоги: анти-спам не работает (спам проходит); `wait` через реальное время.
- [ ] 26. **`meet`** (пачка `social_meet`): мок-игрок в 8 блоках; план `{"script":"meet","params":
  {"target":"player:<имя>"}}`; дух приблизился на `MEET_ARRIVAL` → `SUCCEEDED`; игрок исчез — `FAILED` с текстом.
  Подлог: подход не идёт (стоит на месте и тайм-аут).
- [ ] 27. **`deliver`** (пачка `social_deliver`): у духа 3 булыжника, адресат — мок-игрок; план
  `{"script":"deliver","params":{"item":"cobblestone","count":3,"to":"player:<имя>"}}`; предмет у игрока,
  дух `SUCCEEDED`; предмета у духа нет — `FAILED` с числами (или `acquire`, если H6 выберет его). Подлог:
  предмет не передаётся (исчезает/остаётся у духа); передано больше `count`.
- [ ] 28. **Перезапуск на середине** (пачка `social_restart`): `wait` меж середины и `deliver` до передачи
  прерываются сохранением/восстановлением духа (NBT, как `TacticsPersistenceGameTests`): `wait` продолжается с
  того же места (не с нуля), `deliver` доводит передачу до конца; `TaskSnapshotCodec` сериализует RUNNING-задачу
  с этими скриптами. При слиянии 27 — `GET /v1/tasks/{id}` отдаёт итог. Подлоги: `saveState` не хранит остаток
  (после перезапуска `wait` заново); передано дважды (двойная выдача).
- [ ] 29. **Итог через REST и каталог** (пачка `combat_rest`, после 27): `GET /v1/capabilities` содержит восемь
  скриптов; успешный `attack`/`wait` кладут `result`; провал отдаёт `failureReason` с числами. Подлог: скрипт не
  зарегистрирован — `capabilities` его не отдаёт, план отвергнут.
- [ ] 30. **Масштаб боя** (пачка `combat_scale`, образец `TacticsScaleGameTests`): 20 духов с планами `attack` на
  общую цель; за тик — не больше потолка чтений/запросов сущностей тактики (числа `TacticsService.blockReads/
  entityQueries`), решения тактики — раз в `EVAL_INTERVAL_TICKS`; нет прохода «духи × все сущности» каждый тик.
  Подлог: оценка каждый тик на каждого духа — счётчики взлетают.

## Красными до реализации

Все JUnit-тесты `core/social/{SayTextTests,SaySpacingTests,WaitCounterTests}`, `core/tactics/{TargetSelectorTests,
SafePointChoiceTests,CombatDirectiveTests}`, `core/script/{AttackParamsTests,DefendParamsTests,SayParamsTests,
WaitParamsTests,MeetParamsTests,DeliverParamsTests}` (классов и записей нет), тесты `CatalogLimits` на новые
числа и все GameTest пп. 21–30 (исполнители не зарегистрированы в `SpiritScripts`, `catalog()` не отдаёт их в
`capabilities`, план с ними отвергается валидатором). Назови красные до прогона. Для `fix:`-коммитов —
`scripts/prove-red.sh`; GameTest скрипт не проверяет — подлоги вручную. Пределы в тестах — литералы с проверкой
равенства production-константе (`assertEquals(256, CatalogLimits.SAY_LENGTH_MAX)`,
`assertEquals(1200, CatalogLimits.ATTACK_TIMEOUT_TICKS)`).

## Стоимость в тике

- Боевые скрипты не добавляют работы тактике: приказ задаётся один раз, дальше решение/движение/удары — бюджеты
  существующей тактики (`EVAL_INTERVAL_TICKS`, `countEntityQuery`/`blockReads`). Наблюдение за миром в скрипте —
  `O(1)` проверок состояния цели на тик (ссылка на сущность, не скан).
- `TargetSelector`/`SafePointChoice`/`SayText`/`WaitCounter` — чистая логика без чтений мира; разрешение цели
  (`SpiritTargets`) — один запрос на цель/тик (существующий `countEntityQuery`), не скан.
- `wait` — счётчик `O(1)` на тик и ничего больше; `say` — одно сообщение и проверка интервала.
- `meet`/`deliver` — бюджеты существующего `Approach` (путь не пересчитывается каждый тик); передача предмета —
  `O(1)` на предмет, `addItem` — один вызов на доставку.
- Вложенных циклов «духи × цели» нет; числа — в тестах, не время.

## Завершение

- [ ] 31. `./gradlew test` зелёный; GameTest — через `scripts/gametest.sh` (под `flock`, один сервер на машину),
  в финале — 3 прогона подряд. Коммиты по пунктам (`test:`/`feat:`); `fix:`-коммиты с JUnit-тестом
  сопровождаются выводом `scripts/prove-red.sh <коммит>`. `todo.md` не править. Отчёт: хэши; сколько тестов
  прошло и упало; красные до реализации; подлоги пп. 21–30; время самого медленного GameTest и причина; что не
  сделано и почему (в том числе: сделан ли п. 20 и REST-часть пп. 28–29 — зависит от слияния 27; п. 16 — от 28).

## Не входит (уходит в следующие чеклисты)

- `shoot`, `set_combat_mode`, `guard_area`, `hunt_hostile`, `escort`, `disengage`, `give`/`take`, `report`,
  `stop`/`cancel`/`set_priority` как скрипты (команды/режимы уже есть; вынос в скрипты — позже).
- `SpiritHome` (дом семьи) и лагерь похода — отдельный чеклист «Дом и базы»; здесь только `SafePointChoice` и хук
  точки отхода.
- Быстрые укрепления в бою (замуроваться, укрытие 4×4, роли «строитель/защитник») — отдельный чеклист поверх
  тактики и стройки (чеклист 29); интерфейсы отхода/обороны их не закрывают.
- `eat`, `drink_potion`, `equip`/`repair`частично, `pick_up`, `drop_to_player` (переиспользуются, но отдельными
  скриптами не выносятся).
- Координация группы духов в бою (общая цель, роли) сверх удержания приказа одним духом — отдельный чеклист.
- MCP-адаптер поверх REST; публикация мода и перезапуск сервера.

## На согласование

1. **H1 — числа:** `SAY_LENGTH_MAX` 256, `SAY_MIN_INTERVAL_TICKS` 20; тайм-ауты боевых 1200 тиков (60 с), `flee`
   600, `DEFEND_SAFE_TICKS` 60, `FLEE_SAFE_TICKS` 40; прибытия `MEET_ARRIVAL≈1.5`, `DELIVER_ARRIVAL≈2.5`. Цена
   завышения — дух долго держит бесполезный приказ и обороняется; цена занижения — задача падает раньше, чем бой
   разрешился. `say` 256 — баланс между уборкой чата и полезным сообщением.
2. **H2 — сосуществование с арбитром слоёв:** боевой скрипт задаёт приказ (режим/цель/способ отхода) и уступает
   бой тактике (`LayerArbiter` уже даёт TACTICS приоритет), успех — по миру. Альтернатива — боевой скрипт сам
   бьёт и двигается (второй движок: дублирует правила и ломает `LayerArbiter`). Предлагается первое. Вопрос: как
   надёжно отдать приказ первого тика, пока тактика ещё не забрала управление (задаём приказ в `start`/первом
   безопасном тике; при необходимости — короткий «тик приказа» до `wantsControl`).
3. **H3 — `flee` против `retreat`:** `flee` — спастись от близкой угрозы любым способом отхода (нет конкретной
   цели); `retreat` — организованный отход способом `HOME` к безопасной точке (дом/лагерь). Альтернатива — слить
   в один скрипт с параметром. Предлагается два, как в каталоге.
4. **H4 — точка отхода и лагерь (решение владельца 13.10):** `SafePointChoice.nearest(self, home, camp)` —
   ближайшая безопасная из «дом» и «лагерь похода»; ничья — дом. Лагерь в main ещё не реализован: здесь заглушка
   (`camp = Optional.empty()`), `SpiritHome`/лагерь — отдельным чеклистом; хук `setForcedRetreat(method, point)`
   готов принять лагерь без правок. Цена — сейчас `retreat` ходит только домой; цена иного — дублировать логику
   дома/лагеря здесь.
5. **H5 — доставка `say`:** всем игрокам сервера от имени духа (`<Имя духа> text`) — предлагается; альтернативы —
   только игрокам в радиусе или в лог. Цена «всем» — шум в чате (гасится анти-спамом); цена «в радиусе» — игрок
   не слышит духа вдали.
6. **H6 — `deliver` без предмета:** отказ `FAILED` с числами (предлагается) или шаг `acquire` (чеклист 28) перед
   передачей; и что делать при полном инвентаре игрока — выброс рядом (предлагается) или `FAILED`. Цена отказа —
   стратег сам решает, где взять; цена `acquire` — доставка тянет дальний поход.
7. **H7 — `meet`:** разрешать цели игрок/дух по имени/UUID — как предлагается; включать ли `nearest` (ближайший
   игрок)? Уточнить у пользователя список форм (как `KeepDistanceScript`: имя игрока и UUID).
8. **H8 — формат итога:** `ScriptResult.Data` `mode/target/ticks/outcome` (боевые), `ticks` (`wait`),
   `item/count/delivered/to` (`deliver`) — после слияния 27. Цена — новая схема `Data`; альтернатива — только
   строка, стратегу парсить подстроки.
9. **H9 — порядок с 27/28:** п. 20 и REST-часть GameTest зависят от слияния 27; п. 16 (`deliver` через `acquire`)
   — от 28. Если к финалу их нет в main — всё остальное доводится, а эти пункты честно отмечаются
   «ждёт 27»/«ждёт 28».

## Поправки главного агента (13.10) — обязательны, при расхождении с текстом выше действуют они

- П1. **H1, H2, H3, H4, H6, H8, H9 — приняты** как предложены (числа; приказ тактике, бой ведёт тактика; два скрипта
  flee/retreat; заглушка лагеря через `SafePointChoice`; `deliver` без предмета — отказ с числами, полный инвентарь
  игрока — выброс рядом; итог `Data`; честное «ждёт 27/28»).
- П2. **H5 — `say` слышат игроки в радиусе 64 блоков** от духа (константа `SAY_HEARING_RADIUS` в CatalogLimits) и строка
  в лог сервера; не весь сервер.
- П3. **H7 — цель `meet`:** имя игрока или UUID (как `keep_distance`); `nearest` — не сейчас.
- П4. **Единый координатор (решение владельца 13.10, todo → «Тактическое и стратегическое поведение»):** тактический
  интеллект будет один на всех духов. `CombatDirective` — приказ, приходящий СНАРУЖИ тактики духа: сейчас его отдаёт
  боевой скрипт, позже — координатор. Применение/снятие приказа — через одно API `TacticsService` (п. 12), без
  знания, кто приказал. Распределённую оборону, выбор «кому помогать» здесь не делать — это координатор.
- П5. GameTest: timeoutTicks ≤ 400 без метки-обоснования (сторож), время суток — полдень, мок-игрок —
  `GameTestFixtures.mockPlayer`, духи — по тегу; на ПК GameTest — `./gradlew runGameTestServer` (flock в Git Bash нет).
  Тесты по ходу работы — точечно, полный JUnit делает хук (AGENTS.md).
