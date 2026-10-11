# Millénaire (NeoForge 1.21.1 и rewrite) — карта переиспользования

> Исследование двух современных портов/переписываний Millénaire для проекта «The Mine Spirit»
> (NeoForge 1.21.1, Java 21, REST-управляемый ИИ, чистое ядро `com.theminespirit.core` без импортов Minecraft).
> Репозитории читались локально. Сборки не запускались, файлы проекта не менялись.

---

## Обзор репозиториев

| Репозиторий | Целевые версии | Стек | Структура | Лицензия |
|---|---|---|---|---|
| `Leviaria/Millenaire` (папка `millenaire-neoforge`) | **MC 1.21.1, NeoForge 21.1.180, Java 21** → ⚠️ совпадает с нашим стеком | Gradle + `net.neoforged.moddev 2.0.78`, single-module, `mod_id=millenaire`, пакет `org.millenaire`, deps: только NeoForge testframework | 583 Java-файла, один модуль; `src/main/java`, ресурсы: `assets/`, `data/`, свой каталог `millenaire/` (JSON-данные культур). DataGen (`runData`) в `src/generated/resources` | **GPL-3.0 (copyleft)**. ВНИМАНИЕ: `gradle.properties` → `mod_license=All Rights Reserved`; README заявляет GPL-3.0 и что оригинал (Kinniken) и порт MoonCutter2B были MIT. Расхождение в метаданных, **реальная лицензия по файлу LICENSE — GPL-3.0** |
| `WangMioG/Millenaire-rewrite` (папка `millenaire-rewrite`) | MC 1.20.1, Forge 47.4.3 (не NeoForge), Java 17 | Gradle + ForgeGradle `[6.0,6.2)`, single-module, `mod_id=millenaire_rewrite`, пакет `com.jasoncian.millenaire_rewrite` | 36 собственных Java-файлов (только фундамент контента: блоки/предметы/табы/datagen/GUI). + `reference/OldSource/` (317 Java-файлов исходного Millénaire 1.8/1.12) + `reference/迁移计划-AI总结/` (3,6k строк китайских AI-документов по анализу старого кода) | **Неоднозначно**: README/бейдж/`mod_license=MIT`, но реальный `LICENSE.txt` содержит **несменённый LGPL-2.1-шаблон MinecraftForge** (скопирован из форджевого шаблона). Пока не выяснено — трактовать как ограничительную/неточную лицензию, код не копировать |

Итог по лицензиям: **оба внешних кодовых баз как таковые в чужой код копировать нельзя** (GPL-3.0 — kopileft у первого; у второго LICENSE.txt не соответствует заявленному MIT). Переносить можно только идеи/данные с подтверждённым MIT-происхождением (см. «Риски»).

---

## Архитектура (по пакетам/классам)

Все пути — относительно `src/main/java/org/millenaire/` (неофордж-порт, основной объект анализа).

### NPC / AI (`entity/`, `goal/`)
- **НЕ vanilla `Brain`**. Житель `MillVillager extends PathfinderMob` (entity/MillVillager.java:84). Атрибуты через `AttributeSupplier` (стр. 812). В `goalSelector` только вспомогательные: `FloatGoal`, `OpenDoorGoal`, `OpenFenceGateGoal` (стр. 173–175).
- Кастомный планировщик задач: `goal/GoalScheduler.java` (стр. 14), `VillagerGoal` (стратегия) + `VillagerTask` (выполняемая задача). Выбор цели: `pickBest()` по приоритету + «досуговость» (`isLeisure`), ограничение по времени суток (`isTimeAllowed`), cooldown `reoccurDelayTicks`, watchdog на застревание (6000 тиков без прогресса), idle-backoff. Можно форсить задачу извне (`forceTask`) — **это ключевая точка для нашего REST-ИИ**.
- Роли/профессии = `VillagerType` (culture/VillagerType.java:17) — рекорд со списком `goals` (ResourceLocation), тегами (`chief`, `localmerchant`, `foreignmerchant`, `hostile`, `helpInAttacks`…), `spawnWeight`, `requiredGoods`, `toolNeededClasses`, `foreignMerchantStock`, `hiringCost`. Задачи собираются из реестра `goal/GoalRegistry.java` по ID (реестр наполняется в коде, Millenaire.java:180–280; плюс JSON-driven visit-goals).
- 25 готовых задач в `goal/impl/`: `BuildGoal`, `BuildPathGoal`, `SellerGoal`, `BringBackHomeGoal`, `GatherGoodsGoal`, `GetResourcesForShopsGoal`, `GetGoodsForHouseholdGoal`, `DeliverResourcesToShopGoal`, `RestGoal`, `SocialiseGoal`, `ChatGoal`, `ObserveVillagerGoal`, `MerchantVisitBuildingGoal`, `LightHearthGoal`, `ChildBecomeAdultGoal`, `PlayGoal` и др.
- **Расписания** — не schedule-фазы, а:
  - окна времени в goal: `minimumHour`/`maximumHour` (0–12500 и т.п.);
  - день/ночь-гейтинг (`canBeDoneAtNight`, `canBeDoneInDayTime`);
  - «долг сна» `sleepDebtTicks` (entity/MillVillager.java:105, 981) — накопительный цикл сна;
  - ночные действия деревни (`NightActionHelper`, `last_night_action_day`).
