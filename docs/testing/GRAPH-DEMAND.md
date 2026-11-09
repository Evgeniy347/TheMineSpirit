# Граф: спрос, нехватка и подъём приоритета источников

Реестр цепочек «нехватка → что граф поднимает → стоп». Статус `покрыто` — тест добавлен этой задачей,
`есть` — поведение уже проверено прежним тестом (не дублируем), `очередь` — следующая партия.
Спрос идёт от общего заказа (план, этап, событие), а не от каждого духа; приоритет работы ≤ 99,
100 остаётся защите жизни. Общий механизм: `GraphLack` (порог по типу), `GraphFlow` (рост к источникам),
`GraphTickets` (одна заявка на предмет, количество не больше цели).

| № | Цепочка | Тест | Статус |
|---|---|---|---|
| 1 | еда: урожай → охота → дойка/яйца | `DemandFoodChainScenarioTests.case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs` | покрыто |
| 2 | мотыга одна на ферму | `GraphAllocationGroupBTests.case14_NoHoe_HoeCraftBeforeField`, `GearFragmentTests.plan_SpiritWithoutHoe_GetsHoeCraft` | есть |
| 3 | голодное стадо ↔ посев корма | `CrossSettlementScenarioTests.husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing` | есть |
| 4 | еда в дальнем сундуке → доставка вместо добычи | `DemandFoodChainScenarioTests.case04_FoodInFarChest_DeliveryRaisedInsteadOfHunting` | покрыто |
| 5 | нет печи → булыжник → печь | `DemandFoodChainScenarioTests.case05_FurnaceMissing_CobblestoneRaisedUntilFurnaceCanBeBuilt` | покрыто |
| 6 | потолок топоров, улучшение и ремонт | `GraphPlannerTests.plan_WoodenHoeAndSword_OnlySwordUpgraded`, `GraphAllocationGroupBTests.case26_WornTool_RepairRaised` | есть |
| 7 | железо → плавка → железный инструмент | `CrossSettlementScenarioTests.repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool`, `GraphPlannerTests.plan_StageTwo_IronPickaxeIsKitNotChance` | есть |
| 8 | палки малым запасом | `GraphPlannerTests.plan_NoStonePickaxe_RaisesCobbleAndSticks` | есть |
| 9 | лес рядом кончился → посадка и дальняя рубка | `GraphTreeCasesTests.plan_ForestDepleted_ReplantsOnlyInOrchard`, `GraphTreeCasesTests.plan_NoOakInWorld_NoChopTask` | есть |
| 10 | песок/стекло для стройки | `DemandWorkChainScenarioTests.case10_SandAndGlassForConstruction_RaisedThroughSandAndFurnace` | покрыто |
| 11 | овец нет → разведение → стрижка | `DemandFoodChainScenarioTests.case11_NoSheep_BreedingRaisedThenShearing` | покрыто |
| 12 | низкое здоровье без еды | `DemandFoodChainScenarioTests.case12_LowHealthWithoutFood_FoodAndHealingRaisedAboveMine` | покрыто |
| 13 | оружие и стрелы | `GraphPlannerTests.plan_HostileNearby_RaisesSword` | есть |
| 14 | свет по периметру при частых рейдах | `DemandSafetyChainScenarioTests.case14_RaidPerimeterDark_LightingAndTorchesRaised` | покрыто |
| 15 | запас блоков спасателей | `DemandSafetyChainScenarioTests.case15_TrappedInWater_RescueBlocksRaisedAndBridgeWaitsForPlanks` | покрыто |
| 16 | щиты и факелы ночных караульных | `GraphPlannerTests.plan_Night_HalfGuardNoBedPush` | есть |
| 17 | сундуки с мусором → разбор, а не новые сундуки | `StoreGraphTests.plan_MixedChest_PostsSort`, `GraphAllocationGroupCTests.case28_MixedChest_SortingRaised` | есть |
| 18 | буфер у стройки/шахты | `DemandFoodChainScenarioTests.case18_SiteNeedsBuffer_PlanksAndSiteChestRaised` | покрыто |
| 19 | вода/канал/ведро для грядки | `DemandFoodChainScenarioTests.case19_FieldDry_WaterBucketRaisedThroughBucketAndIron` | покрыто |
| 20 | набор выхода для шахты (еда, факелы, кирка) | `DemandFoodChainScenarioTests.case20_MiningKit_FoodTorchesPickaxeRaised` | покрыто |
| 21 | снабжение игрока перед походом | `DemandWorkChainScenarioTests.case21_PlayerBeforeTrip_FoodAndToolSupplied` | покрыто |
| 22 | общий механизм спроса и подъёма приоритета | `GraphDemandMechanismTests.demand_RepeatedTenTimes_OneRequestNoMoreThanTarget`, `GraphDemandMechanismTests.demand_Disappeared_PriorityReturnsAndComesBack`, `GraphDemandMechanismTests.demand_TargetGrowsWithSpirits_RequestCoversWholeShortage` | покрыто |
| 23 | износ брони перед боем → ремонт/замена | `GearInputsTests.events_WornHelmet_AddsArmorWorn`, `CrossSupplyScenarioTests.combat_WoundedWithoutArmor_FightsBeforeCraftingArmor` | есть |
| 24 | нет лука/стрел у стража → крафт | `DemandFoodChainScenarioTests.case24_NoBowOrArrows_GuardCraftsBowAndArrows` | покрыто |
| 25 | нет наковальни → железо → наковальня | `CrossSettlementScenarioTests.repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool` | есть |
| 26 | мало зелий лечения при частых ранах → алхимия | `BrewGraphPlannerTests.plan_StageThreePotionFullLine_Feasible` | есть |
| 27 | нет книг/полок этапа 4 → кожа, бумага | `DemandWorkChainScenarioTests.case27_BooksAndShelvesForStageFour_RaisedThroughLeatherAndPaper` | покрыто |
| 28 | нет тростника → посадка у воды | `DemandFoodChainScenarioTests.case28_SugarCaneMissing_PlantingRaisedUntilStocked` | покрыто |
| 29 | нет станка мастерской → крафт станций | `DemandWorkChainScenarioTests.case29_NoWorkshopStation_PlacedThroughPlanks` | покрыто |
| 30 | лава у шахты → вода/булыжник до работы | `DemandSafetyChainScenarioTests.case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork` | покрыто |
| 31 | шахту затопило → блоки, факелы, обход | `DemandSafetyChainScenarioTests.case31_FloodedMine_BlocksTorchesAndBypassRaised` | покрыто |
| 32 | обвал/гравий → поддержки, не копать под песком | `DemandSafetyChainScenarioTests.case32_Collapse_SandAndGravelSupportRaised` | покрыто |
| 33 | нет ступеней вниз → доски/булыжник | `DemandWorkChainScenarioTests.case33_NoStairsOutOfPit_PlanksAndStoneRaised` | покрыто |
| 34 | жила кончилась → новая жила/смена шахты | `DemandWorkChainScenarioTests.case34_OreVeinEnded_SustainMineRaised` | покрыто |
| 35 | нет глины/кирпича для плана → глина, печь | `DemandWorkChainScenarioTests.case35_BricksMissingForForge_ClayAndFurnaceRaised` | покрыто |
| 36 | нет камня для стен → шахта камня до плана | `GraphRequiresTests.plan_MineNotBuiltNoCobble_CobbleWaitsMineRaised` | есть |
| 37 | нет красителей для кроватей → цветы/красители | `DemandWorkChainScenarioTests.case37_ColoredBedPlan_DyeAndFlowersRaised` | покрыто |
| 38 | нет кожи → забой коров | `DemandWorkChainScenarioTests.case38_LeatherMissing_CowSlaughterRaised` | покрыто |
| 39 | нет золота/алмазов этапов 3–4 → шахта глубже | `DemandWorkChainScenarioTests.case39_DiamondForStageThree_DeeperMineRaised` | покрыто |
| 40 | нет нитей → паутина/овцы | `FarmGraphTests.plan_StringsStocked_WeaveWoolIsWork` | есть |
| 41 | пожар в поселении → вода/засыпка | `DemandSafetyChainScenarioTests.case41_FireInSettlement_WaterAndBlocksRaised` | покрыто |
| 42 | перегруз: свободных духов нет → ротация | `More7RulesTests.workload_AllSpiritsOnOneTaskWithPendingNeeds_Piled`, `More7RulesTests.workload_BusiestOverWorking_Rejected` | есть |
| 43 | мест ночёвки меньше духов → кровати и дом | `GraphPlannerTests.threshold_BedPerSpirit_NotMultipliedByStage`, `More2RulesTests.sleep_FewerBedsThanSpirits_AsksForBed` | есть |
| 44 | дальний поход → еда, лагерь, факелы | `GraphPlannerTests.plan_NetherExpedition_NeedsPartyArmorPotionsFood` | есть |
| 45 | цель недостижима → мост/тропа/разведка | `DemandWorkChainScenarioTests.case45_UnreachableResource_ExplorationAndBridgeRaised` | покрыто |
| 46 | грязные тропы тормозят → улицы | `DemandWorkChainScenarioTests.case46_TrailsSlow_TrailAndBlocksRaised` | покрыто |
| 47 | ворота/двери сломаны → ремонт до ночи | `DemandSafetyChainScenarioTests.case47_FenceBrokenNight_RepairRaisedAboveGuard` | покрыто |
| 48 | мало света у грядок → факелы у посевов | `DemandSafetyChainScenarioTests.case48_CropsDark_TorchesRaisedUntilLit` | покрыто |
| 49 | нет ведра → железо → ведро | `DemandSafetyChainScenarioTests.case49_NoBucket_IronChainRaisedThenBucketCrafted` | покрыто |
| 50 | нет ножниц/лопаты/мелких инструментов → железо | `CrossFarmSupplyScenarioTests.husbandry_ShearingAndWornShears_RepairAndFenceStayPosted`, `GraphAllocationGroupBTests.case23_DifferentTools_DifferentWork` | есть |
