# Карта навигации: кто строит маршрут, кто ведёт духа и почему он кружит

Разведка 10.11 (ветка `task/navmap` от main `c928979a`). Поведение не менялось. Числа — `scripts/logq.sh --main`
по базе основного сервера (`routes`, `stuck`, `spirit_samples`) за 11.07 16:26 – 11.10 04:37 UTC, 6 духов.
Проверялись: `mech/movement` (`core/movement`, `core/navigation`, `core/route`), `mod` (`Approach`,
`SpiritNavigation`, `movement`, `policy`), `board`, `policy`.

## Итог

Слоёв не три, а четыре, и они не делят одну карту мира:

1. **Ваниль** — `GroundPathNavigation` + `SpiritNavigation`/`SpiritNodeEvaluator`: обычные ноги, 32 блока / 512 узлов;
   её строит `Approach` (49 файлов с `new Approach`) и скрипты движения (18 в `MovementScriptPack`: часть через
   `Approach`, часть своими сценариями).
2. **Граф троп** — `PathNetworkService` (`NetworkBuilder/NetworkRoute/PathSegment`): редкие ноги по протоптанным
   дорожкам, поверх ванили (`Approach.followNetwork`).
3. **Свой A*** — `NavAStarSession/NavSegmentedSession` (`mech/movement/core/navigation`): включается только как
   спасение (`StuckRescue`) и в `dig_out`; **94,1 % его попыток — «маршрут не найден»**, а `canBreak/canPlace = false`.
4. **Частные планировщики** — ~40 классов `core/movement` + скрипты `dig_out/SurfaceRoute`, `climb`, `bridge`,
   `cross_water`, `swim`, `step_over`, `WallEscape`, `ReflexExecutor` и спасатели доски.

Кружение — не один баг: слой 3 не умеет ломать/ставить, поэтому `dig_out` уходит в ручные ступени; вода
(`ReflexExecutor.leaveWater`) каждый тик пытается выйти и не выходит (365 401 попытка); доска ставит новую задачу
поверх тех же.

## Схема

```
граф/доска (ticket) → скрипт (MovementScriptPack / MiningScriptPack)
  → Approach (49 файлов) ──┬─ ваниль GroundPathNavigation + SpiritNodeEvaluator   (обычные ноги)
                         ├─ PathNetworkService (граф троп)                       (когда ваниль не дошла)
                         ├─ StuckRescue → RouteDetour → NavSegmentedSession       (свой A*, срезы 10 мс)
                         │     → RouteWalk (шаги WALK/SWIM/BREAK/PLACE/PILLAR)    (спасение, dig_out)
                         └─ PassageWalk/GateCrossing (двери, калитки)
  → частные скрипты: dig_out/SurfaceRoute, climb, bridge, cross_water, swim, step_over…
SpiritEntity.customServerAiStep: WallEscape (каждый тик) → SpiritPolicies → ReflexExecutor (вода/лава/воздух,
  каждый тик, перекрывает задачу) → SpiritReflexes (личное пространство, снаряды)
доска: StuckSpirits (mark/clear) → RescuePlans / GraphRescuePost / PitRescue → task nodes
```

## Классы: роль, кто зовёт, дубль