- Data-driven «визитные» цели: `visit_goal/*.json` (типы `visitBuilding`, `observeVillager`, ...) загружаются `goal/visit/VisitGoalLoader.java` — приоритет, окно времени, `buildingTag`, `heldItems`, `buildingTag` для плейсмента в конкретном здании.
- Сбор ресурсов: `gathering_type/*.json` (напр. `chop_trees.json`) + `SpotGatheringGoal` (зарегистрированы в коде для SNAIL/SILK/BRICK и т.п.) + `GatheringGoal` (handler-driven), пассивный pick-up (`tickPassivePickup`).
- Навигация: кастомные `MillPathNavigation`, `VillagerNavigationManager`, `MillWalkNodeEvaluator`, `WaypointNavigator`/`WaypointGraph` (междеревенские дороги), анти-топиловка листьев, анти-утопление/анти-застревание с таргетным телепортом. `SETTINGS`: follow_range 120, step_height 1.

### Экономика / склад / торговля (`commerce/`, `village/`)
- **Данные товаров**: `traded_goods.json` на культуру → `TradeGood` (commerce/TradeGood.java:12): `sellingPrice`/`buyingPrice`, `reservedQuantity`/`targetQuantity`, `minReputation`, `category`, `autoGenerate`, поддержка тегов (`#minecraft:logs`). Схемы магазинов: `shops/*.json` → `ShopProfile` (sells/buys/buysOptional/deliverTo).
- **Валюта**: реальные предметы-монеты `denier`, `denier_argent`, `denier_or` (3 номинала, item/ModItems.java:40–42) + `MoneyHelper` (баланс/списания со стака инвентаря игрока). Награды в денариях (`rewardMoney` у квестов), покупка зданий за денарии.
- **Склад здания**: `BuildingInventory` (сундуки здания), `BuildingInstance.imported/exported` карты предметов, «начальное наполнение» `GoodsRestockHelper.fillStartingGoods`. Склады/запасы живут в сундуках мира (vanilla `Container`), а не в отдельной economy-СУБД.
- **Рынок**: `MarketManager` — ночные «рыночные дни», спавн иностранных купцов на `stall`-позиции, `TownHall`-лимиты в gathering (`townhallLimit`), `ForeignMerchantKeepStallGoal`. Рест ок в сундуки по `target_quantity`.
- **Торговое меню**: `TradeMenu` / `TradeScreen`, покупка-продажа против кошелька игрока, цены от `reputation`. `VillageSellerDispatcher`, `LocalMerchantHelper`. Иностранцы держат прилавки N ночей (накапливают `nb_nights_merchant`).
- **Внутридеревенская цепочка**: закуп для лавок `GetResourcesForShopsGoal` + `DeliverResourcesToShopGoal`, домашние запасы `GetGoodsForHouseholdGoal`, склад ратуши. Это аналог наших «профессий» и потоков «дом→лавка→склад».

