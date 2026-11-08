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

| | M01 | M02 | M03 | M04 | M05 | M06 | M07 | M08 | M09 | M10 | M11 | M12 | M13 | M14 | M15 | M16 | M17 | M18 | M19 | M20 | M21 | M22 | M23 | M24 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| M01 | — | Е | Н | Н | Ч | Е | Е | Н | Н | Н | Н | Ч | Н | Н | Н | Н | Ч | Н | Н | Н | Н | Н | Н | Н |
| M02 | Е | — | Н | Н | Е | Е | Н | Е | Ч | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Е | Н | Н | Н | Е |
| M03 | Н | Н | — | Н | Н | Е | Н | Е | Е | Н | Н | Е | Е | Н | Е | Н | Н | Н | Н | Е | Н | Н | Н | Н |
| M04 | Н | Н | Н | — | Н | Е | Н | Н | Е | Н | Н | Н | Н | Е | Н | Е | Н | Е | Н | Е | Н | Ч | Н | Н |
| M05 | Ч | Е | Н | Н | — | Е | Е | Н | Е | Н | Е | Н | Е | Н | Е | Е | Н | Е | Н | Н | Е | Н | Е | Е |
| M06 | Е | Е | Е | Е | Е | — | Е | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Е | Е | Н | Н | Н |
| M07 | Е | Н | Н | Н | Е | Е | — | Е | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Е | Н | Н | Е | Н |
| M08 | Н | Е | Е | Н | Н | Н | Е | — | Е | Н | Ч | Н | Н | Е | Н | Н | Н | Н | Е | Н | Н | Н | Е | Н |
| M09 | Н | Ч | Е | Е | Е | Н | Н | Е | — | Н | Е | Е | Н | Н | Н | Е | Н | Е | Е | Е | Н | Н | Н | Н |
| M10 | Н | Н | Н | Н | Н | Н | Н | Н | Н | — | Е | Н | Н | Е | Н | Н | Н | Н | Н | Е | Н | Н | Н | Н |
| M11 | Н | Н | Н | Н | Е | Н | Н | Ч | Е | Е | — | Н | Н | Е | Н | Н | Н | Н | Н | Е | Н | Н | Н | Н |
| M12 | Ч | Н | Е | Н | Н | Н | Н | Н | Е | Н | Н | — | Ч | Е | Е | Е | Е | Н | Н | Е | Н | Н | Н | Н |
| M13 | Н | Н | Е | Н | Е | Н | Н | Н | Н | Н | Н | Ч | — | Н | Н | Н | Н | Н | Н | Е | Н | Н | Н | Н |
| M14 | Н | Н | Н | Е | Н | Н | Н | Е | Н | Е | Е | Е | Н | — | Н | Н | Н | Н | Н | Е | Н | Н | Н | Н |
| M15 | Н | Н | Е | Н | Е | Н | Н | Н | Н | Н | Н | Е | Н | Н | — | Н | Н | Н | Н | Е | Н | Н | Н | Н |
| M16 | Н | Н | Н | Е | Е | Н | Н | Н | Е | Н | Н | Е | Н | Н | Н | — | Е | Н | Н | Е | Е | Н | Н | Е |
| M17 | Ч | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Е | Н | Н | Н | Е | — | Н | Н | Е | Н | Н | Н | Н |
| M18 | Н | Н | Н | Е | Е | Н | Н | Н | Е | Н | Н | Н | Н | Н | Н | Н | Н | — | Н | Е | Н | Н | Н | Н |
| M19 | Н | Н | Н | Н | Н | Н | Н | Е | Е | Н | Н | Н | Н | Н | Н | Н | Н | Н | — | Е | Н | Е | Н | Н |
| M20 | Н | Е | Е | Е | Н | Е | Е | Н | Е | Е | Е | Е | Е | Е | Е | Е | Е | Е | Е | — | Е | Е | Е | Е |
| M21 | Н | Н | Н | Н | Е | Е | Н | Н | Н | Н | Н | Н | Н | Н | Н | Е | Н | Н | Н | Е | — | Н | Е | Е |
| M22 | Н | Н | Н | Ч | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Е | Е | Н | — | Е | Н |
| M23 | Н | Н | Н | Н | Е | Н | Е | Е | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Е | Е | Е | — | Н |
| M24 | Н | Е | Н | Н | Е | Н | Н | Н | Н | Н | Н | Н | Н | Н | Н | Е | Н | Н | Н | Е | Е | Н | Н | — |

