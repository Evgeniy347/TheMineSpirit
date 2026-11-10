package com.theminespirit.core.script;

import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.husbandry.Species;
import java.util.List;

final class FarmScriptSpecs {
    private FarmScriptSpecs() {}

    private static final String RADIUS = "radius";
    private static final String SPECIES = "species";
    private static final String COUNT = "count";

    static List<ScriptSpec> cropCareSpecs() {
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
                                ParamSpec.enumeration("crop", false, CropKind.names(), "any"),
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

    static List<ScriptSpec> breedingSpecs() {
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

    private static ScriptSpec spec(
            String id, String summary, List<ParamSpec> params, List<String> preconditions, List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects);
    }
}
