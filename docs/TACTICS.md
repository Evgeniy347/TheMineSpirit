# Ядро тактики

Пакет `com.theminespirit.core.tactics`: чистая функция «обстановка → решение» без Minecraft. Адаптер раз в
`EVAL_INTERVAL_TICKS` (10, предварительно) или по событию «ударили» собирает `Situation` и вызывает
`TacticsEngine.step(Situation, TacticsMemory, nowTick)`. Ответ — `TacticalOutcome(decision, action, retreatMethod,
callHelp, state, mode, returnTo, trace, memory)`; `memory` адаптер передаёт в следующий вызов. Числа правил живут в
четырёх JSON `src/main/resources/data/theminespirit/tactics/`, код их не дублирует.

## Конвейер одного вызова

1. Режим: приказ стратега `Orders.mode` запоминается; таблица `modeTransitions` может временно сменить режим (устал,
   нет зоны, нет подопечного); решение этого вызова считается уже в новом режиме.
2. Факты (`Situation`, враги отсортированы по `(дистанция, тип, HP-доля, id)`, сверх `MAX_ENEMIES = 16` хвост
   сворачивается в запись «ещё n того же типа») → производные (`EnemyMetrics`, запас победы, способы отхода).
3. Жёсткие правила (`rules.json → hard`): первое подходящее по убыванию `priority` с `decision` решает; `exclude`
   вычёркивает варианты; вычеркнуто всё — `RETREAT` при наличии способа отхода, иначе `FIGHT`. Любое `RETREAT` (правило
   или оценка) без единого способа отхода подменяется правилами `then.noRoute` (первое подходящее, невычеркнутое):
   `no_route_fight_targeted` — FIGHT, если враг целится в духа; иначе `no_route_ignore` — IGNORE (враг вплотную
   обрабатывает `cornered_fight`). Подмена hold не наследует (`HoldStatus.NONE`). Крипер:
   `explosive_close_retreat` отводит на 5 блоках, `explosive_band_retreat` (приоритет 84) держит отход, пока дух в
   состоянии RETREATING и крипер ближе 8 блоков — полоса гистерезиса данными против качки на границе 5/6 блоков;
   `shoot_explosive` стреляет по взрывчатке от 3 блоков при луке.
4. Оценка `score(вариант) = Σ вес × фактор` для `FIGHT / RETREAT / IGNORE`, ничья — по `tieBreak`.
5. Гистерезис: прежнее решение держится `holdTicks` (кроме жёсткого правила, потери HP больше `hpDropRelease` за окно
   или вычёркивания) и получает `stickiness` к счёту после окна. Удерживается только решение, принятое оценкой
   (`score`, `hold`); решение жёсткого правила, единственного кандидата или «вычеркнуто всё» удержания не создаёт
   (`TacticsMemory.previousSource = RULE`): окно начинается с первого решения по оценке.
6. Действие (`actions`), способ отхода (`retreat`), состояние боя (`states`), `returnTo` (возврат к якорю после боя).

Порядок слоёв тика зафиксирован в `LayerOrder`: `POLICY > TACTICS > STRATEGY > IDLE`, прерванный слой возобновляется.
Политики безопасности и арбитраж тика — вне этого пакета.

## Схема JSON

Любая ошибка при загрузке — `IllegalStateException` вида «файл → путь: что не так. Проверьте: что сделать».
Неизвестные поля, дубли ключей и пустые таблицы запрещены. Загрузка: `TacticsConfig.loadDefault()` (один раз за
процесс) или `TacticsConfig.fromJson(enemies, weights, rules, modes)` из строк. Границы чисел держит
конструктор записи (`FactChecks.range`), парсер читает число без границ, а `ConfigNode.build` превращает отказ
конструктора в ошибку с путём в файле (`weights.json → scales.armorCap`); границы в одном месте.