### Квесты (`quest/`, `dialogue/`)
- Полностью **data-driven**: `quests/*.json` (например `quests/quests/normanbasic/abbotbooksdelivery.json`) + манифесты. `Quest` (quest/Quest.java:5): `chancePerHour`, `maxSimultaneous`, `minReputation`, глобальные/игрок/теги, `villagerDefs`, `steps`.
- `QuestStep`: цель (villagerKey), `duration`, `requiredGoods`, `rewardMoney/Goods/Reputation`, теги успеха/провала (`villagerTags*`, `playerTags*`, `globalTags*`), `actionDataSuccess`, `relationChanges`, `bedrockBuildings`. Продвижение по шагам, `QuestManager.tickQuests(ServerPlayer, ServerLevel)` — world/игро-временные триггеры. `QuestInstance`/`QuestInstanceVillager`, `MarvelManager`, `QuestTextRenderer`.
- Диалоги/реплики: `dialogue/` (Dialogue, DialogueLoader, SentenceLoader) + `VillagerSpeech` — реплики keyed по `культура/роль/goalKey/вариант`, спрайт-бабблы + чат (payload `SpeechChatPayload`, дальность из конфига), «изучение языка» (штраф понимания без языка `PlayerCultureReputation.getLanguageKnowledge`).
- Отношения в квестах: `relation: "nearbyvillage"` (QuestVillagerDef), `RelationChange`.

### Отношения / репутация / культуры
- **Репутация**: `VillageReputation` (UUID→int, пороги-лейблы из `reputation.json`/`culture_reputation.json`, например норманы 13 ступеней до `next_of_kinniken`); штраф/плюс при уроне по жителю (`MillVillager.hurt`, −10×damage). `PlayerCultureReputation` (языковые знания, культуры).
- **Отношения деревня↔деревня**: `VillageRelations` (±100 со ступенями от `open_conflict` до `excellent`), `VillageRelations`/`VillageDiplomacyHelper`, `DiplomacyHelper`. Хранятся `relations` в SavedData (UUID→int).
- **Культуры**: `Culture` (culture/Culture.java:8) — id, displayName, `nativeLanguage`, каталоги путеводителя, `knownCrops`/`knownHuntingDrops`, баннер NBТ. Культуры: norman, byzantines, indian, inuits, japanese, mayan, seljuk (7). `ModCultures`/`CultureLoader` + манифесты. Имена: `namelists/`. Внешность: cloth-сеты, 2 слоя, генерация имён `VillagerAppearanceFactory`; дети/взросление (`isChild`, `ChildBecomeAdultGoal`, рост `childSize`).

### Строительство (`building/`, `world/`)
- **Схемы**: JSON-описатель здания (уровни/варианты, footprint, `groundLevel`, `buildingOrientation`, теги, `shop`, `starting_sub_buildings`, `signs`) + **NBT-схемы** `*.nbt` на каждый уровень/вариант (например `building/townhalls/abbey_a_0.nbt`). `BuildingPlan` (building/BuildingPlan.java:8): `nbtPath`, size, `constructionOrder`, `terrainPolicy`, `specialPoints` (`stall`, `hearth`, `sleepingPos`, `sellingPos`, `craftingPos`, `pathStartPos`…), `shopId`.
- **Планировка**: `BuildingPlanSet` (вариант/уровень), `BuildingPlanSetLoader`, `_manifest/buildings.json` (сотни файлов). `BuildingInstance` (building/BuildingInstance.java, 766 строк): статус (UNDER_CONSTRUCTION→BUILT), `ConstructionTask`, подстроения (`sub_building`, `parent_building_id`), апгрейды, кровати `BedManager`, импорт/экспорт, brick-colour mapping, runtime-tags.
- **Плейсмент в мир**: `world/BuildingPlacer`, `SiteValidator`, `TerrainPreparer`, `PlacementConstraints`, `StructureAvoidance`, `VillageTerrainMap`, `TerrainTraversal`, `MockBlockProcessor`, `VillageWallGenerator`, дороги `village/path`. `VillageGrowthManager` выбирает PendingProject; `BuildingCostCalculator` считает требуемые ресурсы (свободный набор блоков: air/вода/трава/земля…); строительство ведут сами жители (`BuildGoal`, `BuildPathGoal`, `BuilderSafetyHandler` — безопасность при телепортах/застреваниях, `LightHearthGoal`).
- **Данные структур в datapack**: `data/millenaire/structure/*` — вспомогательные vanilla-структуры; `data/millenaire/recipe|advancement|loot_table` — datapack-контент. Основные данные культур — НЕ в datapack, а в собственном каталоге `resources/millenaire/*` мода, загружаемом кастомными лоадерами по `_manifest/*.json`.