## Пары

| пара | тест | граф дал | механика сделала | отметка |
|---|---|---|---|---|
| 1-2 | `CrossLifeWorkScenarioTests.pit_InPlanCell_EscapeAndRescuePostedBesideRepairAndBackfill` | дух в клетке получает `dig_out` | выход — действие `dig_out`, без телепорта | ОК |
| 1-6 | `FillScenarioTests.fill_TwoPitsTwoSpirits_LayersSupportFluidAndAuditTogether` | две ямы — две задачи засыпки | два духа кладут слои и держат опору | ОК |
| 1-7 | `DemolishPlanTests.plan_DemolishUnreachableTop_ClearsBottomUpWithoutGoto` | задание сноса без прямого подхода сверху | разбор идёт снизу вверх, без телепорта | ОК |
| 2-5 | `CrossLifeWorkScenarioTests.pit_InPlanCell_EscapeAndRescuePostedBesideRepairAndBackfill` | rescue 98, repair hut 78, backfill выполним | спасатель `clear_area`, ремонт `build_schematic` | ОК |
| 2-6 | `CrossLifeWorkScenarioTests.pit_InPlanCell_EscapeAndRescuePostedBesideRepairAndBackfill` | backfill выполним при застрявшем духе | засыпка `fill` не ждёт спасения | ОК |
| 2-8 | `CrossRouteStageScenarioTests.pit_WaterAtBottom_BridgeWaitsForTraceAndThenUsesPlanks` | bridge требует trail_clean, затем 8 досок | мост тратит доски, дом ждёт | ОК |
| 2-20 | `CrossLifeWorkScenarioTests.pit_InPlanCell_EscapeAndRescuePostedBesideRepairAndBackfill` | спасение выше ремонта хижины | граф ставит rescue первым | ОК |
| 2-24 | `BoardRestartStateLiveTests.restore_StuckMarkAndCasePause_SurviveServerRestart` | метка застревания и пауза кейса на доске | после рестарта дух не получает работу | ОК |
| 3-6 | `GraphTreeCasesTests.terrain_HangingAfterChop_ClearFloatingPosted` | после рубки `event:clear_floating` | уборка висящей кроны | ОК |
| 3-8 | `StoreGraphTests.plan_GroupMinimumShortage_PostsChop` | минимум `#logs` 80 → `chop_oak_log` | рубка валит дерево, склад ждёт | ОК |
| 3-9 | `ChopToolGraphTests.case68_LogsToPlanksTableAxe_ThenChopWithAxe` | доски → верстак → деревянный топор → chop | крафт идёт до рубки, рубит топором | ОК |
| 3-12 | `CrossLifeWorkScenarioTests.chop_ThreatAtChopper_DefendAtCeilingAndChopStaysPosted` | defend 100 выше рубки, defend первым билетом | защита `defend`, затем `chop_tree` | ОК |
| 3-13 | `CrossFleeScenarioTests.flee_WhileChopping_GraphKeepsChopAndRetreatWaits` | retreat есть, chop остаётся ASSIGNED | рубящий дух не берёт бегство | НЕ ОК |
| 3-15 | `GraphNightDefenseTests.plan_NightAndFarTree_DoesNotGiveDistantGather` | ночью нет далёкой добычи | NightLeash не пускает духа за радиус | ОК |
| 3-20 | `ChopToolGraphTests.case70_AxeBroke_TicketStaysWithSpiritAndSameTree` | тот же билет рубки остаётся за духом | поломка топора не отдаёт дерево другому | ОК |
| 4-6 | `CleanupCasesTests.cleanup_FillDirtInStock_DoesNotDigAgain` | засыпка берёт землю со склада | новая добыча земли не ставится | ОК |
| 4-9 | `GraphPlannerTests.plan_NoIronPickaxe_LowersMineCoal` | железная кирка поднимает крафт и топит шахту | шахта ждёт инструмент, а не копает руками | ОК |
| 4-14 | `CrossLifeWorkScenarioTests.hunger_NoFoodInStock_GraphSendsHungrySpiritToMine` | еды нет: eat невыполним, mine_coal 54 первый | голодный дух получает шахту | НЕ ОК |
| 4-16 | `CrossSettlementScenarioTests.repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool` | tool_worn без наковальни невыполним | изношенная кирка продолжает шахту | ОК |
| 4-18 | `CrossSettlementScenarioTests.mining_DarkMine_CoalThenTorchThenLightInGraphOrder` | тьма шахты → mine_coal 82 первым | уголь добывается шахтой | ОК |
| 4-20 | `GraphPlannerTests.plan_NoCoal_PostsMineAndCharcoal` | `mine_coal` и `smelt_charcoal` | уголь добывается, затем жжётся | ОК |
| 5-6 | `CrossSettlementScenarioTests.fill_PitOnHouseLot_FillAndBuildStayPostedTogether` | backfill и building:hut выполнимы вместе | fill кладёт землю, build_schematic строит дом | ОК |
| 5-7 | `BuildPlanCasesTests.plan_StrayBesidePlannedHouse_DemolishesOnlyStray` | лишний забор → `demolish_stray` | снос убирает только чужое, дом не трогает | ОК |
| 5-9 | `StageOneAcceptanceTests.plan_FencePart_CraftsFromOwnWoodInsteadOfWithdraw` | деталь забора крафтится из своего дерева | крафт закрывает нехватку без похода в сундук | ОК |
| 5-11 | `FarmGraphTests.plan_PenAbsentAtStageTwo_GraphDemandsPen` | на этапе 2 нет загона → стройка загона | животноводство ждёт постройку | ОК |
| 5-13 | `CrossLifeWorkScenarioTests.flee_WhileBuilding_GraphPostsRetreatButDispatchKeepsBuilderBusy` | retreat есть в графе, но стройка остаётся ASSIGNED | занятый строитель не берёт бегство | НЕ ОК |
| 5-15 | `CrossLifeWorkScenarioTests.night_FarChopAndNearBuild_NoDistantChoppingButBuildStays` | ночью `building:hut` выполним | стройка на участке продолжается | ОК |
| 5-16 | `GraphRepairPlannerTests.repair_DamagedHut_ConsumesExactlyMissingItems` | ремонт заказывает ровно недостающее | ремонт строит схему из недостающих блоков | ОК |
| 5-18 | `RepairPlanTests.target_LightingPlan_NoRepairScheme` | свет в плане этапа | ремонт света идёт без схемы постройки | ОК |
| 5-21 | `GraphStageGateTests.building_MissingBlocks_NotReady` | аудит видит недостачу блоков | этап не закрыт, стройка не готова | ОК |
| 5-23 | `CrossSupplyScenarioTests.storage_FullWarehouseNotBuilt_GraphBuildsStorageAndPostsSpace` | граф ставит склад и место | склад строится схемой, а не чинтом | ОК |
| 5-24 | `CrossRouteStageScenarioTests.chunks_UnknownLot_NoBlindRebuildButKnownRepairStays` | неизвестный дом не даёт building/repair | стройка не идёт вслепую | ОК |
| 6-7 | `FloatingCleanupTests.decide_EmptyChestWithYard_IsRemoved` | `clear_floating` для пустого сундука | снос сундука идёт уборкой висящего | ОК |
| 6-20 | `LevelRepairScenarioTests.progress_LevelingRepair_CountsCutAndFillCells` | `level_lot` и засыпка ямы на участке | срезка и засыпка считаются послойно | ОК |
| 6-21 | `GraphStageGateTests.lots_CutOrFill_NotReady` | яма/насыпь на участке — этап не готов | выравнивание блокирует закрытие | ОК |
| 7-8 | `CrossSettlementScenarioTests.demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking` | demolish_stray для непустого сундука | withdraw → deposit → clear_area | ОК |
| 7-20 | `CleanupCasesTests.cleanup_StrayFence_PostsDemolition` | лишний забор → `demolish_stray` | кейс сноса разбирает забор | ОК |
| 7-23 | `ChestClearanceTests.decide_NonEmptyFullWarehouseWithMaterials_AddsChestFirst` | задача сноса/выноса ждёт места | склад решает: сначала добавить сундук, потом нести | ОК |
| 8-9 | `StoreGraphTests.plan_ToolMinimumShortage_PostsCraft` | минимум инструмента → крафт | крафт пополняет склад | ОК |
| 8-14 | `CrossLifeWorkScenarioTests.hunger_FoodInChest_EatOutranksMineAndGoesToHungrySpirit` | еда в сундуке: eat 55 выше mine 54 | поесть достаётся голодному | ОК |
| 8-19 | `StoreGraphTests.plan_CarriedItemsWithFreeChest_PostsDeliver` | несомое и свободный сундук → доставка | социальная доставка кладёт предмет | ОК |
| 8-23 | `CrossSupplyScenarioTests.storage_FullWarehouseNotBuilt_GraphBuildsStorageAndPostsSpace` | склад переполнен → building:warehouse и store_space | стройка склада и раскладка `deposit` | ОК |
| 9-11 | `FarmGraphTests.plan_SheepInSheepPen_ShearingIsWork` | овца в загоне → стрижка | инструмент стрижёт овцу | ОК |
| 9-12 | `CrossSupplyScenarioTests.combat_WoundedWithoutArmor_FightsBeforeCraftingArmor` | craft_armor в графе | крафт брони идёт действием `craft` | ОК |
| 9-16 | `CrossSettlementScenarioTests.repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool` | наковальня + слиток → repair_tool выполним | ремонт идёт действием `repair_tool` | ОК |
| 9-18 | `CrossSettlementScenarioTests.mining_DarkMine_CoalThenTorchThenLightInGraphOrder` | уголь → craft_torch выполним | крафт факела идёт после угля | ОК |
| 9-19 | `BrewGraphPlannerTests.ways_StageThreeBrewing_RequireGlassBottlesAndFuel` | варка требует бутылки и топливо | крафт бутылок и топлива идёт до варки | ОК |
| 9-20 | `GraphPlannerTests.plan_NoTorches_RaisesCoalWays` | нет факелов → угольные пути | крафт тянет уголь и палки | ОК |
| 10-11 | `CrossSettlementScenarioTests.husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing` | gather_seeds 97 выше feed 33 и plant 29 | сбор семян идёт до кормления и посева | ОК |
| 10-14 | `CrossSettlementScenarioTests.husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing` | plant_crop невыполним без семян | посев ждёт семена, а не выдумывает урожай | ОК |
| 10-20 | `FarmGraphTests.plan_StringsStocked_WeaveWoolIsWork` | нить есть → `weave_wool` | ферма/крафт делают шерсть | ОК |
| 11-14 | `CrossSettlementScenarioTests.husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing` | feed_animals невыполним без урожая | кормление ждёт еду, голод не растёт молча | ОК |
| 11-20 | `HerdCapPlannerTests.plan_PerSpiritHerdCap_CullRaisedBySpiritCountNotFixedNumber` | потолок стада растёт от числа духов | граф ставит забой лишних | ОК |
| 12-14 | `LifeGraphPlannerTests.plan_ManySpiritsAndMobs_LifeAtCeilingOnlyForAggroTargets` | голод и мобы → приоритеты жизни | еда и защита не выходят за потолок | ОК |
| 12-15 | `CrossLifeWorkScenarioTests.night_ThreatAtWounded_DefendAboveSleepAndGuard` | defend 100 > sleep 84 > guard 55 | ночью бой выше сна и охраны | ОК |
| 12-16 | `CrossSupplyScenarioTests.combat_WoundedWithoutArmor_FightsBeforeCraftingArmor` | defend 100 выше craft_armor 39 | раненый сначала отбивается | ОК |
| 12-17 | `CrossSettlementScenarioTests.combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair` | defend 100 выше ремонта забора 61 | бой раньше забора | ОК |
| 12-20 | `CrossLifeWorkScenarioTests.chop_ThreatAtChopper_DefendAtCeilingAndChopStaysPosted` | защита жизни получает потолок 100 | механика боя выполняется скриптом `defend` | ОК |
| 13-20 | `CrossLifeWorkScenarioTests.flee_WhileBuilding_GraphPostsRetreatButDispatchKeepsBuilderBusy` | retreat на доске, стройка не снята | доска не бросает работу ради бегства | НЕ ОК |
| 14-20 | `CrossLifeWorkScenarioTests.hunger_NoFoodInStock_GraphSendsHungrySpiritToMine` | на доске только шахта, еды нет | голодный дух копает вместо еды | НЕ ОК |
| 15-20 | `GraphNightDefenseTests.plan_NightAndFarTree_DoesNotGiveDistantGather` | ночью далёкая рубка не выдаётся | граф и NightLeash согласованы | ОК |
| 16-17 | `CrossSettlementScenarioTests.combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair` | repair_fence выполним рядом с боем | ремонт ставит схему забора | ОК |
| 16-20 | `GraphRepairPlannerTests.repair_MoreMissing_RaisesWeight` | больше недостачи — выше вес ремонта | ремонт поднимается в очереди графа | ОК |
| 16-21 | `GraphStageRepairSelectionTests.stageTwo_Pens_AreChosenByGraphNotByWatcher` | этап 2 выбирает ремонт загонов графом | ремонт выполняет выбранный узел | ОК |
| 16-24 | `CrossRouteStageScenarioTests.chunks_UnknownLot_NoBlindRebuildButKnownRepairStays` | неизвестное не чинится, известный забор чинится | ремонт идёт только по известному аудиту | ОК |
| 17-20 | `BuildGapFenceTests.need_PartialFence_AsksOnlyRemainingCraft` | нехватка забора считается графом | крафт добирает только остаток | ОК |
| 18-20 | `CrossSettlementScenarioTests.mining_DarkMine_CoalThenTorchThenLightInGraphOrder` | уголь → факел → свет по шагам | порядок графа совпадает с действиями | ОК |
| 19-20 | `BrewGraphPlannerTests.plan_StageThreePotionFullLine_Feasible` | полная линия зелья на этапе 3 выполнима | алхимия варит по цепочке | ОК |
| 19-22 | `CrossSupplyScenarioTests.social_PlayerNearAndExpedition_ExpeditionOutranksGuard` | expedition 69 выше guard 57 | поход `follow` раньше патруля | ОК |
| 20-21 | `GraphStageGateTests.simulate_StageTwoBaseline_ReachesStageThree` | симуляция графа проходит этап 2 | стройка и аудит закрывают этап | ОК |
| 20-22 | `CrossSupplyScenarioTests.social_PlayerNearAndExpedition_ExpeditionOutranksGuard` | поход и охрана — разные узлы графа | механики не смешиваются в одну карточку | ОК |
| 20-23 | `WarehouseGraphNoticeTests.plan_WarehouseMissingAtStageTwo_StillAsksStrategist` | нет склада — граф просит стратега | уведомление уходит в REST, стройка ждёт | ОК |
| 20-24 | `BuildGapUnloadedTests.trace_ChunksNotLoaded_UnknownInsteadOfMissing` | незагруженный чанк — «неизвестно» | недостача не выдумывается, граф ждёт | ОК |
| 21-23 | `WarehouseChainScenarioTests.stageTwoStage_StrategistOrder_MakesWarehouseTask` | заказ стратега на этапе 2 → склад | стройка склада идёт по заказу | ОК |
| 21-24 | `GraphStageGateTests.building_UnknownChunks_NotReady` | неизвестные чанки не закрывают постройку | этап ждёт аудита, а не «наверное, готово» | ОК |
| 22-23 | `WarehouseChainScenarioTests.stageTwoStage_StrategistOrder_MakesWarehouseTask` | заказ стратега и поход/склад | склад строится по узлу, а не по порогу | ОК |
## Итог

