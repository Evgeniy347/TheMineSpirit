## Архитектура (пакеты/классы, пути файлов)

### Жители (villager) — `mill/org/millenaire/common/`
- `MillVillager.java` (≈4163 стр.) — сущность-житель: `familyName/firstName`, `gender`, `housePoint`,
  `townHallPoint`, инвентарь (`HashMap<InvItem,Integer>`), текущая `goalKey`, диалоги, торговля
  (`merchantSells`), здоровье/атака/экипировка, NBT-сериализация (`readEntityFromNBT`, ~2921–3036).
- `VillagerType.java` (461) — «тип/профессия» из `cultures/<c>/villagers/*.txt`: `native_name`, `gender`,
  `goal=...`, `collectgood`, `bringBackHomeGood`, `startinginv`, `requiredgood/requiredfood`,
  `toolneededclass`, `merchantstock`, `familyNameList/firstNameList`, `hiringcost`, теги.
- `VillagerRecord.java` (514) — «паспорт» жителя для сохранения: `id`, `type`, `firstName/familyName`,
  `fathersName/mothersName/spousesName/maidenName`, `housePos/townHallPos`, `inventory`, `questTags`,
  `killed/raidingVillage`. Хранится внутри NBT ратуши.
- `goal/*` (≈50 классов) — поведение (см. «AI»).

### Культура и локализация (culture) — `Culture.java` (2267) + `cultures/`, `languages/`
- `Culture` — реестр: `villagerTypes`, `villageTypes`/loneBuildings, `planSet` (здания), `goodsByItem`,
  `shopSells/shopBuys/shopBuysOptional/shopNeeds`, загрузчики `loadShops`, `loadVillagers` и т.д.
- `Culture.CultureLanguage` — языковой слой культуры: `sentences`, `dialogues`, `reputationLevels`,
  `buildingNames`, локализованные строки. Диалоги — `CultureLanguage.Dialogue` с условиями
  (`v1`/`v2` роль, `rel`/`notrel` отношения, `tag`, `building`, `villager`) и «предложениями» с таймингами
  (`v1;0;текст`, `v2;30;текст`) — это сценарий разговора двух NPC.
- Данные: `languages/<lang>/<culture>_{sentences,dialogues,reputation,buildings,strings}.txt`.
  `reputation`-файл: строки `порог;Название;текст-при-этом-уровне`, пороги — выражения (`4*64`,
  `2*64*64`, вплоть до `32*64*64`; отрицательные — вражда).
- `culture.txt` задаёт мелочи (например `qualifierSeparator`, `knownCrop`).
- Названия: `namelists/*.txt` (имена/фамилии по профессии и полу).

### Экономика (goods/economy) — `item/Goods.java` (1055), `InvItem.java`, `VillageType.java`, `Building.java`
- `Goods` — товар: `sellingPrice`, `buyingPrice`, `reservedQuantity` (сколько держать «для себя»),
  `targetQuantity` (целевой запас), `foreignMerchantPrice` (цена для чужеземного торговца),
  `minReputation`, `requiredTag`, `autoGenerate`, `desc`.
- Прайс-листы — по культуре: `cultures/<c>/traded_goods.txt` (CSV: `name;selling;buying;reserved;
  target;foreignMerchant;autoGen;equipment;minRep;desc`). Значения — **выражения** (`2*64`, `6*64`).
- Цены конкретной деревни переопределяются в `cultures/<c>/villages/*.txt`: `sellingPrice:good,price`,
  `buyingPrice:good,price`.
- Склад: `BuildingResManager.java` (1050) — списки `chests`, `furnaces`, `soils`, `spawns`, `stalls` и
  метод `getChestsContent()`. `Building.countGoods(item,meta)` суммирует содержимое сундуков; спрос/запас —
  `nbGoodAvailable(...)` (сколько можно продать с учётом `reservedQuantity`, нужд жителей и стройки) и
  `nbGoodNeeded(...)` (сколько не хватает до `targetQuantity`).
