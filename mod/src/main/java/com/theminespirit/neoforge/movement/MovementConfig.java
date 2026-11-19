package com.theminespirit.neoforge.movement;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class MovementConfig {
    public static final String FILE_NAME = "theminespirit-movement.toml";

    public static final int DEFAULT_GROUND_SEARCH_DEPTH = 4;
    public static final int DEFAULT_SURVEY_DEPTH = 2;
    public static final int DEFAULT_SWIM_DEPTH = 2;

    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue GROUND_SEARCH_DEPTH;
    private static final ModConfigSpec.IntValue SURVEY_DEPTH;
    private static final ModConfigSpec.IntValue SWIM_DEPTH;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("movement");
        GROUND_SEARCH_DEPTH = builder.comment(
                        "Глубина поиска земли под ногами, в блоках: дальше этого дух считает место провалом.")
                .defineInRange("groundSearchDepth", DEFAULT_GROUND_SEARCH_DEPTH, 1, 16);
        SURVEY_DEPTH = builder.comment("Вертикальный размах осмотра места при разведке, в блоках.")
                .defineInRange("surveyDepth", DEFAULT_SURVEY_DEPTH, 1, 8);
        SWIM_DEPTH = builder.comment("Глубина брода, по которой дух решает переходить воду вплавь, в блоках.")
                .defineInRange("swimDepth", DEFAULT_SWIM_DEPTH, 1, 8);
        builder.pop();
        SPEC = builder.build();
    }

    private MovementConfig() {}

    public static ModConfigSpec spec() {
        return SPEC;
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, spec(), FILE_NAME);
    }

    public static int groundSearchDepth() {
        return loaded(GROUND_SEARCH_DEPTH, DEFAULT_GROUND_SEARCH_DEPTH);
    }

    public static int surveyDepth() {
        return loaded(SURVEY_DEPTH, DEFAULT_SURVEY_DEPTH);
    }

    public static int swimDepth() {
        return loaded(SWIM_DEPTH, DEFAULT_SWIM_DEPTH);
    }

    private static int loaded(ModConfigSpec.IntValue value, int fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }
}