| Файл | Содержимое |
|---|---|
| `enemies.json` | `{"enemies": [{type, hp, dps, speed, ranged, explosive, swims, flies, tier}]}`; ключ `minecraft:[a-z_]+`, обязателен `default` для неизвестного типа |
| `weights.json` | `scales` (масштабы факторов), `weights` (матрица фактор × вариант, полная), `tieBreak`, `stickiness`, `holdTicks`, `hpDropRelease` |
| `rules.json` | `hard`, `actions`, `modeTransitions`, `states`, `retreat` (`methods` + `callHelp`); `id` правил уникальны на весь файл |
| `modes.json` | `returnTolerance` и на режим `{initiates, radiusBase, engageRadius, pursuitRadius, anchor, leash, returnToAnchor}` |

Правило: `{"id", "priority", "when": [атомы], "then": {...}}`. Атом: `{"fact", "op", "value"}`, `op` ∈
`< <= > >= == != in`; числа сравнивают все операторы, булевы факты — `==`/`!=`, перечисления — `==`, `!=`, `in`
(значение — список). `when` — конъюнкция, пустой список — «всегда».

- `hard.then`: `{"decision": "RETREAT"}`, `{"exclude": ["FIGHT"]}` или `{"noRoute": "FIGHT"}` (подмена RETREAT без пути;
  такие правила не участвуют в обычном проходе).
- `actions.then`: `{"action": "SHOOT"}`; решение выводится из действия, для каждого решения нужно замыкающее правило
  с пустым `when`.
- `modeTransitions`: `{id, from: [режимы], to, when}`; первое подходящее по порядку в файле.
- `states`: `{id, from: [состояния], to, decision?, when}`; `decision` — необязательное условие на решение.
- `retreat.methods`: `{id, method, requires, utility: {base, fact?, perUnit?}, bonus: [{when, add}]}`; полезность
  `base + perUnit × факт + бонусы`, в пределах 0..1; несколько строк одного способа допустимы (лучшая выигрывает);
  условия отхода не могут ссылаться на `env.escapeAvailability`.

## Факты

Единый реестр `FactName` с типом `NUM / BOOL / ENUM`; полный список и типы — в этом enum. Группы: `self.*` (HP-доля,
броня, урон, скорость, еда, блоки, дальний бой, ведро воды, союзники, лечилки), `enemy.*` (число, видимые, ближайший,
ближайший взрывной, суммарные HP и урон, быстрейший, флаги стреляет/взрывается/плавает/летает/целится в нас),
`env.*` (лава, обрыв, свет, ночь, дом, укрытие, вода, `escapeAvailability` = число доступных способов отхода),
`order.*` (режим, приказанный режим, инициирует ли, зона, `leashLeft`, `engageLeft`, подопечный, помощь), `time.*`
(`sinceHit`, `sinceEnemySeen`, `inState`), `state.tactical`, `derived.winMargin`, `derived.speedRatio`.
`leashLeft = pursuit − расстояние от якоря до цели` (лучшая из целей), `engageLeft` аналогично для радиуса реакции;
«нет ограничения» — `Assessment.UNBOUNDED` (1000).

## Факторы

Каждый фактор — функция `Situation → [−1; 1]` (порядок enum `Factor` = порядок членов суммы); масштабы — `weights.json
→ scales`. `ownEhp = hp / (1 − min(armorCap, armorPerPoint × броня))`, `timeToKill = HP врагов / max(minDps, свой dps)`,
`timeToDie = ownEhp / max(minDps, Σ dps врагов)`.

