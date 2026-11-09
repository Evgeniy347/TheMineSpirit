# Пересечение механик: матрица пар

Характеризационные сценарии (решение владельца 08.11): фиксируем текущее поведение, даже нелогичное.
В каждом сценарии видно, **что дал граф** (задача, приоритет, событие, порядок билетов) и **что сделала
механика** (скрипт/план/состояние доски). Баг не чинится здесь: он отмечен «НЕ ОК» и вынесен в «Техдолг».

## Реестр механик

| id | механика | код и узлы |
|---|---|---|
| M01 | Ходьба, маршрут, навигация | `mech:movement`: `RoutePlan`, `PitWalkOut`, `NavAStar` |
| M02 | Яма, выход, застревание | `PitEscape`, `PitExitPlanner`, `SelfEscape`, `StuckSituation`; узлы `rescue`, `dig_out`, `bridge`, `pillar` |
| M03 | Рубка леса | `core.forestry`: `ChopPlan`, `TreeDetection`; узлы `chop_*`, `GATHER_MATERIALS` |
| M04 | Добыча, шахта | `core.mining`: `MinePlan`, `DescentPlan`; узлы `mine_*`, `MINE_LEFT_ORE` |
| M05 | Строительство по плану | `core.building`, `StageSites`; узлы `building:*`, схемы |
| M06 | Засыпка и выравнивание | `FILL_CRATER`, `fill`, `event:backfill`, `event:level_lot`, `event:clear_floating` |
| M07 | Разбор и снос | `DISMANTLE_SHELTER`, `ChestPlans`, `DemolitionScope`; узлы `demolish_*` |
| M08 | Сундуки и склад | `core.inventory`: `TransferPlanner`, `ChestClearance`; `STOCK_CHESTS`, `store_*` |
| M09 | Крафт и инструменты | `core.crafting`: `CraftPlan`, `ChainCraft`; события `gear_*`, узлы `craft_*` |
| M10 | Ферма и посадки | `core.farming`: `FarmPlanner`, `SeedPolicy`; узлы `till_soil`, `plant_crop`, `harvest_crop` |
| M11 | Животноводство | `core.husbandry`: `BreedPlanner`, `HerdCap`; узлы `feed_animals`, `breed_*`, `HERD_CULL` |
| M12 | Бой и тактика | `core.tactics`: `DefenseAllocator`, `LifeThreats`; `life_threat`, `ACTIVITY_DEFEND/COMBAT/GUARD` |
| M13 | Бегство и укрытие | `ACTIVITY_RETREAT`, `DisengageRoute`, `CoverFinder` |
| M14 | Еда и голод | `core.hunger`: `SpiritHunger`, `HungerRules`; событие `hungry`, `EAT_TO_FULL`, `RESTOCK_FOOD` |
| M15 | Сон и ночь | `core.sleep`: `SleepRule`, `BedChoice`; `night`, `ACTIVITY_REST`, `NightLeash` |
| M16 | Броня и ремонт | `core.armor`, `core.repair`; `tool_worn`, `armor_worn`, `gear_equip_best`, `repair_*` |
| M17 | Двери, забор, ворота | `core.doors`, `Fence*`, `GateCrossing`; `BUILD_FENCE`, `REPAIR_FENCE` |
| M18 | Свет | `LIGHTING`, `CRAFT_TORCHES`, `event:*_dark`, `repair_lighting` |
| M19 | Социальное, опыт, алхимия | `core.social`, `core.experience`, `core.alchemy`; `more4_*`, `BREW_*` |
| M20 | Граф задач | `GraphPlanner`, `GraphPriorities`, `GraphFailureMemory`; `BoardReprioritize` |
| M21 | Этапы и аудит | `GraphStageGate`, `SettlementStage`, `BoardLedger`, `AuditDiff` |
| M22 | Поход и сопровождение | `ACTIVITY_EXPEDITION`, `ExpeditionDispatch`, `Escort`; `expedition_ready` |
| M23 | Стратег и REST | `GraphPlan.Notice`, `WarehouseChain`, REST `board/tasks` |
| M24 | Чанки и сохранение | `ChunkLoadingRadius`, `BuildGap`, `BoardState`, сохранение меток `StuckSpirits` |

## Матрица пар

Строки и столбцы — M01…M24. `Е` — есть общий тест (строка в таблице «Пары»), `Ч` — механики проверены по
отдельности, но не вместе, `Н` — нужен сценарий. Клетка `—` — пара с самой собой.
`н/п` — пара физически не пересекается (обоснование — `docs/testing/CROSS-MATRIX-NO-PAIR.md`).