| Класс | Роль | Кто зовёт | Дубль |
|---|---|---|---|
| `GroundPathNavigation`, `SpiritNavigation`, `SpiritNodeEvaluator` | ванильный A* и шаги; вода/двери/паутина | `Approach.moveTo`, 49 файлов | всё остальное: свой A*, граф троп, частные скрипты |
| `Approach` | оркестратор ноги: таймаут, застревание, сеть, спасение | скрипты движения и работы | `RouteWalk` (свой маршрут), скрипты-спасатели |
| `StuckWatcher` | «не двигался 40 тиков > 0,05» | `Approach` | `StuckSituation`/`SelfEscape`, `StuckSpirits` доски |
| `PathRefreshPolicy` | перестроить ванильный путь: сдвиг >1, путь кончился, 20 вызовов | `Approach` | `RouteThrottle` (200 тиков), `RouteFailures` |
| `PathNetworkService`, `NetworkBuilder`, `NetworkRoute`, `NetworkAreas`, `PathSegment` | граф протоптанных троп, ноги по ~3 блока | `Approach.followNetwork`, `BuildSchematicScript` | ваниль на ногах; `RouteStore`/`core.board.route` (план троп поселения) |
| `StuckRescue` | разрешает и ведёт спасение своим A* | `Approach` | `RescuePlans` доски, `DigOutScript` |
| `RouteDetour` | запуск своего A*, троттлинг 200 тиков, кэш провалов, лог `[route]` | `StuckRescue` | сам себе дубль: `[route]` в тексте и строка `routes` |
| `RouteWalk` | исполнитель шагов маршрута, 60 тиков/шаг, бюджет | `RouteDetour`, `SurfaceRoute`, `DigOutRoute` | ванильные шаги `GroundPathNavigation`, `WalkAscent` |
| `RouteMutations` | ломка/установка/столб по шагу | `RouteWalk` | `BlockBreaker`/`BlockPlacer`, `DigOutScript`, `PillarColumn` |
| `RouteHistory`, `core.route.RouteTrail` | крошки пройденного | `RouteWalk` | `RouteTrailBook` (след троп поселения) |
| `PassageWalk`, `GateCrossing`, `Passage` | двери/калитки/ворота на пути | `Approach`, `RouteWalk` | `SpiritNodeEvaluator` (`WALKABLE_DOOR`) и сама ваниль |
| `StandWalker`, `StandChoice` | стоянка у цели и подход к ней | `ApproachScript`, `GotoBlockSession` | `GotoBlockSession`, `ReachApproach`, `SelfEscape` (свои стоянки) |
| `NavAStarSession` | A* по `NavWorld`, куча, узлы | `RouteDetour` (через `NavSegmentedSession`), `RescueReach` | ванильный `PathFinder`; `SurfaceEscape` |
| `NavSegmentedSession`, `NavSegmentedRoute` | поиск кусками 10 мс, ≤32 сегмента | `RouteDetour` | ванильные частичные пути (`Path.canReach`) |
| `NavMoves`, `NavMoveCosts`, `NavActionCosts` | ходы (шаг/подъём/спуск/столб/диагональ) и цены в тиках | `NavAStarSession` | `BodyMotion`/`WalkAscent` (шаги), `MoveCost` (оценка) |
| `NavSettings`, `NavContext` | бюджеты, `canBreak/canPlace` | `RouteDetour` (`false,false`), `RescueReach` (`false,false`) | `NavSettings.defaults` (`true,true`) не используется |
| `NavWorld`, `LevelNavWorld`, `NavCacheWorld`, `NavCachedWorld/Region/Chunk` | вид мира и кэш чанков | `RouteDetour`, `RescueReach` | `RouteLevelView`, `LevelSafetyView`, `LevelNavWorld` |
| `NavGoal*`, `NavFavoring`, `NavReversibility`, `NavFallTable`, `NavPillarEscape`, `PillarDescent`, `NavPathCutoff`, `NavRouteSteps`, `NavNode`, `NavPath`, `NavOpenSet`, `NavBinaryHeapOpenSet`, `NavResult` | цель, веса, выдача шагов | `NavAStarSession`, `NavSegmentedSession` | `Goal/GoalNear`, `NavRouteSteps → RouteStep` |
| `NavigationChunkRequests` | тикеты чанков на время поиска | навигация (`RouteDetour`) | `NavigationTickets` (mod) — второй набор для того же |
| `BodyMotion`, `BlockKind`, `Box3` | проходимость тела, вид блока, габариты | `SurfaceEscape`, `PillarDescent`, `SelfEscape` | `SpiritNodeEvaluator`, `NavBlockType`, `RouteCell` |
| `ClimbRoute` | диапазон лестницы/лианы | `ClimbScript` | `WalkAscent.CLIMB` |
| `DescentPath` | проверка безопасного спуска | `SurfaceRoute`, `RescueReach` | `NavFallTable`, `CarefulPath` |
| `DigOutObstacle` | «копать ли клетку» | `DigOutScript` | `NavMoveCosts.break` |
| `DisengageRoute` | точка отхода от угрозы | `DisengageScript` | `ProjectileDodge`, `Flee` |
| `EscapeRestore` | не замуровать себя | `DigOutRestore` | проверки `RouteMutations` |
| `ExploreRoute` | точки обхода | `ExploreScript` | `WanderPolicy` |
| `LineObstacle` | первая занятая клетка на линии | `DeadEndRefusal` | ванильный `Path` |
| `MoveCost` | оценка пути в тиках | `GotoBlockSession`, `StandChoice` | `NavActionCosts` |
| `PitEscape` | куда край ямы | `PitExitPlanner`, `SelfEscape`, `StuckSituation` | `StuckSituation` |
| `PitExitPlanner`, `PitStairPlanner` | план выхода из ямы (ломка/ступени) | `DigOutAction`, `PitRescue`, `PitWalkOut` | `SurfaceEscape`, `DigOutScript` |
| `PitWalkOut`, `PitWalkStep` | выход без ломки | `PitRescue`, `DigOutExit` | `SelfEscape.STEP` |
| `SelfEscape` | план выхода (DIG/STEP/DESCEND/GOTO) | `RescuePlans`, `TicketTarget`, `NavFuzzChecks` | `PitExitPlanner`, `DigOutScript` |
| `SealedSpace`, `SurfaceLevel`, `SurfaceEscape` | замурован / на поверхности / путь к небу (BFS) | `DigOutScript`, `DigOutSealedView`, `SurfaceRoute` | свой A*, `DigOutRoute` |
| `StuckSituation` | FREE/WALLED/DEEP_PIT | `SelfEscape`, `NavFuzzChecks` | `StuckSpirits` доски, `StuckWatcher` |
| `ToolBreakCost`, `CheapBlocks`, `WaterCrossingPlanner` | чем дешевле ломать/мостить/заливать | `PitRescue`, `DigOut`, `Bridge`, `RouteMutations` | `NavMoveCosts` (цены) — отдельно |
| `WalkAscent` | пошаговая команда MOVE/JUMP/CLIMB/SWIM/DIVE | `RouteWalk` | ванильные шаги; ходы `NavMoves` не совпадают |
| `WalkOutRules`, `WanderPolicy`, `SearchMillis` | данные обхода, случайная точка, время | `PitWalkOut`, `WanderScript`, `SurfaceRoute` | — |
| `WaterTraverse` | фаза вода→берег | `SwimStep` | `WalkAscent.SWIM`, `ReflexExecutor.leaveWater` |
| `ProjectileDodge`, `ProjectileTrajectory` | уклонение от снаряда | `ProjectileDodgeKeeper` | `DisengageRoute` |
| `RoutePlan` (`core.movement`) | точки патруля/маршрута | `PatrolScript`, `GraphInputs`, `RouteStore` | `core.route.Route` и `core.board.route.RoutePlan` — три «RoutePlan» |
| `RouteFailures`, `RouteThrottle`, `RouteCell`, `RouteWorld`, `RouteStep`, `Route` | кэш провалов, троттлинг, клетка, шаг | `RouteDetour`, `RouteWalk`, `NavRouteSteps` | `RouteDetour` сам кэширует провалы; `WalkReport` |
| `DigOutScript`, `DigOutRoute`, `DigOutExit`, `DigOutBackfill`, `DigOutRestore`, `DigOutSealedView` | `dig_out`: свой маршрут к небу, затем ручные ступени | `MiningScriptPack`, `DigOutAction` | `SurfaceRoute`, `SelfEscape`, свой A* |
| `SurfaceRoute` | мост из `SurfaceEscape` в `RouteWalk` | `DigOutScript` | лестничный fallback `DigOutScript` |
| `RescueReach`, `RescueNavWorld` | «помощник дойдёт?» своим A* (`WALK_ONLY`) | `GraphRescuePost` | ваниль + `Approach` (второй ответ на тот же вопрос) |
| `ClimbScript`, `BridgeScript`, `CrossWaterScript`, `SwimScript`, `SwimStep`, `StepOverScript` | частные сценарии препятствий | `MovementScriptPack` | `WalkAscent`, `WaterTraverse`, `RouteDetour` |
| `WallEscape` | шаг из стены, каждый тик | `SpiritEntity.customServerAiStep` | `SelfEscape`, `DigOutScript.embedded` |
| `PillarColumn`, `PillarUp/DownScript`, `PillarDismantler` | столб вверх/вниз | `FillStands`, `PillarUpScript` | `RouteMutations.pillar`, `NavMoves.PILLAR` |
| `DescentSession`, `DescentWidenSession`, `SafeMineSession`, `VeinTunnelSession`, `MineStepWalker` | спуск в шахту и копание | mining-скрипты | `DigOutScript`, `WalkAscent` |
| `ReflexExecutor`, `HazardReflex`, `SpiritPolicies` | вода/лава/огонь/воздух, перекрывает задачу каждый тик | `SpiritPolicies.tick` | `SelfEscape`, `SurfaceEscape`, `Swim`, `StuckRescue` |
| `SpiritReflexes`, `PersonalSpaceKeeper`, `ProjectileDodgeKeeper` | личное пространство, снаряды | `SpiritEntity.customServerAiStep` | `ProjectileDodge` |
| `DigOutAction`, `PitRescue`, `StuckSpirits`, `RescuePlans`, `GraphRescuePost` | доска: метка, «сам/помощник/невозможно», задача | `Board`, `GraphRescuePost` | `StuckRescue`/`StuckWatcher` |
| `core.board.route` (`RoutePath`, `QuarryPaths`, `RouteTrailBook`, `RouteStore`) | план троп и шахт поселения (блоки, не шаги духа) | `GraphRoutes`, REST | `PathNetworkService` (фактические тропы) |