| Фактор | Определение |
|---|---|
| `BIAS` | 1 |
| `WIN_MARGIN` | `clamp(log2(timeToDie / timeToKill) / winMarginDivisor)`; врагов нет — 0 |
| `LOW_HP` | `clamp01((lowHpFloor − доля HP) / lowHpFloor)` |
| `ENEMY_PRESSURE` | `clamp01(1 − timeToDie / timeToKill)`: 0, пока дух убивает врагов быстрее, чем они его |
| `PROXIMITY` | `clamp01(1 − ближайший / proximityRange)` |
| `RANGED_EXPOSURE` | `clamp01(стрелков / rangedFull) × clamp01(1 − ближайший стрелок / rangedRange)` |
| `EXPLOSIVE_NEAR` | `clamp01(1 − ближайший взрывной / explosiveRange)` |
| `ALLY_SUPPORT`, `HEALING_AVAILABLE`, `ESCAPE_ROUTE` | `clamp01(число / allyFull, healFull, routesFull)` |
| `TERRAIN_RISK` | максимум риска лавы `1 − лава/lavaRange` и обрыва `(высота − dropSafe)/dropRange` |
| `DARKNESS` | `max(1 − свет/15, ночью nightFloor)` |
| `HUNGER` | `clamp01((hungerThreshold − еда) / hungerThreshold)` |
| `PROTECT_URGENCY` | 1, если режим protect и подопечного бьют |
| `LEASH_PRESSURE` | `clamp01(1 − leashLeft / leashMargin)` при наличии врагов |

## Как добавить

- **Врага**: строка в `enemies.json` с ключом `minecraft:имя`; без неё враг оценивается профилем `default`.
- **Правило**: строка в нужной секции `rules.json` с новым `id` и приоритетом; добавить сценарий в
  `src/test/resources/tactics/scenarios.json`, который его вызывает (сторож `TacticsRuleCoverageGuardTests` краснеет
  на правиле без сценария).
- **Режим**: значение в `CombatMode`, запись в `modes.json`, перечисление в `FactName.enumValues`, правила смены режима.
- **Способ отхода**: значение в `RetreatMethod`, строка в `retreat.methods` с `requires`, полезностью и сценарием.
- **Вес или масштаб**: правка `weights.json`; после неё пройти `TacticsScenarioTests` — сценарии есть истина, веса
  подгоняются под них, а не наоборот.
- **Сценарий** (`scenarios.json`): `name`, `expect`, поля обстановки; сверх обязательного списка задания обязательно `why`
  (почему именно так; спорное решение геймдизайна помечается «СПОРНО» и ждёт согласования). Решение по счёту должно
  переживать сдвиг любого одного веса на ±10 % (`TacticsSensitivityTests`); если нет — сценарий помечается
  `"boundary": true` и `"pair"` (имя сценария с другой стороны границы). `memory.by` — `score` или `rule`: чем принято
  прежнее решение (удержание создаёт только `score`).

## Способы отхода

Полезность и условия — в `rules.json → retreat.methods`, числа взяты из п. 11 задания (tasks/19-tactics-core.md), ревью их приняло как есть:
`HOME` 1 − дистанция/160, `COVER` 0.4 (0.8 против стрелков), `KITE` 0.7, `WATER` 0.6 (ведро 0.55), `BARRICADE` 0.5, `PILLAR` 0.4.
`AWAY` (бег прочь по открытой местности, полезность 0.2, ниже любого другого) добавлен ревью R6: условие — дух не медленнее
врага, враг дальше 3 блоков и нет стрелков (в спину бьют из лука) (ближе — `cornered_fight` обязан оставаться возможным). Фактор `ESCAPE_ROUTE` считает все способы,
поэтому его вес RETREAT снижен с 0.6 до 0.3: открытое поле не должно подталкивать к отходу так же, как укрытие.

## Как читать trace

`TacticalOutcome.trace()` (`Trace.explain()` даёт текст): `rule` — id сработавшего жёсткого правила или `score` (по
оценке), `hold` (удержание прежнего решения), `excludingRules`, `totals` (счёт каждого варианта), `factors` (значение
фактора и вклад в каждый вариант), `actionRule`, `retreatRule`, `stateRule`, `modeRule`, `whyNotFight` — при
решении не `FIGHT`: вычеркнувшие бой правила, решившее правило или три фактора с наибольшим перевесом над боем.

## Границы

Не входят: адаптер (чтение мира, исполнение действий, `set_combat_mode`), стрельба, командная тактика, политики
безопасности. Вызов не делает ввода-вывода и не меняет аргументы; стоимость — O(15 факторов + правила + враги ≤ 16).