- Производство/логистика: `goal/generic/GoalGenericCrafting.java`, `GoalGenericCooking.java`,
  `GoalGenericHarvestCrop.java`, `GoalGenericPlantCrop.java`, `GoalGenericSlaughterAnimal.java` —
  параметризуются текстовыми файлами из `goals/generic*/*.txt` (что из чего делается).

### Торговля (trade)
- С игроком: `Building.computeShopGoods(player)` → `shopSells`/`shopBuys` (по имени игрока), цены —
  `Goods.getBasicSellingPrice/getBasicBuyingPrice` с учётом цен деревни; GUI `client/gui/GuiTrade.java`,
  `ContainerTrade.java`. Продавец — `goal/GoalBeSeller.java`.
- Между деревнями/культурами: **чужеземные торговцы**. `Building` на рынке создаёт foreign merchant; тип
  берётся из культур деревень с отношением `> RELATION_VERYGOOD` (шанс растёт с числом дружественных
  деревень). `MillVillager.getBasicForeignMerchantPrice` — цена ×1.5, если товар чужой культуры.
  Голы `GoalForeignMerchantKeepStall`, `GoalMerchantVisitBuilding`, `GoalMerchantVisitInn`.
- Учёт внешней торговли: `Building.exported`/`imported` (+ `addToExports/addToImports`), панели-таблички
  со статистикой.

### Квесты (quest) — `Quest.java` (1137), `SpecialQuestActions.java`, `quests/`, `languages/*/quests_*.txt`
- Формат `quests/<group>/<key>.txt`: `minreputation`, `chanceperhour`, `maxsimultaneous`; блоки `step:new`
  с `definevillager:key=...,type=<culture>/<vtype>[,relatedto=..,relation=samehouse|samevillage|
  nearbyvillage|anyvillage]`, `villager`, `duration`, `requiredgood:g,n`, `rewardmoney`,
  `rewardgood:g,n`, `rewardreputation`, `penaltyreputation`, `showrequiredgoods`, теги
  (`set/clear/required/forbidden` — глобальные, игрока, жителя), `setactiondatasuccess`, `bedrockbuilding`.
- `Quest.testQuest(...)` проверяет шанс/лимит/теги/репутацию деревни и подбирает связанных жителей по
  `relation`; `QuestInstance` хранит `step`, тайминги, жителей. Тексты квестов — в языковых файлах,
  ключи вида `guardwife_0_description`.
- `SpecialQuestActions` — «скриптовые» действия сюжетных квестов (borehole, underwater, top/bottom,
  mayan siege и т.п.) через теги/action-data игрока.

### Строительство (building) — `building/`
- `BuildingPlan.java` (4133) — **один уровень** одного варианта здания: `length/width`, `startLevel`,
  `areaToClear` (`around`), `nativeName`/локализованные имена, `shop`, `maleResident/femaleResident`,
  `priority`, `reputation`, `price`, `isgift`, `minDistance/maxDistance`, `requiredTag`, `tags`,
  `subBuildings`, `resCost` (`HashMap<InvItem,Integer>` — стоимость), `plan` (`PointType[][][]`).
- Хранение: `buildings/<group>/<name>_A.txt` — метаданные + строки уровней; геометрия — PNG
  `<name>_A0.png`, `_A1.png`, … (пиксель = точка пола, цвет кодирует блок/маркер). `blocklist.txt` —
  таблица «цвет/символ → `PointType`»: имена-маркеры (`mainchest`, `sleepingPos`, `sellingPos`,
  `craftingPos`, `defendingPos`, `leasurePos`, `soil/ricesoil/...`, `oakspawn`, `stall`, `signs`,
  `lockedchest`) и блоки (`logpine;17;1;...;colour`). Именованные маркеры → `BuildingResManager`
  (точки сна/работы/торговли/полей/спавнов).