## Числа

Воспроизведение — три запроса (подставить начало окна, мс):
`logq.sh --main sql "SELECT purpose,status,COUNT(*) FROM routes WHERE ts>=… GROUP BY 1,2"`,
`… "SELECT event,COUNT(*) FROM stuck WHERE ts>=… GROUP BY 1"`,
`… "SELECT spirit,ts,x,y,z,state,script FROM spirit_samples WHERE ts>=…"`.

- **Свой A***: 11 041 попытка за 3 суток. FOUND 477 (4,3 %), PARTIAL 176 (1,6 %), **NONE 10 388 (94,1 %)**.
  «Возврат» 9 260 (457/174/8 629), `dig_out` 1 781 (20/2/1 759). FOUND: p50 148 мс, p95 6 502, max 14 705;
  NONE: p50 46 мс, p95 2 178. Узлов FOUND p50 306 / p95 3 383.
- **Ванильный путь**: строк в БД нет — `countPathBuild()` живёт только в памяти (`SpiritEntity`), `[route]` пишет
  лишь свой A*. Доля по числу поэтому **не измеряется**; по коду ваниль обслуживает все обычные ноги (49 файлов
  с `new Approach`, 18 скриптов движения), свой A* — только спасение и `dig_out`. Это дефект наблюдаемости, его
  закрывает этап 1.