### Данные и локализация
- Каталог данных: `src/main/resources/millenaire/` (cultures, quests, languages, visit_goal, gathering_type, wall_type, villager_config, templates, legacy, _manifest). Загрузка — манифест-файлами `_manifest/{buildings,villagers,villages}.json` (списки путей), свои лоадеры (`CultureLoader`, `QuestLoader`, `ShopProfileLoader`, `TradeGoodsLoader`, `BuildingPlanSetLoader`, `VisitGoalLoader`). JSON → рекорды. Наличие шаблонов `templates/_template_*.json` для авторов контента.
- Локализация: классические `assets/millenaire/lang/*.json` + собственная языковая система `language/` (`LanguageHelper`, `LocaleResolver`, `ServerTranslationCache`, `SentenceRenderer`, `I18nKeyAuditor`). Внутриигровые «языки» культур (`native_language`), перевод реплик «родной язык ↔ язык игрока», жесты/приветствия.
- NBT-блоки контента: `LockedChestBlockEntity`, `ImportTableBlockEntity`, панели (`village/panel/`).

### Persistence (`village/VillageSavedData.java`)
- **vanilla `SavedData`** (`Level.getDataStorage()`), одно хранилище на измерение: `millenaire_villages`. **Не attachments, не Codec**. Версионирование `CURRENT_VERSION = 4` с миграциями (напр. `migrateVillageTypeId`).
- Сохраняет «на всё»: деревни (id, культура, type, центр, имя), здания (plan, origin, rotation, status, beds/`BedManager`, `construction_task`, imported/exported, visitor log, runtime_tags, parent), рекорды жителей (`VillagerRecord.save`/`load`), репутации, `lastGoodsRefresh`, pending project, brick theme, купленные игроком здания, paths, отношения, chronicle (лента событий деревни), marvel. Обособленно: `AdvancementStatsManager extends SavedData`.
- Жители дополнительно пишут свой `addAdditionalSaveData` (villageId, дом, `sleepDebtTicks`, `foreignMerchantStallId`…). Сущности живут в чанках; `VillagerRecord` — «якорь» для респауна/переживания разгрузки чанков; `VillageChunkLoader` держит чанки (ticket controller в `RegisterTicketControllersEvent`).
- Остальной стейт — прямо в мире (сундуки, блоки), что упрощает выживаемость без экономической БД.

### Интеграция с NeoForge / сервер (`Millenaire.java`, `network/`)
- `NeoForge.EVENT_BUS`: `ServerTickEvent.Pre` (глобальный тик, Millenaire.java:401 — `VillageSavedData.get(overworld).getVillageManager().tick(...)`), `ServerStartedEvent` (загрузка данных, стр. 437), регистрация chunk loader. `ModEventBus` — реестры. `net.neoforged.moddev` runs client/server/data.
- **Networking**: NeoForge custom payloads (`RegisterPayloadHandlersEvent` → `ModPayloads.register`), десятки payload-классов (квесты, торговля, книга путешествий, панели, дипломатия, speech). S2C через `PacketDistributor.sendToPlayer`.
- **Конфиг**: `config/MillenaireServerConfig|Common|Client` (NeoForge config): тумблеры изучения языка, дальность реплик в чате и т.п.
- **Тесты**: `org.millenaire.test` — GameTests на NeoForge testframework (зависимость `net.neoforged:testframework`) — путь для наших тестов.

### `reference/` в neoforge-репо
`src/main/resources/millenaire/reference/README.txt` — служебная справка. Архив старого контента — `content/legacy/` (движок конвертации старых карт: `LegacyAutoConverter`, `ItemIdMapper`, `BiomeMapper`, конвертация квестов).

