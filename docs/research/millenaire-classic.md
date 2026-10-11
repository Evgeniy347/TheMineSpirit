# Millénaire (классика) — карта переиспользования

Исследование по локальному клону классического Millénaire от Kinniken
(`/opt/minecraft/research/millenaire`). Изменений в репозитории нет; это справочник идей для наших
подсистем семей/работ/склада/экономики/торговли/квестов/отношений/строительства/культур.
Наш проект — `The Mine Spirit`, NeoForge 1.21.1 / Java 21; ядро `com.theminespirit.core` без импортов
Minecraft. Дата анализа: 2026-10-11.

---

## Обзор (назначение, версия MC/загрузчик, Java, структура репо, лицензия)

**Назначение.** Классический Millénaire — мод, добавляющий самодостаточные NPC-деревни по мотивам
культур XI века (норманны, византийцы, индийцы, японцы, майя). Жители (мужчины/женщины/дети) имеют
работу, дом, семью, потребности, торгуют с игроком и между деревнями, строят и улучшают здания по
уровням, дают квесты, помнят репутацию и «отношения» между деревнями. Мир живёт автономно (расписания,
голы/цели, ночная/дневная активность).

**Версия и стек (решающие факты).**
- Мод: **Millénaire 6.0.1** — `mill/org/millenaire/common/forge/Mill.java:112` (`versionNumber = "6.0.1"`),
  `@Mod`-аннотация там же (`Mill.java:97`).
- Minecraft **1.7.10**, загрузчик **Forge / FML** (`cpw.mods.fml.*`, `net.minecraftforge.*`), эпоха
  `IIconRegister`, `LanguageRegistry`, числовых Block/Item ID. Java 6/7-эра (используются `final`-повсюду,
  diamond-оператор отсутствует).
- Это **не** современный NeoForge/1.21.1. Прямой перенос API невозможен, переносимы только идеи и форматы
  данных (см. «Портируемость»).

**Структура репозитория.** Три корня:
- `mill/` — Java-исходники (`mill/org/millenaire/client/*`, `mill/org/millenaire/common/*`) и ассеты
  (`mill/assets/millenaire/`). `common/` ≈ серверная логика, `client/` ≈ рендер/GUI.
- `millenaire/` — **контент и данные**: `cultures/` (5 культур: `norman`, `byzantines`, `hindi`,
  `japanese`, `mayan`), `quests/`, `goals/`, `languages/{en,fr}/`, `config*.txt`, `goods.txt`,
  `itemlist.txt`, `blocklist.txt`, `hof.txt`, `resources used*.txt`.
- `millenaire-custom/` — заготовка пользовательского контента (те же подкаталоги + `mods/`).

Git: один коммит `9b9607e "Minor fixes"`, remote `https://github.com/Kinniken/millenaire.git`. Два файла
моделей помечены `// Decompiled by Jad ... Copyright 2001 Pavel Kouznetsov` — признак смешанного
происхождения кода (частично декомпилирован).

### Лицензия (критично)

- В клоне **нет файла LICENSE/COPYING** ни в корне, ни в подкаталогах. По умолчанию это
  **all-rights-reserved**.
- Официальная страница (millenaire.org/contribute) и CurseForge описывают **custom proprietary license**:
  - «Millénaire as a whole, and all parts specifically developed by Kinniken, are copyrighted to Kinniken,
    **all rights reserved**, 2011–2026».
  - Использование мода и **некоммерческая** перепаковка в модпаки — разрешены при указании авторства и
    ссылки на millenaire.org.
  - **«Use of elements of the mod in other mods or other projects is forbidden without authorization»** —
    использовать элементы мода (код, данные, текстуры, blueprints, тексты) в других проектах запрещено.
- Это **не copyleft** (не GPL). Но лицензия **запретительная**: она не даёт прав на копирование/переработку.
  Вывод: **только clean-room** — читать и переосмысливать идеи, самостоятельно писать код и заново
  порождать данные. Ни строчки кода, ни контент-файлов копировать нельзя.
- Для контекста (это **другие** репозитории, не этот клон, лежат рядом в `/opt/minecraft/research/`):
  `millenaire-neoforge/LICENSE` — **GPL-3.0**, `millenaire-rewrite/LICENSE.txt` — **LGPL-2.1** (Forge).
  Это сторонние проекты; на классический клон их лицензии **не** распространяются.

---

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

## Что переиспользовать у нас (идея → наша система)