- **Перепланировки**: 3 194 из 11 041 (28,9 %) — тот же дух и та же цель повторно в пределах 60 с. Максимальные
  цепочки: spirit-2 — 1 052, spirit-3 — 445, spirit-5 — 13, spirit-4 — 12, spirit-1 — 9, spirit-6 — 9.
  Троттлинг своего A* — 200 тиков; ванильный — сдвиг цели >1 блока, конец пути, затем каждые 20 вызовов.
- **Метки застревания**: mark 2 902, clear 2 912, rescue_start 10, rescue_done 1, rescue_failed 1. Из mark 2 760 —
  по UUID (старый импорт, причина испорчена кодировкой), 142 — по именам (JSONL, причина читается).
- **Сэмплы**: 11 231, шаг 30 с; 58 серий ≥4 одинаковых клеток подряд (≥2 мин без движения).

### Кружение: топ-10 по журналу

Метрика «та же клетка ≥3 раз за минуту» по `spirit_samples` **недостижима**: 2 сэмпла в минуту. Ближайшее
наблюдаемое окно — 30 мин в радиусе 24 блока: 6 эпизодов (3 возврата в клетку), все у стройки
(`build_schematic`, 11.07 16:39–22:47), а не «в поле». Настоящее кружение в журнале — многочасовой застой и
повтор одной цели:

| # | Дух, клетка | Когда | Длина | Причина по журналу |
|---|---|---|---|---|
| 1 | spirit-6 `-99,52,-417` | 09.11 12:37 → 10.11 03:55 | 1 474 сэмпла (12 ч 17 м) | `dig_out`; вода: `[water] выход из воды` попытка **365 401**, берег не достигнут |
| 2 | spirit-4 `-99,52,-417` | 14:06 → 03:55 | 1 459 (12 ч 09 м) | то же |
| 3 | spirit-3 `-99,52,-417` | 14:39 → 03:55 | 1 452 (12 ч 05 м) | то же |
| 4 | spirit-2 `-99,52,-417` | 15:13 → 03:55 | 1 445 (12 ч 03 м) | то же |
| 5 | spirit-5 `-99,52,-417` | 15:26 → 03:55 | 1 443 (12 ч 02 м) | то же |
| 6 | spirit-1 `-100,56,-413` | 22:45 → 03:56 | 622 (5 ч 11 м) | вода, цель `-100,62,-413`; попытка 1 164 в 03:56 |
| 7 | spirit-2 `-201,71,-485` | 07.11 20:08 → 08.11 23:55 | 231 | `[stuck]` «застрял в (x=-200,64, y=71, z=-484,40) по пути» к `-205,65,-505` |
| 8 | spirit-1 `-100,56,-412` | 09.11 17:22 → 18:52 | 178 | та же вода |
| 9 | spirit-1 `-100,56,-412` | 19:39 → 21:09 | 178 | та же вода |
| 10 | spirit-1 `-100,56,-412` | 21:22 → 22:44 | 164 | та же вода |

Повторы целей своего A*: spirit-3 `dig_out` к `-180,74,-505` — 1 173 попытки, 0 FOUND; spirit-2 «возврат» к
`-181,67,-518` — 1 056, 0 FOUND; spirit-4 «возврат» к `-171,71,-489` — 142, 1 FOUND.

## Предложение: один планировщик, один исполнитель, один след

Свой A* уже умеет ходы со стоимостью (`NavMoves`/`NavMoveCosts`), но **не включает ломку/установку**
(`canBreak=false`, `canPlace=false`) и не выдаёт `BREAK/PLACE/PILLAR` (`NavRouteSteps` — только WALK/SWIM).
Поэтому «один планировщик» — это включить недостающие ходы, а не писать третий.

Этапы по 1–3 дня; каждый заканчивается тестами и мерой. Решение по этапам — за владельцем.

1. **Наблюдаемость (1–2 дня).** В `routes` — колонка `planner` (`vanilla/network/nav/script`); писать каждый
   расчёт ванили (`Approach`, `FollowScript`, `EscortScript`) и каждое решение сети; команда `logq route <дух>`.
   Тесты: загрузчик `logdb` + `CrossRouteStageScenarioTests` (крупный сценарий: несколько духов, все слои);
   мера: 100 % ног имеют строку, доля и перепланировки считаются запросом.
2. **Ходы и цены в своём A* (2–3 дня).** `canBreak/canPlace=true`; `NavRouteSteps` выдаёт BREAK/PLACE/PILLAR;
   цены: ломка — твёрдость/лучший инструмент + надбавка, установка — `placeCost`, столб — прыжок + установка,
   вода — `SWIM`, копка вверх — ломка + подъём. Один `NavWorld` из `LevelNavWorld` + тикеты чанков.
   Тесты: `NavFuzzTests` + живые фикстуры (яма, замурован, водяная ловушка, лестница); мера: ≥95 % фикстур
   находятся без fallback, p95 ≤50 мс, ≤8 000 узлов (40 000 = 1,2 с и ~200 МБ, см. `pathfinding-range.md`).
