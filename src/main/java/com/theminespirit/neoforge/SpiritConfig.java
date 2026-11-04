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
    private static final ModConfigSpec.BooleanValue COORDINATOR_ENABLED;
    private static final ModConfigSpec.BooleanValue BOARD_ENABLED;
    private static final ModConfigSpec.IntValue CRATER_HOME_RADIUS;
    private static final ModConfigSpec.IntValue NAV_CHUNK_BUDGET;
    private static final ModConfigSpec.IntValue NAV_CHUNK_PER_SECOND;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("spirit");
        CHUNK_LOADING_RADIUS = builder.comment(
                        "Радиус квадрата принудительно загружаемых чанков вокруг духа, в чанках.",
                        "-1 — как simulation distance сервера: дух держит столько же, сколько игрок.")
                .defineInRange(
                        "chunkLoadingRadius",
                        AUTO_CHUNK_LOADING_RADIUS,
                        AUTO_CHUNK_LOADING_RADIUS,
                        MAX_CHUNK_LOADING_RADIUS);
        MOBS_TARGET_SPIRITS = builder.comment(
                        "Добавлять враждебным мобам цель «дух», чтобы они сами нападали на духов.",
                        "false — мобы смотрят только на игроков, как в ванили.")
                .define("mobsTargetSpirits", true);
        COORDINATOR_ENABLED = builder.comment(
                        "Координатор поселений раздаёт духам с общим домом оборону, помощь и работы.",
                        "false — аварийный рубильник: координатор не тикает, духи живут без его приказов.")
                .define("coordinatorEnabled", true);
        BOARD_ENABLED = builder.comment(
                        "Доска заданий поселения ставит задачи от событий мира и стратега, выдаёт их "
                                + "свободным духам через гейт по ресурсам.",
                        "false — аварийный рубильник: доска не тикает, задания не ставятся и не выдаются.")
                .define("boardEnabled", true);
        CRATER_HOME_RADIUS = builder.comment(
                        "Радиус вокруг дома духа, в котором взрыв крипера запоминается как воронка поселения.",
                        "Вне радиуса взрывы не запоминаются: дух заделывает только воронки своего поселения.")
                .defineInRange("craterHomeRadius", 64, 1, 512);
        NAV_CHUNK_BUDGET = builder.comment(
                        "Сколько чанков навигация может подгрузить на один поход духа.",
                        "Загрузка только для расчёта пути: чанки читаются, мир не меняется; тикеты снимаются после похода.")
                .defineInRange("navChunkBudget", 128, 1, 512);
        NAV_CHUNK_PER_SECOND = builder.comment(
                        "Сколько чанков в секунду на сервер навигация может подгружать для расчётов пути.",
                        "Пока чанк грузится, расчёт ждёт; «неизвестно» не считается воздухом.")
                .defineInRange("navChunkPerSecond", 16, 1, 256);
        builder.pop();
        SPEC = builder.build();
    }

    private SpiritConfig() {}

    public static ModConfigSpec spec() {
        return SPEC;
    }

    public static boolean mobsTargetSpirits() {
        return MOBS_TARGET_SPIRITS.get();
    }

    public static boolean coordinatorEnabled() {
        return COORDINATOR_ENABLED.get();
    }

    public static boolean boardEnabled() {
        return BOARD_ENABLED.get();
    }

    public static int craterHomeRadius() {
        return CRATER_HOME_RADIUS.get();
    }

    public static int navChunkBudget() {
        return NAV_CHUNK_BUDGET.get();
    }

    public static int navChunkPerSecond() {
        return NAV_CHUNK_PER_SECOND.get();
    }

    public static int radiusFor(ServerLevel level) {
        return ChunkLoadingRadius.effective(
                CHUNK_LOADING_RADIUS.get(), level.getServer().getPlayerList().getSimulationDistance());
    }
}