| | M01 | M02 | M03 | M04 | M05 | M06 | M07 | M08 | M09 | M10 | M11 | M12 | M13 | M14 | M15 | M16 | M17 | M18 | M19 | M20 | M21 | M22 | M23 | M24 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| M01 | — | Е | Е | Е | Е | Е | Е | Е | Е | Е | Е | Ч | Е | Е | Е | Е | Е | Е | Е | Е | н/п | Е | Н | Н |
| M02 | Е | — | н/п | н/п | Е | Е | н/п | Е | Ч | н/п | н/п | Е | Е | н/п | н/п | н/п | н/п | н/п | н/п | Е | Н | Е | н/п | Е |
| M03 | Е | н/п | — | Н | Н | Е | н/п | Е | Е | н/п | н/п | Е | Е | Н | Е | Н | Н | н/п | н/п | Е | Н | н/п | н/п | Н |
| M04 | Е | н/п | Н | — | Н | Е | н/п | Н | Е | н/п | н/п | Е | н/п | Е | н/п | Е | н/п | Е | н/п | Е | Н | Ч | н/п | Н |
| M05 | Е | Е | Н | Н | — | Е | Е | Н | Е | Н | Е | Е | Е | Е | Е | Е | Е | Е | Н | Н | Е | Н | Е | Е |
| M06 | Е | Е | Е | Е | Е | — | Е | Н | н/п | Н | н/п | н/п | н/п | н/п | н/п | н/п | н/п | н/п | н/п | Е | Е | н/п | н/п | Е |
| M07 | Е | н/п | н/п | н/п | Е | Е | — | Е | н/п | н/п | н/п | н/п | н/п | н/п | н/п | н/п | Е | н/п | н/п | Е | Е | н/п | Е | Н |
| M08 | Е | Е | Е | Н | Н | Н | Е | — | Е | Н | Ч | Е | н/п | Е | н/п | Е | н/п | н/п | Е | Е | Н | н/п | Е | Н |
| M09 | Е | Ч | Е | Е | Е | н/п | н/п | Е | — | Н | Е | Е | Н | Е | Е | Е | Н | Е | Е | Е | Н | н/п | н/п | Н |
| M10 | Е | н/п | н/п | н/п | Н | Н | н/п | Н | Н | — | Е | н/п | н/п | Е | н/п | н/п | Н | н/п | н/п | Е | Н | н/п | н/п | Е |
| M11 | Е | н/п | н/п | н/п | Е | н/п | н/п | Ч | Е | Е | — | Е | н/п | Е | н/п | Е | Е | н/п | н/п | Е | Н | н/п | н/п | Н |
| M12 | Ч | Е | Е | Е | Е | н/п | н/п | Е | Е | н/п | Е | — | Ч | Е | Е | Е | Е | н/п | н/п | Е | н/п | Е | Е | Н |
| M13 | Е | Е | Е | н/п | Е | н/п | н/п | н/п | Н | н/п | н/п | Ч | — | н/п | Е | н/п | Е | н/п | н/п | Е | н/п | Е | н/п | н/п |
| M14 | Е | н/п | Н | Е | Е | н/п | н/п | Е | Е | Е | Е | Е | н/п | — | н/п | н/п | н/п | н/п | н/п | Е | н/п | Е | н/п | н/п |
| M15 | Е | н/п | Е | н/п | Е | н/п | н/п | н/п | Е | н/п | н/п | Е | Е | н/п | — | н/п | н/п | н/п | н/п | Е | н/п | Н | н/п | Н |
| M16 | Е | н/п | Н | Е | Е | н/п | н/п | Е | Е | н/п | Е | Е | н/п | н/п | н/п | — | Е | н/п | н/п | Е | Е | Н | н/п | Е |
| M17 | Е | н/п | Н | н/п | Е | н/п | Е | н/п | Н | Н | Е | Е | Е | н/п | н/п | Е | — | н/п | н/п | Е | Н | Е | Е | н/п |
| M18 | Е | н/п | н/п | Е | Е | н/п | н/п | н/п | Е | н/п | н/п | н/п | н/п | н/п | н/п | н/п | н/п | — | н/п | Е | н/п | н/п | н/п | Н |
| M19 | Е | н/п | н/п | н/п | Н | н/п | н/п | Е | Е | н/п | н/п | н/п | н/п | н/п | н/п | н/п | н/п | н/п | — | Е | н/п | Е | н/п | н/п |
| M20 | Е | Е | Е | Е | Н | Е | Е | Е | Е | Е | Е | Е | Е | Е | Е | Е | Е | Е | Е | — | Е | Е | Е | Е |
| M21 | н/п | Н | Н | Н | Е | Е | Е | Н | Н | Н | Н | н/п | н/п | н/п | н/п | Е | Н | н/п | н/п | Е | — | Н | Е | Е |
| M22 | Е | Е | н/п | Ч | Н | н/п | н/п | н/п | н/п | н/п | н/п | Е | Е | Е | Н | Н | Е | н/п | Е | Е | Н | — | Е | Н |
| M23 | Н | н/п | н/п | н/п | Е | н/п | Е | Е | н/п | н/п | н/п | Е | н/п | н/п | н/п | н/п | Е | н/п | н/п | Е | Е | Е | — | Н |
| M24 | Н | Е | Н | Н | Е | Е | Н | Н | Н | Е | Н | Н | н/п | н/п | Н | Е | н/п | Н | н/п | Е | Е | Н | Н | — |

