package com.theminespirit.core.script;

import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentDirection;
import com.theminespirit.core.mining.MiningTuning;
import com.theminespirit.core.mining.OreKind;
import com.theminespirit.core.mining.PickaxeTiers;
import com.theminespirit.core.mining.SafeMineMode;
import java.util.List;

final class ResourceScriptSpecs {
    private ResourceScriptSpecs() {}

    private static final String RADIUS = "radius";
    private static final String SPECIES = "species";
    private static final String COUNT = "count";
    private static final String DIRECTION = "direction";

    static List<ScriptSpec> forestryAndMiningSpecs() {
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
                        List.of(ScriptRequirement.pickaxe("ore", PickaxeTiers.oreTiers(), 1)),
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

    static List<ScriptSpec> descentSpecs() {
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
}