3. **Свой исполнитель (2–3 дня).** В `Approach` вместо `GroundPathNavigation.moveTo` — `RouteWalk`
   (шаги, 60 тиков/шаг, двери `PassageWalk`, плавание); удалить `PathRefreshPolicy`, `StuckWatcher`, `RouteDetour`.
   Тесты: крупный сценарий «джунгли/вода/двери/несколько духов»; одна сцена GameTest на дверь+воду.
   Мера: 100 % обычных ног через свой исполнитель; застой (≥4 сэмпла в клетке) <5 за 3 суток (сейчас 58);
   перепланировок <10 на духа-час (сейчас в среднем 26, пики 1 052).
4. **Частные планировщики → цели и цены (2–3 дня).** `SurfaceEscape`, `SelfEscape`, `PitExitPlanner`,
   `PitWalkOut`, `ClimbRoute`, `WaterTraverse`, `Bridge`, `CrossWater`, `StepOver` становятся целями
   (`surface`, `pit_exit`, `bridge`, `water`, `climb`) или ценами ходов; `StuckSituation` остаётся только
   входом цели; `dig_out` — цель «на поверхность» с ценой ломки/установки; `ReflexExecutor` — только мгновенная
   опасность (лава/огонь/воздух) с пределом попыток и эскалацией в план.
   Тесты: те же fuzz-семена + фикстуры; сторож-поиск: вне `core/navigation` нет постройки пути; мера:
   0 циклов `leaveWater` >60 попыток, 0 «маршрут не найден» на фикстурах.
5. **Уборка и groupescape (1–2 дня).** Убрать тройной `RoutePlan`, `RescueReach` → обычный запрос достижимости,
   мёртвые действия `RouteMutations`; groupescape отдаёт «групповая цель + вода/ступени» ценами и целями, а не
   вторым планировщиком. Мера: один планировщик, один исполнитель, один след; метрики этапов 2–4 держатся.

**Groupescape (параллельно).** Он трогает `PitExitPlanner`, `WaterTraverse`, `ReflexExecutor`, `Approach` — те же
файлы. Договор: этап 2 добавляет ходы (вода/ступени) в `NavMoves`, этап 4 превращает групповой выход в
`GoalComposite` с ценой; до этого его файлы не правим, строки `routes` помечаем `purpose=group`.

## Риски

- **Лицензия.** Файлы `Nav*` несут шапку «Из Baritone, LGPL-3.0» — это копия, а не идея; до распространения нужен
  clean-room или решение владельца.
- **Смена поведения** — самая большая в проекте; каждый этап отдельно согласуется и проверяется в копии.
- **Производительность**: 40 000 узлов = 1,2 с и 187–244 МБ мусора; бюджет 8 000 узлов / 10 мс, срезы по тикам.
- **Незагруженные чанки**: «неизвестно» ≠ воздух; без тикетов маршрут в новом месте не строится (уже есть
  `NavigationChunkRequests`/`NavigationTickets`, их надо свести).
- **Граф троп** (`PathNetworkService`) — четвёртый источник; на этапе 3 решить: цель внутри `Nav` или удалить.
- **Логи**: 30-секундные сэмплы не доказывают «кружение за минуту»; быстрый след появится на этапе 1.

## Техдолг (замечено по ходу; в этой задаче не правилось — поведение не менялось)

- Тройное имя `RoutePlan` (`core.movement`, `core.board.route`) и `Route` (`core.route`): не отличить по имени.
- `routes` до 10.11 не писала `reason`; старые NONE нельзя объяснить (10 191 строка без причины).
- Текст `messages` из старых логов импортирован в cp1251-мойибейке; причины читаются только из `routes`/`stuck`.
- `ReflexExecutor.leaveWater` без предела попыток и без эскалации — 365 401 попытка за 13 ч.
- `SpiritNavigation` дублирует воду/двери в `SpiritNodeEvaluator`, а `PathNetworkService` — в `PathSegment`.
