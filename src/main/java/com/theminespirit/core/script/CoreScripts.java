package com.theminespirit.core.script;

import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.fishing.FishingData;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentDirection;
import com.theminespirit.core.mining.MiningTuning;
import com.theminespirit.core.mining.OreKind;
import com.theminespirit.core.mining.PickaxeTiers;
import com.theminespirit.core.mining.SafeMineMode;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.perception.MemoryType;
import java.util.ArrayList;
import java.util.List;

public final class CoreScripts {
    private static final String RADIUS = "radius";
    private static final String FILTER = "filter";
    private static final String SPECIES = "species";
    private static final String TARGET = "target";
    private static final String BLOCK = "block";
    private static final String COUNT = "count";
    private static final String NEAREST = "nearest";
    private static final String DIRECTION = "direction";
    private static final String CROP = "crop";
    private static final String ITEM = "item";
    private static final String CONTAINER = "container";

    private CoreScripts() {}

    public static ScriptCatalog defaultCatalog() {
        List<ScriptSpec> specs = new ArrayList<>();
        specs.addAll(perceptionSpecs());
        specs.addAll(memorySpecs());
        specs.addAll(travelSpecs());
        specs.addAll(navigationSpecs());
        specs.addAll(terrainSpecs());
        specs.addAll(waterAndFleeSpecs());
        specs.addAll(forestryAndMiningSpecs());
        specs.addAll(descentSpecs());
        specs.add(stripLogSpec());
        specs.addAll(gatheringSpecs());
        specs.addAll(cropCareSpecs());
        specs.addAll(breedingSpecs());
        specs.addAll(animalProduceSpecs());
        specs.addAll(huntingSpecs());
        specs.addAll(fishingSpecs());
        specs.addAll(itemSpecs());
        specs.add(acquireAtSpec());
        specs.addAll(combatAndClimbSpecs());
        specs.addAll(placementSpecs());
        specs.addAll(fillAndClearSpecs());
        specs.addAll(buildSpecs());
        specs.addAll(structureSpecs());
        specs.addAll(socialSpecs());
        specs.addAll(campSpecs());
        specs.addAll(interactionSpecs());
        specs.addAll(exchangeSpecs());
        return ScriptCatalog.of(specs);
    }