Механик — 24, неупорядоченных пар — 24·23/2 = 276. Общий тест есть у 76 пар, частично — 14, к доработке — 186.
В этой задаче добавлены крупные парные сценарии (`CrossLifeWorkScenarioTests`, `CrossSettlementScenarioTests`,
`CrossRouteStageScenarioTests`): 35 новых пар, несколько духов и разное состояние в одной модели мира.

## Техдолг (найдено отметкой «НЕ ОК», не чинится здесь)

- **14-4, 14-20** (`CrossLifeWorkScenarioTests.hunger_NoFoodInStock_GraphSendsHungrySpiritToMine`): голодный дух
  при пустом складе получает `mine_coal`, потому что `event:hungry:eat` невыполним и не ставится на доску.
  Цена: дух копает и голодает, хотя рядом может быть еда/сбор. Предложение: узел «добыть еду» выше шахты.
- **13-3** (`CrossFleeScenarioTests.flee_WhileChopping_GraphKeepsChopAndRetreatWaits`): рубящий дух так же не
  берёт `retreat`; рубка остаётся ASSIGNED. Та же причина, что ниже.
- **13-5, 13-20** (`CrossLifeWorkScenarioTests.flee_WhileBuilding_GraphPostsRetreatButDispatchKeepsBuilderBusy`):
  `retreat` есть в графе, но занятый стройкой дух не берёт его: доска держит одну активную задачу и не
  вытесняет стройку. Цена: дух продолжает строить под мобами. Предложение: вытеснение по приоритету жизни.
