package com.theminespirit.neoforge;

import com.theminespirit.core.chunk.ChunkLoadingRadius;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class SpiritConfig {
    public static final int AUTO_CHUNK_LOADING_RADIUS = ChunkLoadingRadius.AUTO;
    public static final int MAX_CHUNK_LOADING_RADIUS = 32;

    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue CHUNK_LOADING_RADIUS;
    private static final ModConfigSpec.BooleanValue MOBS_TARGET_SPIRITS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("spirit");
        CHUNK_LOADING_RADIUS = builder
                .comment("Радиус квадрата принудительно загружаемых чанков вокруг духа, в чанках.",
                        "-1 — как simulation distance сервера: дух держит столько же, сколько игрок.")
                .defineInRange("chunkLoadingRadius", AUTO_CHUNK_LOADING_RADIUS, AUTO_CHUNK_LOADING_RADIUS,
                        MAX_CHUNK_LOADING_RADIUS);
        MOBS_TARGET_SPIRITS = builder
                .comment("Добавлять враждебным мобам цель «дух», чтобы они сами нападали на духов.",
                        "false — мобы смотрят только на игроков, как в ванили.")
                .define("mobsTargetSpirits", true);
        builder.pop();
        SPEC = builder.build();
    }

    private SpiritConfig() {
    }

    public static ModConfigSpec spec() {
        return SPEC;
    }

    public static boolean mobsTargetSpirits() {
        return MOBS_TARGET_SPIRITS.get();
    }

    public static void setMobsTargetSpirits(boolean value) {
        MOBS_TARGET_SPIRITS.set(value);
    }

    public static int radiusFor(ServerLevel level) {
        return ChunkLoadingRadius.effective(
                CHUNK_LOADING_RADIUS.get(), level.getServer().getPlayerList().getSimulationDistance());
    }
}