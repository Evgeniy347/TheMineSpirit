# Матрица пересечений: ось «механика × прерывание»

Часть `docs/testing/CROSS-MATRIX.md`. Основная матрица M01…M24 отвечает, что будет, если две механики
встретятся, но сравнивает **состояние на входе** и один прогон графа. Оси времени в ней нет: сценарий ставит
задачу и проверяет, кто её получил, а не что осталось, если голод, угроза, ночь, задача выше по приоритету,
смерть или рестарт пришли **после** первого поставленного блока.

С этапа 2 (аренда временной правки) временная правка этих механик живёт в `LeaseBook`: клетка помнит вид
(`PLACED`/`BROKEN`), владельца и назначение. `cancel`, смерть духа и рестарт сервера бросают аренду
(`ORPHANED`), мир при этом не меняется; скан (`FloatingScan`, `WorldAreaScan`) считает временными только клетки
`ORPHANED`, поэтому действующие подмости чужого духа не сносит. Уборка брошенного — узел графа `undo_lease`
(этап 3); до него брошенные клетки видны скану как след.

`ОК` — выход корректен: остаётся только видимое графу (брошенная аренда, нужда, бронь/тикет с владельцем).
`Н` — механика оставляет мусор или застрявшего духа, которого граф не видит; номер — задача из
`docs/research/interrupt-map.md` → «Задачи починки».