### Repo `millenaire-rewrite` — что в нём полезного
- Свои 36 классов: только «фундамент контента» — культурные семейства блоков (`CulturalBlockFamily`, `BuildingMaterial`, generic-система вариантов), предметы (rуse `ItemMillPurse`, `ItemMillParchment`, жезлы, амулеты), 8 креатив-вкладок, полностью datagen (model/loot/lang/blockstate/recipes), `CurrencyUtils`. Поселение/ИИ/стройка — **не реализованы** (дерево развития: Village 30%, Building 15%, Entity 10%, Mill Chest 10%).
- Ценность не в коде, а в документации:
  - `reference/迁移计划-AI总结/` — 8 китайских AI-документов «проект миграции»: обзор архитектуры оригинального 1.12 кода (структура `Culture`, `MillVillager`, AI-goals, GUI/сеть, ресурсы, план миграции). Хорошая **спецификация для clean-room** (по идеям старого MIT-кода).
  - `reference/OldSource/java/org/millenaire/common/*` — исходники старого Millénaire 1.8/1.12 (317 файлов) + `net/minecraft/block/BlockMillWallItem.java` (фордж-хак); `OldSource/resources` — старые lang/textures/recipes. `todeploy/` — пусто.
  - `ENHANCED_BLOCK_SYSTEM_DESIGN.md`, `HUAXIA_CULTURE_PLANNING.md`, roadmap-документы.

---

## Прямое совпадение с нашим стеком (можно брать почти как есть)

⚠️ «Взять почти как есть» = только по **паттернам/сборке/архитектуре**, НЕ по тексту классов (GPL-3.0). Под NeoForge 1.21.1 + Java 21 **рабочего открытого кода, разрешённого к копированию, у обоих репо НЕТ** — все данные/код под GPL/неоднозначной лицензией.

Что в неофордж-порте **дословно совпадает с нашим окружением** и переиспользуется как технический шаблон 1:1:
- **Сборка**: `net.neoforged.moddev` 2.0.78, `neoVersion 21.1.180`, ranges, runs client/server/data, `mods {}` block, UTF-8, manifests — готовый skeleton для нашего мода.
- **Событая модель**: `ServerTickEvent.Pre` глобальный тик, `ServerStartedEvent` для инициализации, `RegisterTicketControllersEvent` для удержания чанков, custom payloads (`RegisterPayloadHandlersEvent`) — те же API, что у нас.
- **Persistence**: vanilla `SavedData` с версионированием NBT + entity-NBT — наш выбор тоже; их схема (деревня→здания→жители-рекорды, `CURRENT_VERSION`, миграции) — референс для нашего ядра без изменений.
- **Тесты**: `net.neoforged:testframework` как `implementation`-зависимость — тот же способ, что предлагаем мы.
- **Конфиг**: NeoForge config — 1:1 с нами.

Конкретные паттерны, которые можно переиспользовать **как есть по идее** (переписывание под нашу лицензию/архитектуру тривиально):
- JSON-данные культур/квестов/зданий с манифестами и шаблонами (schema-контракты).
- Поля `TradeGood` (двойные цены продажи/покупки, reserved/target quantity, `minReputation`, категории) — готовая модель «экономики с избытка».
- Формат сегментов репутации (порог→i18n-ключ).
- Окна времени и гейтинг «рабочее/досуг» у целей, cooldown, watchdog застревания.
- `BuildingCostCalculator` со списком «свободных» блоков — многолетняя эвристика стоимости стройки.
- Иерархия инвентарей `VillagerInventory` (Карта<Item,Integer>, не item-стаки) поверх MC.

---

## Что переиспользовать у нас (идея → наша архитектура)

Наша особенность: ядро `com.theminespirit.core` без Minecraft, управление внешним ИИ через REST; `com.theminespirit.neoforge` — адаптер. Отсюда перенос идей:

1. **Задачи жителей как ресурсы намерения.** `GoalScheduler` (приоритет + `canStart` + окно времени + cooldown + watchdog) почти 1:1 ложится на наш «планировщик намерений», но: в ядре держим *чистый* state (world-agnostic), а MC-реализация задач — в адаптере. `forceTask()` — готовый шов для REST-ИИ (внешний ИИ может форсить задачу, менять приоритет).
2. **Роль = оркестрация целей, а не класс.** У них профессия = ID + список goal-ID + теги. У нас аналогично: роль → список навыков/задач в data-слофах; REST-ИИ читает тот же контракт.
3. **Экономика «с избытка»:** модель `TradeGood` (reserved vs target quantity, мин. репутация, категории) и потоки `GetResourcesForShopsGoal`→`DeliverResourcesToShopGoal` и `GetGoodsForHouseholdGoal` — готовая семантика, которую мы переносим в ядро как чистую модель товаропотоков, а сундуки/стаки оставляем адаптеру.
4. **Квесты как DSL:** `Quest`/`QuestStep` с тегами-переключателями (global/player/villager), шанс в час, `minReputation`, награды, `relationChanges` — идеальный формат для наших заданий, отдаваемых через REST. Теги = «флаги состояния», удобные и ИИ.
5. **Репутация и отношения** гранулярно: `VillageReputation` (игрок↔деревня, порог-лестницы), `VillageRelations` (±100 деревня↔деревня), события-хроника (`VillageEventType` + chronicle-lenta) — прямое PCI → в ядро как доменные сервисы; игровой слой дёргает по REST.
6. **Стройка:** JSON-описатель + NBT-схема по уровням/вариантам — формат переносим. `BuildingCostCalculator` (список бесплатных блоков) и `SiteValidator`/`TerrainPreparer` — в ядре чистая «модель стройки», в адаптере `StructureTemplate`/`StructurePlace`. `specialPoints` → привязка задач к точкам здания (у нас точки интересностей для REST-ИИ).
7. **Данные-манифесты:** механизм `_manifest/` + шаблоны — готовая идея пакета культур; у нас данные культур будут в data-pack-совместимых JSON, отдаваемых в ядро через нейтральные схемы (ContractFirst), а не через MC-рекорды.
8. **Persistence:** `SavedData` «одна запись на мир» + entity-NBT + «виртуальный рекорд жителя» для респауна после разгрузки чанков — переиспользуем как есть (это работающий, проверенный подход).
9. **Речь/языки:** «реплика = culture/role/goalKey/variant”, баббл в мире + «уровень знания языка» игроком — хорошая основа для RPG-слоя наших NPC.
10. **Навигация:** `MillPathNavigation` + `WaypointGraph` (дороги) + антизастревания (телепорт на поверхность через `getHeight`) — ценные готовые *алгоритмы*, которые можно переписать под нашу лицензию в адаптере.
11. **Chunk-удержание** (`VillageChunkLoader`) — поведение должно быть у наших поселений (иначе энтити разгружаются): паттерн 1:1.
12. **Legacy-конвертация** (`content/legacy`) — напоминание делать миграции данных с версии в версию с самого начала (их `CURRENT_VERSION`/мигратор).

---

## Конкретные рекомендации (брать / адаптировать / избегать)

**Брать (паттерны, идеи, форматы — чистое переписывание):**
- Схему и «запаковку» данных: JSON-данные + `_manifest` + `_template_*.json`.
- Поля `TradeGood`, модель reserved/target + `minReputation` + категории.
- Модель репутации (пороговая лестница → i18n-ключ) отдельно per-village и per-culture.
- Модель отношений (−100…+100, ступени, цвета).
- Квестовый DSL (`Quest`/`QuestStep`, теги, `chancePerHour`, награды).
- Модель стройки: план/уровень/вариант/подстроение/апгрейд + free-block list для стоимости.
- GoalScheduler-семантику (приоритет, время суток, cooldown, watchdog, `forceTask`).
- `SavedData`-версионирование + «рекорд жителя» как якорь.
- NeoForge-инфраструктуру: moddev-сборку, custom payloads, testframework, chunk ticket.
- naming/cloth/appearance/имягенез и детей → взрослые — как модель «жизненного цикла семьи».

**Адаптировать (придётся переписывать под наш чистое ядро):**
- Все NPC/AI (переместить планировщик в ядро, MC-движ в адаптер).
- Экономику: сундуки-инвентарь → чистые счета/товаропотоки в ядре + адаптер-прокси к сундукам.
- Локализацию/речь: оставить MC-side; в ядро — только ключи и уровни знания языков.
- Навигацию: адаптер (Minecraft Pathfinder); в ядре — только граф дорог/waypoints как данные.
- Строить данные: конвертнуть NBT-схемы (MC-зависимы) в ядре не хранить; схемы читает адаптер, ядру — только «состав/стоимость/статус».