## Пары

| пара | тест | граф дал | механика сделала | отметка | сила |
|---|---|---|---|---|---|
| 1-2 | `CrossLifeWorkScenarioTests.pit_InPlanCell_EscapeAndRescuePostedBesideRepairAndBackfill` | дух в клетке получает `dig_out` | выход — действие `dig_out`, без телепорта | ОК | полный |
| 1-3 | `CrossPathScenarioTests.path_NoPathMark_RefusesEveryGraphTaskButRescue` | граф ставит `chop_jungle_log` | духу без пути отказ «пути нет», рубка ждёт выхода | ОК | полный |
| 1-4 | `CrossPathScenarioTests.path_NoPathMark_RefusesEveryGraphTaskButRescue` | граф ставит `mine_coal` | духу без пути отказ, шахта ждёт | ОК | полный |
| 1-6 | `FillScenarioTests.fill_TwoPitsTwoSpirits_LayersSupportFluidAndAuditTogether` | две ямы — две задачи засыпки | два духа кладут слои и держат опору | ОК | слабо |
| 1-7 | `DemolishPlanTests.plan_DemolishUnreachableTop_ClearsBottomUpWithoutGoto` | задание сноса без прямого подхода сверху | разбор идёт снизу вверх, без телепорта | ОК | слабо |
| 1-8 | `CrossPathScenarioTests.path_NoPathMark_RefusesEveryGraphTaskButRescue` | граф ставит вынос материалов | склад не берёт духа без пути | ОК | полный |
| 1-9 | `CrossPathScenarioTests.path_NoPathMark_RefusesEveryGraphTaskButRescue` | граф ставит крафт кирки | дух без пути не берёт крафт | ОК | полный |
| 1-10 | `CrossPathScenarioTests.path_NoPathMark_RefusesEveryGraphTaskButRescue` | граф ставит `gather_seeds` | ферма ждёт, дух без пути не берёт | ОК | полный |
| 1-13 | `CrossPathScenarioTests.path_NoPathMark_RefusesEveryGraphTaskButRescue` | граф ставит `retreat` | застрявший не берёт бегство, пока не вышел | ОК | полный |
| 1-14 | `CrossPathScenarioTests.path_NoPathMark_RefusesEveryGraphTaskButRescue` | граф ставит еду под угрозой | голодный без пути не берёт обед | ОК | полный |
| 1-15 | `CrossPathScenarioTests.path_NoPathMark_RefusesEveryGraphTaskButRescue` | граф ставит `sleep` | сон не достаётся духу без пути | ОК | полный |
| 1-16 | `CrossPathScenarioTests.path_NoPathMark_RefusesEveryGraphTaskButRescue` | граф ставит `repair_tool` | ремонт ждёт, дух без пути не берёт | ОК | полный |
| 1-18 | `CrossPathScenarioTests.path_NoPathMark_RefusesEveryGraphTaskButRescue` | граф ставит свет шахты | свет не ставится духом без пути | ОК | полный |
| 1-19 | `CrossPathScenarioTests.path_NoPathMark_RefusesEveryGraphTaskButRescue` | граф ставит `brew_regeneration` | варка ждёт, дух без пути не берёт | ОК | полный |
| 1-20 | `CrossPathScenarioTests.path_NoPathMark_RefusesEveryGraphTaskButRescue` | граф ставит 12 работ разных механик | метка noPath отказывает всем, кроме спасения | ОК | полный |
| 1-22 | `CrossPathScenarioTests.path_NoPathMark_RefusesEveryGraphTaskButRescue` | граф ставит поход | поход не берёт духа без пути | ОК | полный |
| 2-5 | `CrossLifeWorkScenarioTests.pit_InPlanCell_EscapeAndRescuePostedBesideRepairAndBackfill` | rescue 98, repair hut 78, backfill выполним | спасатель `clear_area`, ремонт `build_schematic` | ОК | полный |
| 2-6 | `CrossLifeWorkScenarioTests.pit_InPlanCell_EscapeAndRescuePostedBesideRepairAndBackfill` | backfill выполним при застрявшем духе | засыпка `fill` не ждёт спасения | ОК | полный |
| 2-8 | `CrossRouteStageScenarioTests.pit_WaterAtBottom_BridgeWaitsForTraceAndThenUsesPlanks` | bridge требует trail_clean, затем 8 досок | мост тратит доски, дом ждёт | ОК | полный |
| 2-12 | `CrossBattleWorkScenarioTests.combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend` | defend 100 выше `rescue_spirit` | сначала бой, спасение из ямы ждёт | ОК | полный |
| 2-13 | `CrossBattleWorkScenarioTests.combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend` | `retreat` и `rescue_spirit` рядом | механика ямы выбирает PIT, без ступеней NONE | ОК | полный |
| 2-20 | `CrossLifeWorkScenarioTests.pit_InPlanCell_EscapeAndRescuePostedBesideRepairAndBackfill` | спасение выше ремонта хижины | граф ставит rescue первым | ОК | полный |
| 2-22 | `CrossBattleWorkScenarioTests.rescue_TrappedSpiritAndExpedition_RescueOutranksFollow` | `rescue_spirit` выше похода | помощник разбирает клетку `clear_area` | ОК | полный |
| 2-24 | `BoardRestartStateLiveTests.restore_StuckMarkAndCasePause_SurviveServerRestart` | метка застревания и пауза кейса на доске | после рестарта дух не получает работу | ОК | полный |
| 3-6 | `CrossRouteStageScenarioTests.chop_HangingCanopyAfterChop_ClearFloatingAndHouseStayPosted` | после рубки висят крона и яма | `clear_area` убирает крону, `fill` — яму | ОК | полный |
| 3-8 | `StoreGraphTests.plan_GroupMinimumShortage_PostsChop` | минимум `#logs` 80 → `chop_oak_log` | рубка валит дерево, склад ждёт | ОК | слабо |
| 3-9 | `ChopToolGraphTests.case68_LogsToPlanksTableAxe_ThenChopWithAxe` | доски → верстак → деревянный топор → chop | крафт идёт до рубки, рубит топором | ОК | слабо |
| 3-12 | `CrossLifeWorkScenarioTests.chop_ThreatAtChopper_DefendAtCeilingAndChopStaysPosted` | defend 100 выше рубки, defend первым билетом | защита `defend`, затем `chop_tree` | ОК | полный |
| 3-13 | `CrossFleeScenarioTests.flee_WhileChopping_RetreatPreemptsChopAndChopReturnsToBoard` | retreat 100 вытесняет рубку, рубка возвращается PENDING | рубящий дух уходит от мобов, дерево берёт другой | ОК | полный |
| 3-15 | `GraphNightDefenseTests.plan_NightAndFarTree_DoesNotGiveDistantGather` | ночью нет далёкой добычи | NightLeash не пускает духа за радиус | ОК | слабо |
| 3-20 | `ChopToolGraphTests.case70_AxeBroke_TicketStaysWithSpiritAndSameTree` | тот же билет рубки остаётся за духом | поломка топора не отдаёт дерево другому | ОК | слабо |
| 4-6 | `CleanupCasesTests.cleanup_FillDirtInStock_DoesNotDigAgain` | засыпка берёт землю со склада | новая добыча земли не ставится | ОК | слабо |
| 4-9 | `GraphPlannerTests.plan_NoIronPickaxe_LowersMineCoal` | железная кирка поднимает крафт и топит шахту | шахта ждёт инструмент, а не копает руками | ОК | слабо |
| 4-14 | `CrossHungerScenarioTests.hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine` | еды нет: `event:hungry:hunt` 60 выше `mine_coal` 54, без источника `event:hungry:wait_food` 56 выше шахты | голодный дух охотится или ждёт, сытый получает шахту | ОК | полный |
| 4-16 | `CrossSettlementScenarioTests.repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool` | tool_worn без наковальни невыполним | изношенная кирка продолжает шахту | ОК | полный |
| 4-18 | `CrossSettlementScenarioTests.mining_DarkMine_CoalThenTorchThenLightInGraphOrder` | тьма шахты → mine_coal 82 первым | уголь добывается шахтой | ОК | полный |
| 4-20 | `GraphPlannerTests.plan_NoCoal_PostsMineAndCharcoal` | `mine_coal` и `smelt_charcoal` | уголь добывается, затем жжётся | ОК | слабо |
| 5-6 | `CrossSettlementScenarioTests.fill_PitOnHouseLot_FillAndBuildStayPostedTogether` | backfill и building:hut выполнимы вместе | fill кладёт землю, build_schematic строит дом | ОК | полный |
| 5-7 | `BuildPlanCasesTests.plan_StrayBesidePlannedHouse_DemolishesOnlyStray` | лишний забор → `demolish_stray` | снос убирает только чужое, дом не трогает | ОК | слабо |
| 5-9 | `StageOneAcceptanceTests.plan_FencePart_CraftsFromOwnWoodInsteadOfWithdraw` | деталь забора крафтится из своего дерева | крафт закрывает нехватку без похода в сундук | ОК | полный |
| 5-11 | `FarmGraphTests.plan_PenAbsentAtStageTwo_GraphDemandsPen` | на этапе 2 нет загона → стройка загона | животноводство ждёт постройку | ОК | слабо |
| 5-12 | `CrossBattleWorkScenarioTests.combat_MobsAtBuilderAndChopper_RetreatPreemptsWork` | defend 100, retreat 96 вытесняет стройку | строитель бросает хижину, рубка второго ждёт | ОК | полный |
| 5-13 | `CrossLifeWorkScenarioTests.flee_WhileBuilding_RetreatPreemptsBuildAndBuildReturnsToBoard` | retreat 100 вытесняет стройку, стройка возвращается PENDING | строитель уходит от мобов, хижину достраивает другой | ОК | полный |
| 5-15 | `CrossLifeWorkScenarioTests.night_FarChopAndNearBuild_NoDistantChoppingButBuildStays` | ночью `building:hut` выполним | стройка на участке продолжается | ОК | полный |
| 5-16 | `GraphRepairPlannerTests.repair_DamagedHut_ConsumesExactlyMissingItems` | ремонт заказывает ровно недостающее | ремонт строит схему из недостающих блоков | ОК | слабо |
| 5-18 | `RepairPlanTests.target_LightingPlan_NoRepairScheme` | свет в плане этапа | ремонт света идёт без схемы постройки | ОК | слабо |
| 5-21 | `GraphStageGateTests.building_MissingBlocks_NotReady` | аудит видит недостачу блоков | этап не закрыт, стройка не готова | ОК | полный |
| 5-23 | `CrossSupplyScenarioTests.storage_FullWarehouseNotBuilt_GraphBuildsStorageAndPostsSpace` | граф ставит склад и место | склад строится схемой, а не чинтом | ОК | полный |
| 5-24 | `CrossRouteStageScenarioTests.chunks_UnknownLot_NoBlindRebuildButKnownRepairStays` | неизвестный дом не даёт building/repair | стройка не идёт вслепую | ОК | полный |
| 6-7 | `FloatingCleanupTests.decide_EmptyChestWithYard_IsRemoved` | `clear_floating` для пустого сундука | снос сундука идёт уборкой висящего | ОК | слабо |
| 6-20 | `LevelRepairScenarioTests.progress_LevelingRepair_CountsCutAndFillCells` | `level_lot` и засыпка ямы на участке | срезка и засыпка считаются послойно | ОК | слабо |
| 6-21 | `GraphStageGateTests.lots_CutOrFill_NotReady` | яма/насыпь на участке — этап не готов | выравнивание блокирует закрытие | ОК | полный |
| 6-24 | `CrossPathScenarioTests.path_UnknownFieldAndTerrain_GraphWaitsInsteadOfGuessing` | неизвестный рельеф с ямами | граф всё равно ставит засыпку — НЕ ОК | НЕ ОК | полный |
| 7-8 | `CrossSettlementScenarioTests.demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking` | demolish_stray для непустого сундука | withdraw → deposit → clear_area | ОК | полный |
| 7-17 | `CrossFarmSupplyScenarioTests.audit_DamagedHutExtraStray_RepairAndDemolitionBeforeStageClose` | снос лишнего забора и ремонт побитого | снос берёт только чужие постройки | ОК | полный |
| 7-20 | `CleanupCasesTests.cleanup_StrayFence_PostsDemolition` | лишний забор → `demolish_stray` | кейс сноса разбирает забор | ОК | слабо |
| 7-21 | `CrossFarmSupplyScenarioTests.audit_DamagedHutExtraStray_RepairAndDemolitionBeforeStageClose` | лишнее и чужие блоки в аудите | этап не закрыт, снос на доске | ОК | полный |
| 7-23 | `ChestClearanceTests.decide_NonEmptyFullWarehouseWithMaterials_AddsChestFirst` | задача сноса/выноса ждёт места | склад решает: сначала добавить сундук, потом нести | ОК | слабо |
| 8-9 | `StoreGraphTests.plan_ToolMinimumShortage_PostsCraft` | минимум инструмента → крафт | крафт пополняет склад | ОК | слабо |
| 8-12 | `CrossFarmSupplyScenarioTests.supply_FullChestFoodIron_FeedRepairAndSpaceStayPosted` | defend 100 и место на складе | бой выше раскладки, склад не мешает обороне | ОК | полный |
| 8-14 | `CrossHungerScenarioTests.hunger_FoodInChest_EatOutranksMineAndGoesToHungrySpirit` | еда в сундуке: eat 55 выше mine 54 | поесть достаётся голодному | ОК | полный |
| 8-16 | `CrossFarmSupplyScenarioTests.supply_FullChestFoodIron_FeedRepairAndSpaceStayPosted` | ремонт кирки и место на складе | ремонт выше раскладки, инструмент чинится | ОК | полный |
| 8-19 | `StoreGraphTests.plan_CarriedItemsWithFreeChest_PostsDeliver` | несомое и свободный сундук → доставка | социальная доставка кладёт предмет | ОК | слабо |
| 8-20 | `CrossFarmSupplyScenarioTests.supply_FullChestFoodIron_FeedRepairAndSpaceStayPosted` | склад переполнен — `store_space` | раскладка и место на доске | ОК | полный |
| 8-23 | `CrossSupplyScenarioTests.storage_FullWarehouseNotBuilt_GraphBuildsStorageAndPostsSpace` | склад переполнен → building:warehouse и store_space | стройка склада и раскладка `deposit` | ОК | полный |
| 9-11 | `FarmGraphTests.plan_SheepInSheepPen_ShearingIsWork` | овца в загоне → стрижка | инструмент стрижёт овцу | ОК | слабо |
| 9-12 | `CrossSupplyScenarioTests.combat_WoundedWithoutArmor_FightsBeforeCraftingArmor` | craft_armor в графе | крафт брони идёт действием `craft` | ОК | полный |
| 9-14 | `CrossFarmSupplyScenarioTests.hunger_NoFoodButExpeditionReady_ExpeditionGoesWhileSpiritStarves` | `craft_food` без пшеницы невыполним | еда не выдумывается крафтом | ОК | полный |
| 9-15 | `CrossFarmSupplyScenarioTests.night_DangerAndThreat_CraftWaitsBehindRetreat` | ночной крафт кирки 39 | retreat выше крафта, сон под мобом не идёт | ОК | полный |
| 9-16 | `CrossSettlementScenarioTests.repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool` | наковальня + слиток → repair_tool выполним | ремонт идёт действием `repair_tool` | ОК | полный |
| 9-18 | `CrossSettlementScenarioTests.mining_DarkMine_CoalThenTorchThenLightInGraphOrder` | уголь → craft_torch выполним | крафт факела идёт после угля | ОК | полный |
| 9-19 | `BrewGraphPlannerTests.ways_StageThreeBrewing_RequireGlassBottlesAndFuel` | варка требует бутылки и топливо | крафт бутылок и топлива идёт до варки | ОК | слабо |
| 9-20 | `GraphPlannerTests.plan_NoTorches_RaisesCoalWays` | нет факелов → угольные пути | крафт тянет уголь и палки | ОК | слабо |
| 10-11 | `CrossSettlementScenarioTests.husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing` | gather_seeds 97 выше feed 33 и plant 29 | сбор семян идёт до кормления и посева | ОК | полный |
| 10-14 | `CrossSettlementScenarioTests.husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing` | plant_crop невыполним без семян | посев ждёт семена, а не выдумывает урожай | ОК | полный |
| 10-20 | `FarmGraphTests.plan_StringsStocked_WeaveWoolIsWork` | нить есть → `weave_wool` | ферма/крафт делают шерсть | ОК | слабо |
| 10-24 | `CrossPathScenarioTests.path_UnknownFieldAndTerrain_GraphWaitsInsteadOfGuessing` | поле в незагруженном чанке | посев невыполним, граф ждёт аудита | ОК | полный |
| 11-12 | `CrossFarmSupplyScenarioTests.husbandry_ShearingAndWornShears_RepairAndFenceStayPosted` | defend у стада рядом со стрижкой | стрижка остаётся работой, бой — отдельный кейс | ОК | полный |
| 11-14 | `CrossSettlementScenarioTests.husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing` | feed_animals невыполним без урожая | кормление ждёт еду, голод не растёт молча | ОК | полный |
| 11-16 | `CrossFarmSupplyScenarioTests.husbandry_ShearingAndWornShears_RepairAndFenceStayPosted` | ремонт ножниц и стрижка | `repair_tool` чинит, `shear` стрижёт овец | ОК | полный |
| 11-17 | `CrossFarmSupplyScenarioTests.husbandry_ShearingAndWornShears_RepairAndFenceStayPosted` | ремонт забора и стрижка овец | забор строится схемой, стадо не ждёт | ОК | полный |
| 11-20 | `HerdCapPlannerTests.plan_PerSpiritHerdCap_CullRaisedBySpiritCountNotFixedNumber` | потолок стада растёт от числа духов | граф ставит забой лишних | ОК | слабо |
| 12-14 | `LifeGraphPlannerTests.plan_ManySpiritsAndMobs_LifeAtCeilingOnlyForAggroTargets` | голод и мобы → приоритеты жизни | еда и защита не выходят за потолок | ОК | слабо |
| 12-15 | `CrossLifeWorkScenarioTests.night_ThreatAtWounded_DefendAboveSleepAndGuard` | defend 100 > sleep 84 > guard 55 | ночью бой выше сна и охраны | ОК | полный |
| 12-16 | `CrossSupplyScenarioTests.combat_WoundedWithoutArmor_FightsBeforeCraftingArmor` | defend 100 выше craft_armor 39 | раненый сначала отбивается | ОК | полный |
| 12-17 | `CrossSettlementScenarioTests.combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair` | defend 100 выше ремонта забора 61 | бой раньше забора | ОК | полный |
| 12-20 | `CrossLifeWorkScenarioTests.chop_ThreatAtChopper_DefendAtCeilingAndChopStaysPosted` | защита жизни получает потолок 100 | механика боя выполняется скриптом `defend` | ОК | полный |
| 12-22 | `CrossBattleWorkScenarioTests.combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse` | defend 100 выше похода 69 | поход ждёт конца боя | ОК | полный |
| 12-23 | `CrossBattleWorkScenarioTests.combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse` | defend 100, заказ склада стратегу | заказ не отменяет бой | ОК | полный |
| 13-15 | `CrossFleeScenarioTests.flee_NightMobsAndExpedition_RetreatBeatsSleepAndFollow` | retreat выше sleep | механика сна под мобом спать не даёт | ОК | полный |
| 13-17 | `CrossFleeScenarioTests.flee_NightMobsAndExpedition_RetreatBeatsSleepAndFollow` | бегство через ворота | ворота закрываются после перехода | ОК | полный |
| 13-20 | `CrossLifeWorkScenarioTests.flee_WhileBuilding_RetreatPreemptsBuildAndBuildReturnsToBoard` | retreat 100 вытесняет стройку, стройка возвращается PENDING | доска возвращает работу и отдаёт бегство | ОК | полный |
| 13-22 | `CrossFleeScenarioTests.flee_NightMobsAndExpedition_RetreatBeatsSleepAndFollow` | retreat выше expedition | отход важнее похода | ОК | полный |
| 14-20 | `CrossHungerScenarioTests.hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine` | голодный дух: добыча еды (охота/рыбалка) выше шахты, без источника — ожидание выше шахты | голодный дух не копает, сытый копает | ОК | полный |
| 14-22 | `CrossFarmSupplyScenarioTests.hunger_NoFoodButExpeditionReady_ExpeditionGoesWhileSpiritStarves` | поход выполним, еды нет | поход уходит, голодный дух остаётся — НЕ ОК | НЕ ОК | полный |
| 15-20 | `GraphNightDefenseTests.plan_NightAndFarTree_DoesNotGiveDistantGather` | ночью далёкая рубка не выдаётся | граф и NightLeash согласованы | ОК | слабо |
| 16-17 | `CrossSettlementScenarioTests.combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair` | repair_fence выполним рядом с боем | ремонт ставит схему забора | ОК | полный |
| 16-20 | `GraphRepairPlannerTests.repair_MoreMissing_RaisesWeight` | больше недостачи — выше вес ремонта | ремонт поднимается в очереди графа | ОК | слабо |
| 16-21 | `CrossFarmSupplyScenarioTests.audit_DamagedHutExtraStray_RepairAndDemolitionBeforeStageClose` | ремонт хижины 80 % выбран графом | после аудита 100 % этап видит готовую хижину | ОК | полный |
| 16-24 | `CrossRouteStageScenarioTests.chunks_UnknownLot_NoBlindRebuildButKnownRepairStays` | неизвестное не чинится, известный забор чинится | ремонт идёт только по известному аудиту | ОК | полный |
| 17-20 | `BuildGapFenceTests.need_PartialFence_AsksOnlyRemainingCraft` | нехватка забора считается графом | крафт добирает только остаток | ОК | слабо |
| 17-22 | `CrossBattleWorkScenarioTests.combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse` | ремонт забора и поход | ворота открыты, после перехода закрываются | ОК | полный |
| 17-23 | `CrossBattleWorkScenarioTests.combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse` | ремонт забора рядом с заказом стратега | оба задания на доске, забор чинится схемой | ОК | полный |
| 18-20 | `CrossSettlementScenarioTests.mining_DarkMine_CoalThenTorchThenLightInGraphOrder` | уголь → факел → свет по шагам | порядок графа совпадает с действиями | ОК | полный |
| 19-20 | `BrewGraphPlannerTests.plan_StageThreePotionFullLine_Feasible` | полная линия зелья на этапе 3 выполнима | алхимия варит по цепочке | ОК | слабо |
| 19-22 | `CrossSupplyScenarioTests.social_PlayerNearAndExpedition_ExpeditionOutranksGuard` | expedition 69 выше guard 57 | поход `follow` раньше патруля | ОК | полный |
| 20-21 | `GraphStageGateTests.simulate_StageTwoBaseline_ReachesStageThree` | симуляция графа проходит этап 2 | стройка и аудит закрывают этап | ОК | полный |
| 20-22 | `CrossSupplyScenarioTests.social_PlayerNearAndExpedition_ExpeditionOutranksGuard` | поход и охрана — разные узлы графа | механики не смешиваются в одну карточку | ОК | полный |
| 20-23 | `WarehouseGraphNoticeTests.plan_WarehouseMissingAtStageTwo_StillAsksStrategist` | нет склада — граф просит стратега | уведомление уходит в REST, стройка ждёт | ОК | слабо |
| 20-24 | `BuildGapUnloadedTests.trace_ChunksNotLoaded_UnknownInsteadOfMissing` | незагруженный чанк — «неизвестно» | недостача не выдумывается, граф ждёт | ОК | слабо |
| 21-23 | `WarehouseChainScenarioTests.stageTwoStage_StrategistOrder_MakesWarehouseTask` | заказ стратега на этапе 2 → склад | стройка склада идёт по заказу | ОК | полный |
| 21-24 | `GraphStageGateTests.building_UnknownChunks_NotReady` | неизвестные чанки не закрывают постройку | этап ждёт аудита, а не «наверное, готово» | ОК | полный |
| 22-23 | `WarehouseChainScenarioTests.stageTwoStage_StrategistOrder_MakesWarehouseTask` | заказ стратега и поход/склад | склад строится по узлу, а не по порогу | ОК | полный |
| 1-5, 1-11, 1-17 | `CrossMovementPlanTests.movement_WorkCards_MoveToTargetThenAct` | работа ставится графом; маршрут — `goto` | стройка/кормление/забор идут после перехода, без телепорта | ОК | слабо |
| 4-12, 5-14, 5-17 | `CrossSiegeScenarioTests.siege_LifeAboveWork_WorkStaysPosted` | defend 100 выше шахты 48; еда 55 выше стройки 54; забор 61 выше стройки | бой/еда/забор вытесняют работу, задачи остаются | ОК | полный |

## Итог

Пар с общим тестом — 119, частично — 5, физически непересекающихся — 109, к доработке — 43.
Вторая партия добавила 37 сценариев, третья — вытеснение `retreat` и 6 пар; слабые «Е» — в колонке «сила».
Непересекающиеся пары с обоснованием — `docs/testing/CROSS-MATRIX-NO-PAIR.md`; итог, техдолг и ревью —
`docs/testing/CROSS-MATRIX-NOTES.md`.