| id | механика | прерывание | что остаётся | видит ли граф | отметка | тест или задача |
|---|---|---|---|---|---|---|
| I01 | столб | голод | столб записан в брошенную аренду, нужда perch | аренда + нужда | ОК | `LeaseScenarioTests.pillarAndScaffold_CancelDeathRestart_LeaveCellsInOrphanedLease` |
| I02 | столб | угроза | дух отбивается или отходит, столб в брошенной аренде | аренда + perch не запирает жизнь | ОК | `InterruptExitTests.perchNeed_Threat_DefendAndRetreatStayAllowed` |
| I03 | столб | ночь | сон не выдаётся, спасение со спуском доступно | аренда + perch + rescue | ОК | `InterruptExitTests.perchNeed_Night_SleepRefusedButRescueAllowed` |
| I04 | столб | задача выше | тикет уходит другому, столб остаётся брошенной арендой | аренда + perch | ОК | `LeaseScenarioTests.pillarAndScaffold_CancelDeathRestart_LeaveCellsInOrphanedLease` |
| I05 | столб | смерть | смерть бросает аренды духа (`orphanSpirit`) | аренда в SavedData | ОК | `LeaseScenarioTests.pillarAndScaffold_CancelDeathRestart_LeaveCellsInOrphanedLease` |
| I06 | столб | рестарт | все ACTIVE бросаются (`orphanAllActive`) | аренда в SavedData | ОК | `LeaseScenarioTests.pillarAndScaffold_CancelDeathRestart_LeaveCellsInOrphanedLease` |
| I07 | мост | голод | недостроенный настил в брошенной аренде | аренда + скан | ОК | `LeaseScenarioTests.bridge_CancelAndSuccess_DifferByStatusAndCleanup` |
| I08 | мост | угроза | настил стоит, аренда брошена | аренда + скан | ОК | `LeaseScenarioTests.bridge_CancelAndSuccess_DifferByStatusAndCleanup` |
| I09 | мост | ночь | настил и дух на нём, аренда брошена | аренда + скан | ОК | `LeaseScenarioTests.bridge_CancelAndSuccess_DifferByStatusAndCleanup` |
| I10 | мост | задача выше | настил остаётся, тикет уходит | аренда + скан | ОК | `LeaseScenarioTests.bridge_CancelAndSuccess_DifferByStatusAndCleanup` |
| I11 | мост | смерть | настил без владельца, аренда брошена событием смерти | аренда в SavedData | ОК | `LeaseScenarioTests.twoSpirits_Hunger_LeavesBothLeasesOrphaned` |
| I12 | мост | рестарт | все ACTIVE бросаются, настил виден скану | аренда в SavedData | ОК | `LeaseScenarioTests.twoSpirits_Hunger_LeavesBothLeasesOrphaned` |
| I13 | расчистка | голод | подмости брошены, брони сняты | аренда + perch + скан | ОК | `LeaseScenarioTests.pillarAndScaffold_CancelDeathRestart_LeaveCellsInOrphanedLease` |
| I14 | расчистка | угроза | retreat/defend, подмости в аренде | аренда + perch | ОК | `InterruptExitTests.clearArea_Interrupted_ReleasesReservationsAndKeepsScaffoldMarked` |
| I15 | расчистка | ночь | сон ждёт спуска, подмости в аренде | аренда + perch + rescue | ОК | `InterruptExitTests.perchNeed_Night_SleepRefusedButRescueAllowed` |
| I16 | расчистка | задача выше | брони отпускаются, подъём отменяет аренду | брони + аренда | ОК | `InterruptExitTests.clearArea_Interrupted_ReleasesReservationsAndKeepsScaffoldMarked` |
| I17 | расчистка | смерть | подмости без владельца — брошенная аренда | аренда в SavedData | ОК | `LeaseScenarioTests.pillarAndScaffold_CancelDeathRestart_LeaveCellsInOrphanedLease` |
| I18 | расчистка | рестарт | подъём не восстановлен, подмости в брошенной аренде | аренда в SavedData | ОК | `LeaseScenarioTests.pillarAndScaffold_CancelDeathRestart_LeaveCellsInOrphanedLease` |
| I19 | шахта | голод | недокопанный проход к руде | блоки видны аудиту, «брошен» — нет | Н | задача 4: факт mine_tunnel_open |
| I20 | шахта | угроза | проход остаётся, дух отходит | то же | Н | задача 4 |
| I21 | шахта | ночь | проход остаётся на ночь | то же | Н | задача 4 |
| I22 | шахта | задача выше | проход брошен на середине | то же | Н | задача 4 |
| I23 | шахта | смерть | проход без владельца | то же | Н | задача 4 |
| I24 | шахта | рестарт | шаги туннеля не в снимке | то же | Н | задача 4 |
| I25 | dig_out | голод | spoil возвращён, пол — PLACED в брошенной аренде | аренда + яма/rescue | ОК | `LeaseScenarioTests.digOut_SuccessClosesLease_InterruptOrphansEverything` |
| I26 | dig_out | угроза | spoil возвращён, клетки тела не трогаются, пол в аренде | аренда + rescue | ОК | `LeaseScenarioTests.digOut_SuccessClosesLease_InterruptOrphansEverything` |
| I27 | dig_out | ночь | аренда брошена, spoil вернёт откат | аренда + rescue | ОК | `LeaseScenarioTests.digOut_SuccessClosesLease_InterruptOrphansEverything` |
| I28 | dig_out | задача выше | аренда брошена, rescue остаётся | аренда + rescue | ОК | `LeaseScenarioTests.digOut_SuccessClosesLease_InterruptOrphansEverything` |
| I29 | dig_out | смерть | смерть бросает аренды духа, spoil и пол видны скану | аренда в SavedData | ОК | `LeaseScenarioTests.digOut_SuccessClosesLease_InterruptOrphansEverything` |
| I30 | dig_out | рестарт | все ACTIVE бросаются, BROKEN помнит before | аренда в SavedData | ОК | `LeaseScenarioTests.digOut_SuccessClosesLease_InterruptOrphansEverything` |
| I31 | маршрут | голод | проломы и поставленные клетки в брошенной аренде | аренда + скан | ОК | `LeaseScenarioTests.route_PassCommits_BreakAndCancelOrphans` |
| I32 | маршрут | угроза | ломка прекращена, клетки в аренде | аренда + скан | ОК | `LeaseScenarioTests.route_PassCommits_BreakAndCancelOrphans` |
| I33 | маршрут | ночь | маршрут брошен, клетки видны скану | аренда + скан | ОК | `LeaseScenarioTests.route_PassCommits_BreakAndCancelOrphans` |
| I34 | маршрут | задача выше | тикет уходит, аренда брошена | аренда + тикет | ОК | `LeaseScenarioTests.route_PassCommits_BreakAndCancelOrphans` |
| I35 | маршрут | смерть | проломы BROKEN без владельца | аренда в SavedData | ОК | `LeaseScenarioTests.route_PassCommits_BreakAndCancelOrphans` |
| I36 | маршрут | рестарт | все ACTIVE бросаются, BROKEN помнит before | аренда в SavedData | ОК | `LeaseScenarioTests.route_PassCommits_BreakAndCancelOrphans` |

## Крупный сценарий

`LeaseScenarioTests.twoSpirits_Hunger_LeavesBothLeasesOrphaned`: два духа, один строит столб, другой мост,
оба на паузе дольше `leaseSuspendTimeoutTicks` — обе аренды брошены, скан видит все клетки сверху вниз,
действующих (`ACTIVE`) аренд не осталось.