- **2-5 (наблюдение)**: узел `pillar` из ямы требует `trail_clean`, поэтому первый прогон графа даёт только
  `rescue`; выход появляется со следующего прогона. Это не «НЕ ОК» (следующий прогон решает), но сценарий
  зависит от порядка прогонов — проверять при пересчёте весов.

## Отметки главного агента (08.11, ревью)

Общая оценка: документ адекватный, честный (покрытие названо конкретными тестами, баги не спрятаны). Замечания:

| что | вердикт | действие |
|---|---|---|
| 14-4, 14-20: голодный дух при пустом складе копает шахту | **править** (высокий) | узел «добыть еду» (сбор/охота/урожай) выше `mine_coal`, пока еды нет; при отсутствии любого источника еды — ждать, а не копать; сценарий уже есть, станет зелёным |
| 13-3, 13-5, 13-20: занятый рубкой/стройкой дух не берёт `retreat` | **править, но после мирного этапа** (владелец: бой в конец очереди) | вытеснение активной задачи по приоритету жизни; тесты уже зафиксированы (поменять ожидание), потом сценарии с мобом и несколькими духами |
| 2-5: `pillar` ждёт `trail_clean`, первый прогон даёт только `rescue` | **не править** | но связать с B2 (observe25, rescue×план): тот же узел, проверять при задаче `obsrescue` |
| матрица: 186 пар «Н» из 276 | **уточнить** | часть пар физически не пересекается (например M17×M24, M19×M18). Пометить `н/п` с коротким обоснованием, чтобы осталось реально нужное (оценка 60–80 пар), и вести «Н» только по ним |
| ячейки «Е» (76) | **частично слабые** | многие «Е» — юнит-тест одной механики, где вторая лишь упомянута (напр. 10-20, 9-19). Для них нужна отметка «слабо» и сценарий в следующей партии; в первую очередь пары из живых багов observe25 (метки noPath × задачи, яма × план, висящее × рубка, аудит × ремонт) |
| сторож `CrossMatrixGuardTests` | **оставить** | проверка ссылок на существующие тесты — то, что нужно |

Следующая партия (задача после `obspath`): 1) разметить `н/п` и «слабо»; 2) 30 пар из списка выше крупными сценариями (несколько духов, мобы, разное состояние); 3) каждую строку «граф дал / механика сделала» вести так же.
