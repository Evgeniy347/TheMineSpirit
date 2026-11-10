package com.theminespirit.core.script;

import com.theminespirit.core.husbandry.Species;
import java.util.List;

final class HuntScriptSpecs {
    private HuntScriptSpecs() {}

    private static final String RADIUS = "radius";
    private static final String SPECIES = "species";
    private static final String COUNT = "count";

    static List<ScriptSpec> huntingSpecs() {
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
                                ParamSpec.enumeration(SPECIES, false, CoreScriptValues.herdCheckValues(), Species.ALL),
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

    static List<ScriptSpec> fishingSpecs() {
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
                        List.of(
                                "в инвентаре есть удочка",
                                "в радиусе есть открытая вода не меньше " + CoreScriptValues.waterAreaText()),
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

    private static ScriptSpec spec(
            String id, String summary, List<ParamSpec> params, List<String> preconditions, List<String> effects) {
        return new ScriptSpec(id, summary, params, preconditions, effects);
    }
}