| Идея Millénaire | Куда у нас (семьи/работы/склад/торговля/квесты/отношения) |
|---|---|
| **Данные как текст, а не код**: `villagers/*.txt`, `shops/*.txt`, `traded_goods.txt`, `quests/*.txt`, `blocklist.txt` | Наши реестры работ/рецептов/квестов — декларативные ресурсы (JSON/datapack), читаемые ядром без Minecraft. Формат Millénaire — образец того, что контент должен быть данными. |
| **Профессия = набор целей + теги + требования + личный инвентарь** (`VillagerType`) | Наша профессия: список работ, теги роли, требуемые инструменты/еда, стартовый инвентарь, условия найма. |
| **Дом + семья + паспорт жителя** (`VillagerRecord`: spouse/father/mother/housePos) | Наши семьи/домохозяйства: у духа есть дом, партнёр, родители/дети; персистентная запись жителя. |
| **Целевой запас и неприкосновенный резерв** (`targetQuantity`/`reservedQuantity`) | Склад: у каждого товара целевой уровень и зарезервированный минимум; заявки «дозаказать до target», «не отдавать ниже reserve». |
| **Спрос/предложение через доступность** (`nbGoodAvailable`/`nbGoodNeeded`, учёт нужд жителей и стройки) | Склад/производство: считать доступное как «запас − резерв − нужды − стройка»; планировщик заказов из дефицита. |
| **Дефицит товара → заказ, излишек → продажа** (магазины `needs`/`sells`, `GoalGetResourcesForShops`/`GoalDeliverResourcesShop`) | Внутренняя логистика: магазин заказывает сырьё, свободные жители разносят; излишек — на экспорт. |
| **Торговля с избытка с ценами продажи/покупки и репутационным порогом** (`Goods`, `computeShopGoods`) | Наша торговля: у торговца набор «продаёт/покупает» с ценами; часть позиций открывается по репутации/культуре. |
| **Чужеземный торговец: межкультурная торговля с наценкой ×1.5 и импортом** | Торговля между поселениями/культурами: приезжий торговец приносит чужой ассортимент, наценка за «чужое». |
| **Отношения деревень как числовая шкала с уровнями** (`RELATION_*`, `adjustRelation`, войны/рейды) | Наши отношения: шкала -100..100 между семьями/поселениями, влияет на торговлю, союзы, конфликты. |
| **Репутация двух уровней: деревня + культура** (`villageReputations`/`cultureReputations`, уровни из текста) | Наши отношения: личная репутация у поселения/семьи и общая у культуры, с уровнями-подписями и текстом. |
| **Квест как цепочка шагов с условиями и наградами** (`Quest`/`QuestStep`: requiredgood, reward, penalty, теги) | Наши задания: данные-скрипт с шагами, требованиями, наградами/штрафами, тегами прогресса. |
| **Связывание жителей в квестах** (`definevillager` + `relation=samehouse/samevillage/nearbyvillage/anyvillage`) | Наши задания/отношения: квест вовлекает связанных NPC (сосед, родня, другая деревня) по типу родства. |
| **Глобальные теги мира и персональные теги игрока** (`globalTags`, `profileTags`) | Флаги состояния мира/игрока для гейтинга контента (что уже произошло и что открылось). |
| **Композиция зданий из уровней-апгрейдов** (`BuildingPlanSet` = варианты × уровни, `priority`) | Строительство: здание растёт по уровням, вариации планировки; вес/приоритет выбора следующего. |
| **Декларативная стоимость постройки** (`resCost` из плана + маркеры) | Стройка: стоимость выводится из плана, а не задаётся руками; дефицит → задания на добычу. |
| **Именованные маркеры в структуре** (`blocklist.txt`: mainchest, sleepingPos, sellingPos, soils, spawns, stalls) | Строительство: в чертеже хранить «роли точек» (склад, спальня, пост торговли, поля, загоны), а не только блоки. |
| **Ночной/дневной цикл и «досуг» с приоритетом работы** (`canBeDoneAtNight`, `leasure`) | Расписания/жизнь: ночью сон, днём работа; досуг только когда нет доступной работы. |
| **Ограничители одновременности** (`maxSimultaneousTotal/InBuilding`, `balanceOutput`) | Детерминированная тактика: не более N на цель, баланс выпуска (не производить A, пока A≥B). |
| **Локализация и «изучение языка»** (`CultureLanguage`, `language_learning`, названия из namelists) | Культуры: тексты по культурам, знание языка игроком/духом открывает перевод, культурные имена. |

---

## Портируемость (старый MC → NeoForge 1.21.1)

Переносить **идеи и форматы данных**, не API. Конкретные замены:

| Классика (1.7.10 / FML) | NeoForge 1.21.1 |
|---|---|
| `cpw.mods.fml.*`, `@Mod`, `@Instance`, `@SidedProxy` | `@Mod`, конструктор мода, `IEventBus`, DeferredRegister, `Dist` |
| `GameRegistry`, `EntityRegistry`, числовые ID | `DeferredRegister.Blocks/Items/EntityType`, `ResourceLocation`, реестры |
| `IIconRegister`/`IIcon`, `setTextureName` | Texture atlas + `ItemModel`/`BlockModel` (JSON) |
| `LanguageRegistry` | `Component`/`translation` + lang JSON |
| `net.minecraft.nbt.*`, `writeToNBT` | `DataComponents`, `Codec`/`SavedData`/`ValueInput` |
| `Blocks/Items.*` (init-константы) | `Blocks.*` доступны, но регистрация — через реестры; предметы/блоки — с `DataComponents` |
| vanilla `PathEntity`, `EntityAITasks`, `GoalSelector` | собственный детерминированный тактический слой ядра; vanilla-AI — только тонкий адаптер |
| A* JPS (`atomicstryker`) на `World` | свой A* в ядре на абстрагированной сетке навигации |
| `ItemStack`/`InvItem` с числовым meta | `ItemStack` + `DataComponentPatch`, идентификация по `Item`+компонентам |
| сервер/клиент-деление через прокси | `@Mod(dist=...)`, `IPayload`-пакеты (`CustomPacketPayload`) |
| сохранение в каталог `<save>/millenaire` | `SavedData` в `DimensionDataStorage` или `Level`-капабилити |

**Ключевые архитектурные уроки, переносимые без кода:** (1) контент полностью вынести из кода в данные;
(2) «мозг» жителя — приоритетный решатель целей над абстрактной моделью мира (это ложится в `core` без
Minecraft); (3) экономика — на «запас/резерв/цель + доступность», а не на скрипты; (4) строительство —
данные чертежа + выведенная стоимость + маркеры-роли; (5) всё состояние (жители, деревни, теги,
репутации, квесты) — сериализуемо и версионируемо.

**Что не переносится:** рендер/GUI/модели, vanilla-навигация и `PathEntity`, любые числовые ID, форматы
PNG-blueprint «как есть», NBT-схема, сетевой протокол Millénaire. Их надо проектировать заново.

---

## Конкретные рекомендации (брать/адаптировать/избегать)

- **Брать (идея → собственная реализация):**
  - декларативные реестры работ/рецептов/квестов/товаров как данные ядра;
  - модель «целевой запас + резерв + доступность» для склада и торговли;
  - двухуровневую репутацию (поселение + культура) с уровнями-подписями;
  - числовую шкалу отношений между поселениями и её влияние на торговлю/союзы;
  - цепочные квесты со связанными NPC и тегами прогресса;
  - уровни-апгрейды зданий с выведенной стоимостью и маркерами-ролями точек;
  - приоритетный решатель целей с лимитами одновременности и «балансом выпуска».
- **Адаптировать:** интервалы цен/порогов из прайс-листов Millénaire — как источник калибровки
  геймдизайна (переосмыслить числа, не копировать таблицы); «чужеземный торговец» — как механизм
  межпоселенческой торговли, но с нашей экономикой и нашим транспортом.
- **Избегать:** копирования кода, текстур, звуков, голосов, blueprints/PNG, прайс-листов, квестовых и
  диалоговых текстов, таблиц `blocklist.txt`/`goods.txt`/`itemlist.txt` — всё это охраняемый контент.
  Не тащить vanilla-AI/`GoalSelector`, FML-паттерны, числовые ID, PNG-ас-формат чертежей.

---

## Риски и лицензионные ограничения

- **All-rights-reserved / custom proprietary.** Нет open-source лицензии. Прямое копирование кода,
  данных или ассетов **запрещено** и юридически рискованно; допустим только clean-room-анализ идей.
- **Это не copyleft.** GPL/LGPL из соседних репозиториев (`millenaire-neoforge`, `millenaire-rewrite`)
  к этому клону **не** относятся — не путать и не опираться на них как на разрешение.
- **Смешанное происхождение.** Часть файлов помечена как декомпилированные (Jad) — повышает
  неопределённость прав; тем более нельзя копировать.
- **Контент охраняется отдельно.** Даже «данные» (тексты квестов/диалогов/репутаций, имена, прайсы,
  чертежи) — охраняемые произведения. Нужны собственные тексты и числа.
- **Технический риск противовеса версий.** 1.7.10 → 1.21.1 — почти полная переработка API, NBT и
  навигации; попытка «перенести код» упрётся в несовместимость и в лицензию.
- **Митигация:** вести собственные ресурсы (datapack/JSON) и собственное ядро `core`; в документации
  фиксировать источники вдохновения, а не происхождение кода; при спорной близости текста/чисел —
  переписывать с нуля по смыслу.