    private static List<ScriptSpec> perceptionSpecs() {
        return List.of(
                spec(
                        "scan",
                        "просканировать мир вокруг духа",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS, false, ScanRadius.MIN, ScanRadius.MAX, ScanRadius.DEFAULT.value()),
                                ParamSpec.textList(FILTER, false)),
                        List.of(),
                        List.of("обновлена карта блоков, сущностей и контейнеров")),
                spec(
                        "scan_blocks",
                        "просканировать только блоки вокруг духа",
                        List.of(perceptionRadius(), ParamSpec.textList(FILTER, false)),
                        List.of(),
                        List.of("блоки по фильтру в радиусе найдены, сущности и контейнеры не собирались")),
                spec(
                        "scan_entities",
                        "просканировать только сущности вокруг духа",
                        List.of(perceptionRadius(), ParamSpec.textList(FILTER, false)),
                        List.of(),
                        List.of("сущности по фильтру в радиусе найдены, блоки и контейнеры не собирались")),
                spec(
                        "sense_hazard",
                        "узнать об опасностях вокруг духа: лава, вода, обрыв",
                        List.of(perceptionRadius()),
                        List.of(),
                        List.of("факты об опасностях в радиусе найдены, дух не двигался")),
                spec(
                        "locate_block",
                        "найти ближайший блок по фильтру",
                        List.of(ParamSpec.textList(FILTER, true), perceptionRadius()),
                        List.of(),
                        List.of("позиция ближайшего блока найдена или сообщено, что блока нет")),
                spec(
                        "locate_tree",
                        "найти ближайшее дерево заданной породы",
                        List.of(
                                ParamSpec.enumeration(SPECIES, false, TreeSpecies.names(), TreeSpecies.ANY),
                                perceptionRadius()),
                        List.of(),
                        List.of("позиция ближайшего ствола найдена или сообщено, что дерева нет")),
                spec(
                        "locate_container",
                        "найти ближайший контейнер",
                        List.of(perceptionRadius()),
                        List.of(),
                        List.of("позиция ближайшего контейнера найдена или сообщено, что контейнера нет")),
                spec(
                        "look_at",
                        "посмотреть на клетку: блок, сущность, контейнер",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of(),
                        List.of("описание клетки получено, чанк не загружался")));
    }

    private static List<ScriptSpec> memorySpecs() {
        return List.of(
                spec(
                        "remember",
                        "запомнить точку под именем",
                        List.of(
                                ParamSpec.text("name", true),
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.enumeration("type", true, MemoryType.ids())),
                        List.of("в памяти есть место для точки этого типа"),
                        List.of("точка записана в память духа и переживёт перезапуск")),
                spec(
                        "recall",
                        "вспомнить точку по имени",
                        List.of(ParamSpec.text("name", true)),
                        List.of(),
                        List.of("точка найдена в памяти или сообщено, что её нет")),
                spec(
                        "recall_all",
                        "вспомнить все точки типа",
                        List.of(ParamSpec.enumeration("type", true, MemoryType.ids())),
                        List.of(),
                        List.of("список точек типа получен")),
                spec(
                        "forget",
                        "забыть точку по имени",
                        List.of(ParamSpec.text("name", true)),
                        List.of(),
                        List.of("точки с таким именем в памяти нет")));
    }

    private static List<ScriptSpec> travelSpecs() {
        return List.of(
                spec(
                        "goto",
                        "пройти к заданной точке",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("точка достижима"),
                        List.of("дух находится в точке")),
                spec(
                        "follow",
                        "следовать за целью на расстоянии",
                        List.of(
                                ParamSpec.text(TARGET, true),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.FOLLOW_RADIUS_MIN,
                                        CatalogLimits.FOLLOW_RADIUS_MAX,
                                        CatalogLimits.FOLLOW_RADIUS_DEFAULT)),
                        List.of("цель существует"),
                        List.of("дух держит дистанцию до цели")),
                spec(
                        "escort",
                        "сопровождать и охранять цель, ожидая рядом с ней",
                        List.of(
                                ParamSpec.text(TARGET, true),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.FOLLOW_RADIUS_MIN,
                                        CatalogLimits.FOLLOW_RADIUS_MAX,
                                        CatalogLimits.FOLLOW_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        "until",
                                        false,
                                        CatalogLimits.ESCORT_UNTIL_MIN,
                                        CatalogLimits.ESCORT_UNTIL_MAX,
                                        CatalogLimits.ESCORT_UNTIL_DEFAULT)),
                        List.of("цель существует"),
                        List.of("дух рядом с целью и защищает её тактикой обороны")),
                spec(
                        "return_home",
                        "вернуться домой (дом задаёт команда /spirit home)",
                        List.of(),
                        List.of("дом задан"),
                        List.of("дух дома")),
                spec(
                        "patrol",
                        "патрулировать область по маршруту",
                        List.of(
                                ParamSpec.textList("points", true),
                                ParamSpec.enumeration(
                                        "mode",
                                        false,
                                        List.of("loop", "ping_pong"),
                                        CatalogLimits.NAV_PATROL_MODE_DEFAULT),
                                ParamSpec.integer(
                                        "loops",
                                        false,
                                        CatalogLimits.NAV_PATROL_LOOPS_MIN,
                                        CatalogLimits.NAV_PATROL_LOOPS_MAX,
                                        CatalogLimits.NAV_PATROL_LOOPS_DEFAULT)),
                        List.of("маршрут задан"),
                        List.of("маршрут пройден")));
    }

    private static List<ScriptSpec> navigationSpecs() {
        return List.of(
                spec(
                        "goto_block",
                        "подойти к ближайшему блоку заданного вида",
                        List.of(
                                ParamSpec.text(BLOCK, true),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.NAV_RADIUS_MIN,
                                        CatalogLimits.NAV_RADIUS_MAX,
                                        CatalogLimits.NAV_RADIUS_DEFAULT),
                                ParamSpec.integer(COUNT, false, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX, 1)),
                        List.of("в радиусе есть подходящие блоки"),
                        List.of("дух в пределах досягаемости блока")),
                spec(
                        "come_to_player",
                        "подойти к игроку",
                        List.of(ParamSpec.text("player", false, NEAREST)),
                        List.of("игрок на сервере"),
                        List.of("дух рядом с игроком")),
                spec(
                        "wander",
                        "бродить вокруг старта",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.NAV_RADIUS_MIN,
                                        CatalogLimits.NAV_RADIUS_MAX,
                                        CatalogLimits.NAV_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        "duration_ticks",
                                        false,
                                        CatalogLimits.NAV_WANDER_DURATION_MIN,
                                        CatalogLimits.NAV_WANDER_DURATION_MAX,
                                        CatalogLimits.NAV_WANDER_DURATION_DEFAULT)),
                        List.of(),
                        List.of("время блуждания прошло")),
                spec(
                        "explore",
                        "обойти область по спирали и запомнить ресурсы и опасности",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.NAV_RADIUS_MIN,
                                        CatalogLimits.NAV_RADIUS_MAX,
                                        CatalogLimits.NAV_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        "budget",
                                        false,
                                        CatalogLimits.EXPLORE_BUDGET_MIN,
                                        CatalogLimits.EXPLORE_BUDGET_MAX,
                                        CatalogLimits.EXPLORE_BUDGET_DEFAULT)),
                        List.of("вход за границу поселения разрешён группой или игроком, ночью — нет"),
                        List.of("ресурсы и опасности записаны в память", "дух вернулся к точке старта")),
                spec(
                        "keep_distance",
                        "держать дистанцию до цели",
                        List.of(
                                ParamSpec.text(TARGET, true),
                                ParamSpec.integer(
                                        "distance",
                                        false,
                                        CatalogLimits.NAV_KEEP_DISTANCE_MIN,
                                        CatalogLimits.NAV_KEEP_DISTANCE_MAX,
                                        CatalogLimits.NAV_KEEP_DISTANCE_DEFAULT)),
                        List.of("цель существует"),
                        List.of("дух держит дистанцию до цели")));
    }

    private static List<ScriptSpec> terrainSpecs() {
        return List.of(
                spec(
                        "walk_careful",
                        "осторожно пройти к точке",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("точка достижима"),
                        List.of("дух находится в точке")),
                spec(
                        "step_over",
                        "шагнуть на соседнюю клетку",
                        List.of(ParamSpec.integer("dx", true, -1, 1), ParamSpec.integer("dz", true, -1, 1)),
                        List.of("соседняя клетка проходима"),
                        List.of("дух на соседней клетке")),
                spec(
                        "approach",
                        "подойти к цели на стоянку в досягаемости",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("к цели есть подход"),
                        List.of("дух на стоянке в досягаемости цели")),
                spec(
                        "swim",
                        "плыть к цели, не тонуть",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("цель за водой или на воде"),
                        List.of("дух переплыл к точке, не утонув")));
    }

    private static List<ScriptSpec> waterAndFleeSpecs() {
        return List.of(
                spec(
                        "cross_water",
                        "переправиться через воду вплавь (мост в переправе не строится)",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("между духом и целью есть вода"),
                        List.of("дух на другой стороне: переплыл узкую воду или перешёл по мосту")),
                spec(
                        "bridge",
                        "построить мост через провал или воду из блоков инвентаря",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer(
                                        "length",
                                        true,
                                        CatalogLimits.BRIDGE_LENGTH_MIN,
                                        CatalogLimits.BRIDGE_LENGTH_MAX),
                                ParamSpec.text(BLOCK, false, "")),
                        List.of("в инвентаре есть дешёвые блоки", "впереди нет лавы"),
                        List.of("мост поставлен, дух на другой стороне")),
                spec(
                        "flee",
                        "убежать от угрозы",
                        List.of(),
                        List.of("поблизости есть угроза"),
                        List.of("дух вне опасности")));
    }

    private static List<ScriptSpec> forestryAndMiningSpecs() {
        return List.of(
                spec(
                        "chop_tree",
                        "срубить деревья",
                        List.of(
                                ParamSpec.enumeration(SPECIES, false, TreeSpecies.names(), TreeSpecies.ANY),
                                ParamSpec.integer(COUNT, false, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX, 1),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.CHOP_RADIUS_MIN,
                                        CatalogLimits.CHOP_RADIUS_MAX,
                                        16),
                                ParamSpec.bool("replant", false, false)),
                        List.of("в радиусе есть подходящие брёвна"),
                        List.of("брёвна срублены", "предметы подобраны в инвентарь")),
                spec(
                        "chop_target",
                        "срубить конкретное дерево по координате ствола",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.bool("replant", false, false),
                                ParamSpec.bool(ChopTargetParams.ORCHARD, false, false)),
                        List.of("по координате есть ствол настоящего дерева"),
                        List.of("дерево срублено целиком", "предметы подобраны в инвентарь")),
                spec(
                        "mine_vein",
                        "добыть жилу руды",
                        List.of(
                                ParamSpec.enumeration("ore", false, OreKind.names(), "any"),
                                ParamSpec.integer(COUNT, false, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX, 1),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.HARVEST_RADIUS_MIN,
                                        CatalogLimits.HARVEST_RADIUS_MAX,
                                        CatalogLimits.HARVEST_RADIUS_DEFAULT)),
                        List.of("в радиусе есть подходящая жила"),
                        pickaxeForOre(),
                        List.of("руда добыта")),
                spec(
                        "dig_area",
                        "выкопать область",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer(
                                        "size", true, CatalogLimits.DIG_SIZE_MIN, CatalogLimits.DIG_SIZE_MAX)),
                        List.of(),
                        List.of("блоки области удалены")));
    }

    private static List<ScriptSpec> descentSpecs() {
        return List.of(
                spec(
                        "dig_descent",
                        "спуститься в шахту вниз по тоннелю",
                        List.of(
                                ParamSpec.enumeration(DIRECTION, true, DescentDirection.names()),
                                ParamSpec.integer(
                                        "y",
                                        false,
                                        CatalogLimits.WORLD_Y_MIN,
                                        CatalogLimits.WORLD_Y_MAX,
                                        DescentData.loadDefault().targetY())),
                        List.of("под ногами духа есть пол шахты"),
                        List.of(
                                "спуск пройден до целевого этажа или остановлен опасностью",
                                "тоннель освещен факелами")),
                spec(
                        "dig_descent_widen",
                        "расширить существующий спуск в шахту по всей длине",
                        List.of(
                                ParamSpec.enumeration(DIRECTION, true, DescentDirection.names()),
                                ParamSpec.integer(
                                        "y",
                                        false,
                                        CatalogLimits.WORLD_Y_MIN,
                                        CatalogLimits.WORLD_Y_MAX,
                                        DescentData.loadDefault().targetY())),
                        List.of("существует спуск уровня 1 шириной 1 и высотой 3"),
                        List.of(
                                "тоннель расширен до трёх блоков в ширину и шести в высоту по всей длине",
                                "факелы спуска сохранены")),
                spec(
                        "place_descent_stairs",
                        "поставить ступеньки в спуске шахты",
                        List.of(
                                ParamSpec.enumeration(DIRECTION, true, DescentDirection.names()),
                                ParamSpec.integer(
                                        "y",
                                        false,
                                        CatalogLimits.WORLD_Y_MIN,
                                        CatalogLimits.WORLD_Y_MAX,
                                        DescentData.loadDefault().targetY())),
                        List.of("спуск расширен до уровня 2", "в инвентаре есть ступеньки"),
                        List.of("на двух полосах спуска уложены ступеньки, третья свободна под рельсы")),
                spec(
                        "safe_mine",
                        "пройти безопасную шахту: спуск лесенкой или штольню с освещением",
                        List.of(
                                ParamSpec.enumeration("mode", true, SafeMineMode.names()),
                                ParamSpec.enumeration(DIRECTION, true, DescentDirection.names()),
                                ParamSpec.integer(
                                        "y",
                                        false,
                                        CatalogLimits.WORLD_Y_MIN,
                                        CatalogLimits.WORLD_Y_MAX,
                                        DescentData.loadDefault().targetY()),
                                ParamSpec.integer(
                                        "length",
                                        false,
                                        CatalogLimits.MINE_LENGTH_MIN,
                                        CatalogLimits.MINE_LENGTH_MAX,
                                        CatalogLimits.MINE_LENGTH_DEFAULT),
                                ParamSpec.integer(
                                        "torchInterval",
                                        false,
                                        MiningTuning.DESCENT_TORCH_INTERVAL_MIN,
                                        MiningTuning.DESCENT_TORCH_INTERVAL_MAX,
                                        DescentData.loadDefault().torchInterval())),
                        List.of("под ногами духа есть твёрдый пол"),
                        List.of(
                                "проход 1×2 пройден до целевого этажа или заданной длины",
                                "опасные клетки заложены блоком из инвентаря, сыпучее осыпано и выкопано",
                                "проход освещён факелами через интервал")));
    }

    private static ScriptSpec stripLogSpec() {
        return spec(
                "strip_log",
                "обтесать брёвна топором и забрать обтёсанные",
                List.of(
                        ParamSpec.enumeration(
                                SPECIES,
                                true,
                                TreeSpecies.ALL.stream().map(TreeSpecies::id).toList()),
                        ParamSpec.integer(COUNT, false, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX, 1),
                        ParamSpec.integer(
                                RADIUS,
                                false,
                                CatalogLimits.CHOP_RADIUS_MIN,
                                CatalogLimits.CHOP_RADIUS_MAX,
                                CatalogLimits.HARVEST_RADIUS_DEFAULT)),
                List.of("в инвентаре есть топор", "в радиусе есть брёвна породы"),
                List.of("брёвна обтёсаны", "обтёсанные брёвна подобраны в инвентарь"));
    }

    private static List<ScriptSpec> gatheringSpecs() {
        return List.of(
                spec(
                        "gather",
                        "собрать блоки заданного типа",
                        List.of(
                                ParamSpec.text(BLOCK, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.HARVEST_RADIUS_MIN,
                                        CatalogLimits.HARVEST_RADIUS_MAX,
                                        CatalogLimits.HARVEST_RADIUS_DEFAULT)),
                        List.of("в радиусе есть подходящие блоки"),
                        pickaxeForBlock(),
                        List.of("блоки собраны")),
                spec(
                        "collect_drops",
                        "подобрать выпавшие предметы вокруг духа",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.DROP_RADIUS_MIN,
                                        CatalogLimits.DROP_RADIUS_MAX,
                                        CatalogLimits.DROP_RADIUS_DEFAULT),
                                ParamSpec.textList(FILTER, false),
                                ParamSpec.enumeration("overflow", false, List.of("fail", "by_value"), "by_value")),
                        List.of("в инвентаре есть место"),
                        List.of(
                                "предметы по фильтру в радиусе подобраны",
                                "предметов по фильтру в радиусе не осталось")),
                spec(
                        "harvest_crop",
                        "собрать урожай",
                        List.of(
                                ParamSpec.enumeration(CROP, false, CropKind.names(), "any"),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.FARM_RADIUS_MIN,
                                        CatalogLimits.FARM_RADIUS_MAX,
                                        CatalogLimits.FARM_RADIUS_DEFAULT),
                                ParamSpec.bool("replant", false, true)),
                        List.of("посевы созрели"),
                        List.of("урожай собран")),
                spec(
                        "plant_crop",
                        "посадить культуру",
                        List.of(
                                ParamSpec.enumeration(CROP, true, CropKind.plantableNames()),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.FARM_RADIUS_MIN,
                                        CatalogLimits.FARM_RADIUS_MAX,
                                        CatalogLimits.FARM_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        COUNT,
                                        false,
                                        CatalogLimits.COUNT_MIN,
                                        CatalogLimits.COUNT_MAX,
                                        CatalogLimits.PLANT_COUNT_DEFAULT)),
                        List.of("есть семена", "есть подготовленная земля"),
                        List.of("культура посажена")));
    }

    private static List<ScriptSpec> cropCareSpecs() {
        return List.of(
                spec(
                        "till",
                        "вспахать землю под грядку",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.FARM_RADIUS_MIN,
                                        CatalogLimits.FARM_RADIUS_MAX,
                                        CatalogLimits.FARM_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        COUNT,
                                        false,
                                        CatalogLimits.COUNT_MIN,
                                        CatalogLimits.COUNT_MAX,
                                        CatalogLimits.TILL_COUNT_DEFAULT)),
                        List.of("есть мотыга", "рядом есть вода"),
                        List.of("земля вспахана")),
                spec(
                        "bone_meal",
                        "подкормить посевы костной мукой",
                        List.of(
                                ParamSpec.enumeration(CROP, false, CropKind.names(), "any"),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.FARM_RADIUS_MIN,
                                        CatalogLimits.FARM_RADIUS_MAX,
                                        CatalogLimits.FARM_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        COUNT,
                                        false,
                                        CatalogLimits.COUNT_MIN,
                                        CatalogLimits.COUNT_MAX,
                                        CatalogLimits.BONE_MEAL_COUNT_DEFAULT)),
                        List.of("есть костная мука", "посевы растут"),
                        List.of("посевы подкормлены")));
    }

    private static List<ScriptSpec> breedingSpecs() {
        return List.of(
                spec(
                        "breed",
                        "свести пару животных",
                        List.of(
                                ParamSpec.enumeration(SPECIES, true, Species.names()),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.HERD_RADIUS_MIN,
                                        CatalogLimits.HERD_RADIUS_MAX,
                                        CatalogLimits.HERD_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        "pairs",
                                        false,
                                        CatalogLimits.HERD_PAIRS_MIN,
                                        CatalogLimits.HERD_PAIRS_MAX,
                                        CatalogLimits.HERD_PAIRS_DEFAULT)),
                        List.of("есть корм вида", "в загоне есть готовая пара"),
                        List.of("пары сведены", "детёныши родились")),
                spec(
                        "feed",
                        "покормить стадо",
                        List.of(
                                ParamSpec.enumeration(SPECIES, true, Species.names()),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.HERD_RADIUS_MIN,
                                        CatalogLimits.HERD_RADIUS_MAX,
                                        CatalogLimits.HERD_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        COUNT,
                                        false,
                                        CatalogLimits.HERD_COUNT_MIN,
                                        CatalogLimits.HERD_COUNT_MAX,
                                        CatalogLimits.HERD_FEED_COUNT_DEFAULT),
                                ParamSpec.bool("babiesFirst", false, true)),
                        List.of("есть корм вида"),
                        List.of("головы покормлены", "отбившиеся возвращены")),
                spec(
                        "shear",
                        "постричь овец",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.HERD_RADIUS_MIN,
                                        CatalogLimits.HERD_RADIUS_MAX,
                                        CatalogLimits.HERD_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        COUNT,
                                        false,
                                        CatalogLimits.HERD_COUNT_MIN,
                                        CatalogLimits.HERD_COUNT_MAX,
                                        CatalogLimits.HERD_WORK_COUNT_DEFAULT)),
                        List.of("есть ножницы", "овцы с шерстью"),
                        List.of("овцы пострижены", "шерсть подобрана")));
    }

    private static List<ScriptSpec> animalProduceSpecs() {
        return List.of(
                spec(
                        "milk",
                        "подоить коров",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.HERD_RADIUS_MIN,
                                        CatalogLimits.HERD_RADIUS_MAX,
                                        CatalogLimits.HERD_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        COUNT,
                                        false,
                                        CatalogLimits.HERD_COUNT_MIN,
                                        CatalogLimits.HERD_COUNT_MAX,
                                        CatalogLimits.HERD_WORK_COUNT_DEFAULT)),
                        List.of("есть пустые вёдра"),
                        List.of("коровы подоены")),
                spec(
                        "collect_eggs",
                        "собрать яйца в загоне",
                        List.of(ParamSpec.integer(
                                RADIUS,
                                false,
                                CatalogLimits.HERD_RADIUS_MIN,
                                CatalogLimits.HERD_RADIUS_MAX,
                                CatalogLimits.HERD_RADIUS_DEFAULT)),
                        List.of(),
                        List.of("яйца в инвентаре")),
                spec(
                        "harvest_hive",
                        "собрать соты или мёд из полного улья",
                        List.of(
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.HERD_RADIUS_MIN,
                                        CatalogLimits.HERD_RADIUS_MAX,
                                        CatalogLimits.HERD_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        COUNT,
                                        false,
                                        CatalogLimits.HERD_COUNT_MIN,
                                        CatalogLimits.HERD_COUNT_MAX,
                                        CatalogLimits.HIVE_COUNT_DEFAULT)),
                        List.of("есть ножницы или стеклянная бутылка", "под ульем костёр или материалы для него"),
                        List.of("соты или мёд в инвентаре", "пчёлы целы")));
    }

    private static List<ScriptSpec> huntingSpecs() {
        return List.of(
                spec(
                        "cull",
                        "забить лишних голов",
                        List.of(
                                ParamSpec.enumeration(SPECIES, true, Species.names()),
                                ParamSpec.integer(
                                        "keep",
                                        false,
                                        CatalogLimits.HERD_KEEP_MIN,
                                        CatalogLimits.HERD_KEEP_MAX,
                                        CatalogLimits.HERD_KEEP_DEFAULT_CATTLE),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.HERD_RADIUS_MIN,
                                        CatalogLimits.HERD_RADIUS_MAX,
                                        CatalogLimits.HERD_RADIUS_DEFAULT)),
                        List.of("меч железный и выше"),
                        List.of("лишние забиты", "детёныши целы")),
                spec(
                        "herd_check",
                        "сосчитать стадо",
                        List.of(
                                ParamSpec.enumeration(SPECIES, false, herdCheckValues(), Species.ALL),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.HERD_RADIUS_MIN,
                                        CatalogLimits.HERD_RADIUS_MAX,
                                        CatalogLimits.HERD_RADIUS_DEFAULT)),
                        List.of(),
                        List.of("взрослые, детёныши и вышедшие сосчитаны")),
                spec(
                        "hunt",
                        "забить диких животных",
                        List.of(
                                ParamSpec.enumeration(SPECIES, true, Species.names()),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.HERD_HUNT_RADIUS_MIN,
                                        CatalogLimits.HERD_HUNT_RADIUS_MAX,
                                        CatalogLimits.HERD_HUNT_RADIUS_DEFAULT),
                                ParamSpec.integer(
                                        COUNT,
                                        false,
                                        CatalogLimits.HERD_COUNT_MIN,
                                        CatalogLimits.HERD_COUNT_MAX,
                                        CatalogLimits.HERD_WORK_COUNT_DEFAULT)),
                        List.of("меч железный и выше", "дичь вне загона"),
                        List.of("дикие забиты", "дроп подобран")),
                spec(
                        "capture",
                        "привести животное приманкой (корм в инвентаре) в загон у дома",
                        List.of(
                                ParamSpec.enumeration(SPECIES, true, Species.names()),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.CAPTURE_RADIUS_MIN,
                                        CatalogLimits.CAPTURE_RADIUS_MAX,
                                        CatalogLimits.CAPTURE_RADIUS_DEFAULT)),
                        List.of("в радиусе есть животное вида", "в инвентаре есть дешёвые блоки"),
                        List.of("животное внутри кольца", "слабое место наращено")));
    }

    private static List<ScriptSpec> fishingSpecs() {
        return List.of(
                spec(
                        "fish",
                        "ловить рыбу удочкой в открытой воде",
                        List.of(
                                ParamSpec.integer(
                                        COUNT,
                                        false,
                                        CatalogLimits.COUNT_MIN,
                                        CatalogLimits.COUNT_MAX,
                                        CatalogLimits.FISH_COUNT_DEFAULT),
                                ParamSpec.integer(
                                        "duration_ticks",
                                        false,
                                        CatalogLimits.FISH_DURATION_MIN,
                                        CatalogLimits.FISH_DURATION_MAX,
                                        CatalogLimits.FISH_DURATION_DEFAULT),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.FISH_RADIUS_MIN,
                                        CatalogLimits.FISH_RADIUS_MAX,
                                        CatalogLimits.FISH_RADIUS_DEFAULT)),
                        List.of("в инвентаре есть удочка", "в радиусе есть открытая вода не меньше " + waterAreaText()),
                        List.of(
                                "рыба поймана по таблице добычи с учётом зачарований удочки",
                                "прочность удочки потрачена")),
                spec(
                        "bucket_fish",
                        "поймать рыбу ведром в открытой воде",
                        List.of(
                                ParamSpec.integer(
                                        COUNT,
                                        false,
                                        CatalogLimits.COUNT_MIN,
                                        CatalogLimits.COUNT_MAX,
                                        CatalogLimits.FISH_COUNT_DEFAULT),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.FISH_RADIUS_MIN,
                                        CatalogLimits.FISH_RADIUS_MAX,
                                        CatalogLimits.FISH_RADIUS_DEFAULT)),
                        List.of("в инвентаре есть пустое ведро", "в радиусе есть рыба в воде"),
                        List.of("рыба в вёдрах", "поймано не больше предела из данных")));
    }

    private static List<ScriptSpec> itemSpecs() {
        return List.of(
                spec(
                        "craft",
                        "скрафтить предмет",
                        List.of(
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                stationRadius(),
                                ParamSpec.bool("place", false, false)),
                        List.of("есть материалы и рецепт"),
                        List.of("предмет скрафчен")),
                spec(
                        "smelt",
                        "переплавить предмет",
                        List.of(
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                stationRadius()),
                        List.of("есть печь и топливо"),
                        List.of("предмет переплавлен")),
                spec(
                        "equip",
                        "надеть предмет",
                        List.of(ParamSpec.text(ITEM, true)),
                        List.of("предмет есть в инвентаре"),
                        List.of("предмет надет")),
                spec(
                        "deposit",
                        "положить предметы в контейнер",
                        List.of(
                                ParamSpec.text(CONTAINER, false, NEAREST),
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                containerRadius(),
                                ParamSpec.bool("leftover", false, false)),
                        List.of("контейнер доступен"),
                        List.of("предметы в контейнере", "контейнер отсортирован")),
                spec(
                        "withdraw",
                        "взять предметы из контейнера",
                        List.of(
                                ParamSpec.text(CONTAINER, false, NEAREST),
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                containerRadius()),
                        List.of("контейнер доступен"),
                        List.of("предметы в инвентаре", "контейнер отсортирован")),
                spec(
                        "eat",
                        "съесть еду из инвентаря (без item — лучшее по питательности)",
                        List.of(ParamSpec.text(ITEM, false)),
                        List.of("в инвентаре есть еда"),
                        List.of("сытость духа выросла", "съеденное убрано из инвентаря")),
                spec(
                        "sort_chest",
                        "рассортировать содержимое сундука",
                        List.of(ParamSpec.text(CONTAINER, false, NEAREST), containerRadius()),
                        List.of("контейнер доступен"),
                        List.of("содержимое упорядочено")),
                spec(
                        "unload",
                        "разгрузить лишнее на базе",
                        List.of(
                                ParamSpec.text(CONTAINER, false, "auto"),
                                containerRadius(),
                                ParamSpec.bool("force", false, false)),
                        List.of("рядом есть допустимый контейнер"),
                        List.of("лишнее разложено по контейнерам", "базовый набор остался при духе")),
                spec(
                        "acquire",
                        "добыть предмет любым способом",
                        List.of(
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                ParamSpec.bool("fresh", false, false)),
                        List.of(),
                        List.of("предмет есть в инвентаре")));
    }

    private static ScriptSpec acquireAtSpec() {
        return spec(
                "acquire_at",
                "иметь предмет в инвентаре; не хватает — добыть у заданного места (земля подсыпки за оградой)",
                List.of(
                        ParamSpec.text(ITEM, true),
                        ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                        ParamSpec.integer(
                                "x", true, -CatalogLimits.WORLD_HORIZONTAL_LIMIT, CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                        ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                        ParamSpec.integer(
                                "z",
                                true,
                                -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                List.of(),
                List.of("предмет есть в инвентаре"));
    }

    private static List<ScriptSpec> combatAndClimbSpecs() {
        return List.of(
                spec(
                        "attack",
                        "атаковать цель",
                        List.of(ParamSpec.text(TARGET, false, "hostile")),
                        List.of("цель видима"),
                        List.of("цель атакована")),
                spec(
                        "defend",
                        "защитить цель",
                        List.of(ParamSpec.text(TARGET, true)),
                        List.of("цель существует"),
                        List.of("цель защищена")),
                spec("retreat", "отступить", List.of(), List.of("есть путь отхода"), List.of("дух отступил")),
                spec(
                        "disengage",
                        "выйти из боя: отойти на дистанцию и прекратить бой",
                        List.of(
                                ParamSpec.text(TARGET, false, DisengageParams.DEFAULT_TARGET),
                                ParamSpec.integer(
                                        "distance",
                                        false,
                                        CatalogLimits.DISENGAGE_DISTANCE_MIN,
                                        CatalogLimits.DISENGAGE_DISTANCE_MAX,
                                        CatalogLimits.DISENGAGE_DISTANCE_DEFAULT)),
                        List.of("рядом есть с кем воевать или бой уже идёт"),
                        List.of("дух отошёл на дистанцию и больше не атакует")),
                spec(
                        "pillar_up",
                        "подняться столбом",
                        List.of(
                                ParamSpec.integer("height", true, 1, CatalogLimits.COUNT_MAX, 1),
                                ParamSpec.text(BLOCK, false, ""),
                                ParamSpec.bool("down", false, false)),
                        List.of("есть материал для блоков"),
                        List.of("дух поднялся на высоту")),
                spec(
                        "dig_out",
                        "выбраться из-под земли: лестница вверх (ступень 1, проход 2) в сторону точки",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("дух замурован: воздуха к поверхности нет"),
                        List.of("дух под открытым небом или на высоте точки")),
                spec(
                        "pillar_down",
                        "разобрать свой столб и вернуть блоки",
                        List.of(),
                        List.of("под духом есть свой столб"),
                        List.of("блоки столба вернулись в инвентарь")),
                spec(
                        "climb",
                        "влезть по лестнице, лиане или строительным лесам вверх или вниз",
                        List.of(ParamSpec.bool("down", false, false)),
                        List.of("рядом есть колонна из лестниц, лиан или строительных лесов"),
                        List.of("дух дошёл до конца колонны: вверх — до верхней ступени, вниз — до нижней")));
    }

    private static List<ScriptSpec> placementSpecs() {
        return List.of(
                spec(
                        "place_block",
                        "поставить блок",
                        List.of(
                                ParamSpec.text(BLOCK, true),
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("место свободно"),
                        List.of("блок поставлен")),
                spec(
                        "light_area",
                        "осветить территорию факелами",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.LIGHT_RADIUS_MIN,
                                        CatalogLimits.LIGHT_RADIUS_MAX,
                                        CatalogLimits.LIGHT_RADIUS_DEFAULT)),
                        List.of("в области есть куда ставить факелы", "в инвентаре есть факелы"),
                        List.of("на земле не осталось тёмных клеток", "итог называет, сколько факелов не хватило")));
    }

    private static List<ScriptSpec> fillAndClearSpecs() {
        return List.of(
                spec(
                        "fill",
                        "заполнить область блоком",
                        List.of(
                                ParamSpec.text(BLOCK, true),
                                ParamSpec.integer(
                                        "x1",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y1", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z1",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer(
                                        "x2",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y2", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z2",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of(),
                        List.of("область заполнена")),
                spec(
                        "clear_area",
                        "очистить область",
                        List.of(
                                ParamSpec.integer(
                                        "x1",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y1", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z1",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer(
                                        "x2",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y2", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z2",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.bool("demolish", false, false),
                                ParamSpec.bool("keep_ground", false, false)),
                        List.of(),
                        List.of("область очищена")));
    }

    private static List<ScriptSpec> buildSpecs() {
        return List.of(
                spec(
                        "build_schematic",
                        "построить схему из файла",
                        List.of(
                                ParamSpec.text("file", true),
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.enumeration(
                                        "rotation",
                                        false,
                                        List.of("none", "clockwise_90", "clockwise_180", "counterclockwise_90"),
                                        "none"),
                                ParamSpec.integer(
                                        "segments",
                                        false,
                                        BuildSchematicParams.SEGMENTS_FROM_FILE,
                                        BuildSchematicParams.SEGMENTS_MAX,
                                        BuildSchematicParams.SEGMENTS_FROM_FILE),
                                ParamSpec.enumeration(
                                        BuildSchematicParams.WOOD, false, WoodSpecies.ALL, WoodSpecies.OAK)),
                        List.of("файл схемы существует"),
                        List.of("схема построена")),
                spec(
                        "build_farm",
                        "разбить грядку прямоугольником: вспахать землю у воды и посадить культуру",
                        List.of(
                                ParamSpec.enumeration(CROP, true, CropKind.plotNames()),
                                ParamSpec.integer(
                                        "x1",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer(
                                        "z1",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer(
                                        "x2",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer(
                                        "z2",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("есть мотыга", "рядом с прямоугольником есть вода", "есть семена культуры"),
                        List.of("земля прямоугольника вспахана там, где хватает воды", "культура посажена")));
    }

    private static List<ScriptSpec> structureSpecs() {
        return List.of(
                spec(
                        "build_fence",
                        "огородить контур забором с калиткой",
                        List.of(
                                ParamSpec.text(BLOCK, true),
                                ParamSpec.text("gate", false, ""),
                                ParamSpec.textList("points", true)),
                        List.of("в инвентаре есть блоки забора и калитки"),
                        List.of("забор стоит по контуру", "в калитке проход")),
                spec(
                        "build_shelter",
                        "построить укрытие 6×6 по схеме из дешёвого материала",
                        List.of(),
                        List.of("в инвентаре полный запас булыжника или досок, полублок порога и факел"),
                        List.of(
                                "вокруг духа стоит укрытие с проёмом входа, крышей и светом",
                                "дух выходит из укрытия после стройки")),
                spec(
                        "fill_crater",
                        "засыпать воронку от взрыва ровно до уровня земли",
                        List.of(ParamSpec.text("crater", true)),
                        List.of(
                                "воронка задана id или позицией и запомнена событием взрыва",
                                "в инвентаре или ближнем сундуке есть земля"),
                        List.of("пустоты воронки засыпаны до уровня земли", "итог называет засыпанное и нехватку")));
    }

    private static List<ScriptSpec> socialSpecs() {
        return List.of(
                spec(
                        "deliver",
                        "доставить предмет адресату",
                        List.of(
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                ParamSpec.text("to", true)),
                        List.of("адресат существует"),
                        List.of("предмет доставлен")),
                spec(
                        "meet",
                        "встретиться с целью",
                        List.of(ParamSpec.text(TARGET, true)),
                        List.of("цель существует"),
                        List.of("дух рядом с целью")),
                spec(
                        "say",
                        "произнести сообщение",
                        List.of(ParamSpec.text("text", true)),
                        List.of(),
                        List.of("сообщение отправлено")),
                spec(
                        "wait",
                        "ждать заданное число тиков",
                        List.of(ParamSpec.integer(
                                "ticks", true, CatalogLimits.WAIT_TICKS_MIN, CatalogLimits.WAIT_TICKS_MAX)),
                        List.of(),
                        List.of("время ожидания прошло")),
                spec(
                        "sleep",
                        "лечь спать в кровать",
                        List.of(),
                        List.of("ночь, дух внутри поселения и не в обороне", "в поселении есть свободная кровать"),
                        List.of("дух спит в кровати и просыпается утром, при угрозе или уроне")));
    }

    private static List<ScriptSpec> campSpecs() {
        return List.of(
                spec(
                        "close_door",
                        "подойти к двери и закрыть её",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("дверь существует", "к двери есть подход"),
                        List.of("дверь закрыта")),
                spec(
                        "set_camp",
                        "поставить лагерь похода",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        false,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", false, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        false,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer(
                                        RADIUS,
                                        false,
                                        CatalogLimits.CAMP_RADIUS_MIN,
                                        CatalogLimits.CAMP_RADIUS_MAX,
                                        CatalogLimits.CAMP_RADIUS_DEFAULT)),
                        List.of(),
                        List.of("лагерь поставлен и переживёт перезапуск")),
                spec("clear_camp", "снять лагерь похода", List.of(), List.of(), List.of("лагеря у духа нет")),
                spec(
                        "repair_tool",
                        "починить инструмент: материалом на наковальне или двумя одинаковыми "
                                + "повреждёнными в точиле (дешевле по опыту и материалу)",
                        List.of(ParamSpec.text(ITEM, true)),
                        List.of(
                                "в инвентаре есть повреждённый предмет; есть материал ремонта или второй такой же",
                                "для ремонта материалом рядом есть наковальня"),
                        List.of("предмет починен: наковальня добавляет 25% максимума за материал, "
                                + "точило — сумму прочности + 5% и теряет зачарования")));
    }

    private static List<ScriptSpec> interactionSpecs() {
        return List.of(
                spec(
                        "use_block",
                        "нажать или переключить блок (рычаг, кнопка, дверь)",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("в клетке есть нажимаемый блок (рычаг, кнопка, дверь)", "к блоку есть подход"),
                        List.of("блок нажат или переключён", "чужой механизм в постройке игрока не трогается")),
                spec(
                        "open",
                        "открыть дверь, люк, калитку или сундук",
                        List.of(
                                ParamSpec.integer(
                                        "x",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT),
                                ParamSpec.integer("y", true, CatalogLimits.WORLD_Y_MIN, CatalogLimits.WORLD_Y_MAX),
                                ParamSpec.integer(
                                        "z",
                                        true,
                                        -CatalogLimits.WORLD_HORIZONTAL_LIMIT,
                                        CatalogLimits.WORLD_HORIZONTAL_LIMIT)),
                        List.of("в клетке есть дверь, люк, калитка или сундук", "к блоку есть подход"),
                        List.of("дверь, люк или калитка открыты", "сундук открыт и закрыт")),
                spec(
                        "set_combat_mode",
                        "сменить режим тактики духа",
                        List.of(ParamSpec.enumeration("mode", true, SetCombatModeParams.MODE_IDS)),
                        List.of(),
                        List.of("режим боя духа сменён", "смена записана в журнал решений тактики")),
                spec(
                        "drop_to_player",
                        "бросить предмет к игроку или духу и оставить его на земле рядом",
                        List.of(
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                ParamSpec.text("to", true)),
                        List.of("адресат существует", "предмета хватает в инвентаре"),
                        List.of("предмет выпал рядом с адресатом")));
    }

    private static List<ScriptSpec> exchangeSpecs() {
        return List.of(
                spec(
                        "give",
                        "передать предмет игроку или духу (то же, что deliver)",
                        List.of(
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                ParamSpec.text("to", true)),
                        List.of("адресат существует", "предмета хватает в инвентаре"),
                        List.of("предмет передан адресату")),
                spec(
                        "take",
                        "взять предмет у игрока или духа в свой инвентарь",
                        List.of(
                                ParamSpec.text(ITEM, true),
                                ParamSpec.integer(COUNT, true, CatalogLimits.COUNT_MIN, CatalogLimits.COUNT_MAX),
                                ParamSpec.text("from", true)),
                        List.of("источник существует", "у источника хватает предмета", "в инвентаре духа есть место"),
                        List.of("предмет перешёл к духу")),
                spec(
                        "report",
                        "доложить о состоянии в чат: задача, инвентарь, здоровье, где находится",
                        List.of(ParamSpec.text("to", false, "")),
                        List.of(),
                        List.of("доклад отправлен игроку или всем в радиусе слышимости")),
                spec(
                        "swap_tool",
                        "сменить инструмент в руке на лучший для блока",
                        List.of(ParamSpec.text(BLOCK, true)),
                        List.of("блок зарегистрирован", "в руке или инвентаре есть подходящий инструмент"),
                        List.of("в руке лучший целый инструмент для блока")),
                spec(
                        "drink_potion",
                        "выпить зелье из инвентаря по типу",
                        List.of(ParamSpec.text("type", true)),
                        List.of("в инвентаре есть зелье этого типа"),
                        List.of("эффекты зелья применены", "зелье убрано из инвентаря")));
    }

    private static ScriptSpec spec(
            String id, String summary, List<ParamSpec> params, List<String> preconditions, List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects);
    }

    private static ScriptSpec spec(
            String id,
            String summary,
            List<ParamSpec> params,
            List<String> preconditions,
            List<ScriptRequirement> requires,
            List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects, requires);
    }

    private static List<ScriptRequirement> pickaxeForOre() {
        return List.of(ScriptRequirement.pickaxe("ore", PickaxeTiers.oreTiers(), 1));
    }

    private static List<ScriptRequirement> pickaxeForBlock() {
        return List.of(ScriptRequirement.pickaxe(BLOCK, PickaxeTiers.blockTiers(), ScriptRequirement.NO_TIER));
    }

    private static ParamSpec perceptionRadius() {
        return ParamSpec.integer(
                RADIUS,
                false,
                CatalogLimits.PERCEPTION_RADIUS_MIN,
                CatalogLimits.PERCEPTION_RADIUS_MAX,
                CatalogLimits.PERCEPTION_RADIUS_DEFAULT);
    }

    private static List<String> herdCheckValues() {
        List<String> values = new ArrayList<>(Species.names());
        values.add(Species.ALL);
        return List.copyOf(values);
    }

    private static ParamSpec stationRadius() {
        return ParamSpec.integer(
                RADIUS,
                false,
                CatalogLimits.STATION_RADIUS_MIN,
                CatalogLimits.STATION_RADIUS_MAX,
                CatalogLimits.STATION_RADIUS_DEFAULT);
    }

    private static ParamSpec containerRadius() {
        return ParamSpec.integer(
                RADIUS,
                false,
                CatalogLimits.CONTAINER_RADIUS_MIN,
                CatalogLimits.CONTAINER_RADIUS_MAX,
                CatalogLimits.CONTAINER_RADIUS_DEFAULT);
    }

    private static String waterAreaText() {
        FishingData data = FishingData.loadDefault();
        return data.minWidth() + "x" + data.minWidth() + " глубиной " + data.minDepth();
    }
}