- `BuildingPlanSet.java` — набор вариантов (A/B/C), каждый со списком уровней = **апгрейды здания**.
- `BuildingProject.java` — кандидат-проект деревни, вес = `priority`; категории
  `EnumProjects{CENTRE, START, PLAYER, CORE, SECONDARY, EXTRA, CUSTOMBUILDINGS}`.
- `VillageType.java` (687) — тип деревни из `cultures/<c>/villages/*.txt`: `centre`, `start`, `player`,
  `core`, `secondary`, `weight`, `radius`, `pathmaterial`, `sellingPrice/buyingPrice`, `biomes`,
  квалификаторы, `nameList`. Деревни `hameau` (деревня) и lone buildings (одиночные здания).
- `Building.java` (7263) — **экземпляр** здания в мире: сундуки, `vrecords`, постройка по шагам
  (`GoalConstructionStepByStep`), заказ ресурсов (`GoalGetResourcesForBuild`), отношения с другими
  деревнями (`relations`, `adjustRelation`), репутация, флаги `isTownhall/isMarket/isInn`.
- `WorldGenVillage.java` — генерация: дистанции между деревнями, проверка биома/площадки, выбор типа по
  весам, размещение центра и стартовых зданий, дорожная сеть.

### AI жителей — `goal/` и `pathing/`
- `Goal.java` (479) — абстрактная цель: `priority(villager)`, `getDestination`, `performAction`,
  `isPossible`, `isStillValid`, флаги `leasure`, лимиты `maxSimultaneousTotal`,
  `maxSimultaneousInBuilding`, `buildingLimit/townhallLimit`, `balanceOutput1/2` (производство «не
  более X, чем Y»), `canBeDoneAtNight/Day`, `range`.
- Набор: `GoalGatherGoods`, `GoalBringBackResourcesHome`, `GoalGetGoodsForHousehold`,
  `GoalDeliverGoodsHousehold`, `GoalGetTool`, `GoalSleep`, `GoalGoSocialise/GoChat/GoDrink/GoPray`,
  `GoalConstructionStepByStep`, `GoalGetResourcesForBuild`, `GoalBuildPath`, `GoalBeSeller`,
  `GoalGuardPatrol`, `GoalDefendVillage`, `GoalRaidVillage`, `GoalBreedAnimals`, `GoalChildBecomeAdult`,
  `GoalFish/Miner/Lumberman*/Harvest*/Plant*`, `generic/*` и `leasure/*`.
- Выбор цели — по приоритету среди возможных (утилитарный решатель), а не vanilla GoalSelector.
- Навигация: `pathing/AStarPathing.java`, `pathing/atomicstryker/*` (A* + JPS, `AStarConfig`), привязана к
  vanilla `PathEntity`.

### Персистентность (persistence) — `MillWorld.java` (1137), `UserProfile.java` (1004), `VillagerRecord.java`
- `MillWorld` на мир в каталоге `.../millenaire/`: `villages.txt`, `lonebuildings.txt` (позиции/тип/
  культура/для-кого), `tags.txt` (глобальные теги), `buildings/*.nbt` (сжатый NBT каждого здания),
  `profiles/<player>/` (профиль игрока).
- `UserProfile`: `config.txt` (репутации культур `culture_reputation=...`, язык `culture_language=...`,
  дипломатия), `tags.txt`, `actiondata.txt`, `quests.txt` (активные `QuestInstance`). Репутация:
  `villageReputations` (по деревне) + `cultureReputations` (двигается на `change/10`, клампится до
  `±64*64`/`-10*64`); уровни/подписи берутся из `<culture>_reputation.txt`.
- Отношения деревень: `Building.relations` (карта позиция→int, `RELATION_*` от `OPENCONFLICT=-90` до
  `EXCELLENT=90`), `adjustRelation(...)`; влияют на торговлю и квесты.

---