**Избегать (не переносить):**
- Копирование `org.millenaire`-кода (GPL-3.0) и `com.jasoncian.millenaire_rewrite`-кода и `OldSource` (лицензия неоднозначна).
- Привязку семантики к `BuiltInRegistries.ITEM`-ID в JSON — у нас нейтральные «товарные ID» + адаптер-маппинг.
- Vanilla-`Brain`-вмешательство в NPC-логику (у Millénaire НЕ используется — и правильно).
- Спавнить NPC через случайные шаблоны — у нас поселения должны быть целенаправленными и наблюдаемыми через REST (лучше — явные события создания, не `chancePerHour`).
- Хранение всей деревни «одной кишкой» в одном SavedData (у них один блоб на всё) — для REST-управления лучше гранулярнее (по-деревне/по-жителю), чтобы ИИ читал подмножества. Но на старте допустимо как быстрый путь.

---

## Риски и лицензионные ограничения

1. **neoforge (`Leviaria/Millenaire`): LICENSE файл = GPL-3.0 (copyleft).** Прямое копирование кода/данных в наш проект недопустимо без открытия нашего кода под GPL. Только clean-room: изучение архитектуры → переписывание → свой авторский код. README заявляет, что оригинал Millénaire (Kinniken) и порт MoonCutter2B — MIT: **мифический** аргумент для копипаста — цепочка происхождения не проверена, комментариев в коде об MIT нет.
2. **Неоднозначность метаданных неофорджа:** `gradle.properties mod_license=All Rights Reserved` конфликтует с README (GPL-3.0) и LICENSE-файлом (GPL-3.0). Для копирования это worst-case → приравниваем к GPL-3.0/all-rights-reserved.
3. **`millenaire-rewrite`: LICENSE.txt = несменённый LGPL-2.1 MinecraftForge**, тогда как README/бейдж/gradle говорят MIT. Если суд по фактам — файл, который «поставляет» репо как лицензию, противоречит заявлению. До выяснения у автора трактовать как **all-rights-reserved/clean-room**: не копировать ни `com.jasoncian.*`, ни `reference/OldSource/*` (317 старых файлов + фордж-хак в `net/minecraft/`).
4. **`OldSource` содержит чужеродные классы** (`net/minecraft/block/BlockMillWallItem.java`) — этого точно нельзя трогать (проприетарные/фордж-патчи).
5. **Данные (JSON-культуры, текстуры, строи: сотни файлов)** формально производные от GPL-репо / неоднозначного репо. Даже «только данные, без кода» — копирование данных нарушит GPL-3.0. Можно использовать **наименования и контент-идеи** (средневековые культуры, набор ролей), но не сами ассеты/файлы.
6. **Очень много кода завязано на MC-типы** (`BuiltInRegistries`, `BlockState`, `StructureTemplate`) — даже при разрешённой лицензии это противоречит нашему «чистому ядру»: адаптация дорогая (оценка: 60–80% переписка).
7. **Порт не статус-релиз**: `9.0.0-dev-preview.7`, версии данных меняются (v4 мигратор), часть механик (Marvel, travel book) в процессе — опираться на их «стабильность» нельзя.
8. **Rewrite — почти пустой по целевым системам** (AI/эконом/стройка 10–30%): соблазн «взять готовое оттуда» не подтверждается кодом; ценность — только их документация.

---

### Резюме для нашего решения
- По стеку «под NeoForge 1.21.1» идеально совпадает **неофордж-порт**, но его код — **GPL-3.0** => переиспользуем PATTERN-NUR (сборка, события, payloads, SavedData-схема, JSON-контракты, семантика задач/торговли/репутации/квестов/стройки).
- `millenaire-rewrite` — только как источник **spec/документации** (китайские миграционные документы) идей старого Millénaire; код/данные не трогаем.
- Практический план: скопировать у неофорджа **архитектурные чертёжи** (таблицы/поля/схемы из этого документа), написать свой код в `core`/`neoforge`, дата-форматы культуры/зданий/квестов сделать как нейтральные JSON-контракты с масштабируемым `_manifest`.