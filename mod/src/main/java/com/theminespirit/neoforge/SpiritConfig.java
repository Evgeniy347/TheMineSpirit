package com.theminespirit.neoforge;

import com.theminespirit.core.chunk.ChunkLoadingRadius;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class SpiritConfig {
    public static final int AUTO_CHUNK_LOADING_RADIUS = ChunkLoadingRadius.AUTO;
    public static final int MAX_CHUNK_LOADING_RADIUS = 32;
    public static final int DEFAULT_SEARCH_Y_RADIUS = 64;
    public static final int DEFAULT_MORE_DANGERS_MAX = 256;
    public static final int DEFAULT_MORE_DROPS_MAX = 64;
    public static final int DEFAULT_MORE_BROKEN_MAX = 256;
    public static final int DEFAULT_MORE_COUNT_MAX = 64;
    public static final int DEFAULT_COLLECT_RADIUS = 4;
    public static final int DEFAULT_TIGHT_COLLECT_RADIUS = 3;
    public static final int DEFAULT_STORAGE_SEARCH_RADIUS = 8;
    public static final int DEFAULT_WATER_SEARCH_RADIUS = 6;
    public static final int DEFAULT_FORAGE_WATER_RADIUS = 8;
    public static final int DEFAULT_STAND_WALK_RADIUS = 3;
    public static final int DEFAULT_WALL_ESCAPE_RADIUS = 2;
    public static final int DEFAULT_BUILD_MAKE_WAY_RADIUS = 6;
    public static final int DEFAULT_FOLLOW_PICKUP_RADIUS = 4;
    public static final int DEFAULT_COMPANION_SEARCH_RADIUS = 64;
    public static final int DEFAULT_SURVEY_RADIUS = 2;
    public static final int DEFAULT_PARTY_RADIUS = 32;
    public static final int DEFAULT_NEAR_RADIUS = 8;
    public static final int DEFAULT_NAV_TICKET_RADIUS_CHUNKS = 1;
    public static final int DEFAULT_RESCUE_REACH_RADIUS = 2;

    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue CHUNK_LOADING_RADIUS;
    private static final ModConfigSpec.BooleanValue MOBS_TARGET_SPIRITS;
    private static final ModConfigSpec.BooleanValue COORDINATOR_ENABLED;
    private static final ModConfigSpec.BooleanValue BOARD_ENABLED;
    private static final ModConfigSpec.IntValue CRATER_HOME_RADIUS;
    private static final ModConfigSpec.IntValue NAV_CHUNK_BUDGET;
    private static final ModConfigSpec.IntValue NAV_CHUNK_PER_SECOND;
    private static final ModConfigSpec.IntValue SEARCH_Y_RADIUS;
    private static final ModConfigSpec.IntValue MORE_DANGERS_MAX;
    private static final ModConfigSpec.IntValue MORE_DROPS_MAX;
    private static final ModConfigSpec.IntValue MORE_BROKEN_MAX;
    private static final ModConfigSpec.IntValue MORE_COUNT_MAX;
    private static final ModConfigSpec.IntValue COLLECT_RADIUS;
    private static final ModConfigSpec.IntValue TIGHT_COLLECT_RADIUS;
    private static final ModConfigSpec.IntValue STORAGE_SEARCH_RADIUS;
    private static final ModConfigSpec.IntValue WATER_SEARCH_RADIUS;
    private static final ModConfigSpec.IntValue FORAGE_WATER_RADIUS;
    private static final ModConfigSpec.IntValue STAND_WALK_RADIUS;
    private static final ModConfigSpec.IntValue WALL_ESCAPE_RADIUS;
    private static final ModConfigSpec.IntValue BUILD_MAKE_WAY_RADIUS;
    private static final ModConfigSpec.IntValue FOLLOW_PICKUP_RADIUS;
    private static final ModConfigSpec.IntValue COMPANION_SEARCH_RADIUS;
    private static final ModConfigSpec.IntValue SURVEY_RADIUS;
    private static final ModConfigSpec.IntValue PARTY_RADIUS;
    private static final ModConfigSpec.IntValue NEAR_RADIUS;
    private static final ModConfigSpec.IntValue NAV_TICKET_RADIUS_CHUNKS;
    private static final ModConfigSpec.IntValue RESCUE_REACH_RADIUS;

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
        SEARCH_Y_RADIUS = builder.comment(
                        "Насколько вверх и вниз от центра поиск блоков смотрит при поиске руды и места.",
                        "Ванильный мир — 384 блока высотой; шире искать нельзя.")
                .defineInRange("searchYRadius", DEFAULT_SEARCH_Y_RADIUS, 1, 384);
        MORE_DANGERS_MAX = builder.comment(
                        "Сколько опасных точек поселения хранит книга опасностей до вытеснения старых.",
                        "Меньше — старые угрозы забываются быстрее и духи перестают их обходить.")
                .defineInRange("moreDangersMax", DEFAULT_MORE_DANGERS_MAX, 1, 4096);
        MORE_DROPS_MAX = builder.comment(
                        "Сколько выпавших предметов помнит книга добычи до вытеснения старых.",
                        "Меньше — старые дропы теряются и дух не забирает их.")
                .defineInRange("moreDropsMax", DEFAULT_MORE_DROPS_MAX, 1, 1024);
        MORE_BROKEN_MAX = builder.comment(
                        "Сколько сломанных блоков помнит книга сноса до вытеснения старых.",
                        "Меньше — старые поломки забываются и дух может их не восстановить.")
                .defineInRange("moreBrokenMax", DEFAULT_MORE_BROKEN_MAX, 1, 4096);
        MORE_COUNT_MAX = builder.comment(
                        "Верхний предел количества предметов, которое дух учитывает за один заход.",
                        "Больше — скрипт обмена может просить чрезмерные пачки.")
                .defineInRange("moreCountMax", DEFAULT_MORE_COUNT_MAX, 1, 256);
        COLLECT_RADIUS = builder.comment(
                        "Радиус сбора выпавших предметов после действия духа, в блоках.",
                        "Шире — дух соберёт и чужой дроп рядом; уже — часть своего дропа останется лежать.")
                .defineInRange("collectRadius", DEFAULT_COLLECT_RADIUS, 1, 16);
        TIGHT_COLLECT_RADIUS = builder.comment(
                        "Радиус сбора для мелких работ — грядка и животные: рядом стоят чужие вещи, их не трогаем.",
                        "Шире — дух стаскивает дроп соседних работ.")
                .defineInRange("tightCollectRadius", DEFAULT_TIGHT_COLLECT_RADIUS, 1, 16);
        STORAGE_SEARCH_RADIUS = builder.comment(
                        "Радиус поиска сундуков и складов вокруг точки, в блоках.",
                        "Шире — дух читает чужие склады и тратит тик на лишние контейнеры.")
                .defineInRange("storageSearchRadius", DEFAULT_STORAGE_SEARCH_RADIUS, 1, 64);
        WATER_SEARCH_RADIUS = builder.comment(
                        "Радиус поиска воды вокруг блока для полива и ведра, в блоках.",
                        "Шире — дух видит воду сквозь стены дальше и чаще берётся за дальний источник.")
                .defineInRange("waterSearchRadius", DEFAULT_WATER_SEARCH_RADIUS, 1, 32);
        FORAGE_WATER_RADIUS = builder.comment(
                        "Радиус поиска воды при выборе места сбора, в блоках.",
                        "Шире — сбор считается обеспеченным водой даже на сухом краю поселения.")
                .defineInRange("forageWaterRadius", DEFAULT_FORAGE_WATER_RADIUS, 1, 32);
        STAND_WALK_RADIUS = builder.comment(
                        "Радиус поиска стоянок при откате и обходе, в блоках.",
                        "Шире — дух ищет стоянку дальше от цели и дольше думает.")
                .defineInRange("standWalkRadius", DEFAULT_STAND_WALK_RADIUS, 1, 12);
        WALL_ESCAPE_RADIUS = builder.comment(
                        "Радиус осмотра запертого духа вокруг себя при побеге из ямы, в блоках.",
                        "Шире — дух видит выход через толстую стену.")
                .defineInRange("wallEscapeRadius", DEFAULT_WALL_ESCAPE_RADIUS, 1, 8);
        BUILD_MAKE_WAY_RADIUS = builder.comment(
                        "Сколько колец вокруг постройки дух расчищает, чтобы подойти, в блоках.",
                        "Шире — больше сноса чужого леса вокруг стройки.")
                .defineInRange("buildMakeWayRadius", DEFAULT_BUILD_MAKE_WAY_RADIUS, 1, 16);
        FOLLOW_PICKUP_RADIUS = builder.comment(
                        "Радиус сбора дропа, когда дух сопровождает игрока, в блоках.",
                        "Шире — дух отвлекается на дроп дальше от игрока.")
                .defineInRange("followPickupRadius", DEFAULT_FOLLOW_PICKUP_RADIUS, 1, 16);
        COMPANION_SEARCH_RADIUS = builder.comment(
                        "Радиус поиска соседних духов при сопровождении, в блоках.",
                        "Шире — дороже поиск, но спутники дальше не теряются.")
                .defineInRange("companionSearchRadius", DEFAULT_COMPANION_SEARCH_RADIUS, 1, 256);
        SURVEY_RADIUS = builder.comment(
                        "Радиус осмотра вокруг клетки при разведке, в блоках.",
                        "Шире — осмотр дольше и дороже по тикам.")
                .defineInRange("surveyRadius", DEFAULT_SURVEY_RADIUS, 1, 8);
        PARTY_RADIUS = builder.comment(
                        "Радиус, в котором дух-разведчик ищет отряд, в блоках.",
                        "Шире — отряд не теряется, но поиск дороже.")
                .defineInRange("partyRadius", DEFAULT_PARTY_RADIUS, 1, 128);
        NEAR_RADIUS = builder.comment(
                        "Радиус, с какого места дух считает, что уже пришёл к блоку, в блоках.",
                        "Шире — дух «доходит» до цели издалека и не касается нужного блока.")
                .defineInRange("nearRadius", DEFAULT_NEAR_RADIUS, 1, 16);
        NAV_TICKET_RADIUS_CHUNKS = builder.comment(
                        "Радиус чанков, которые навигация держит тикетами вокруг похода.",
                        "Шире — больше чанков нагружает сервер.")
                .defineInRange("navTicketRadiusChunks", DEFAULT_NAV_TICKET_RADIUS_CHUNKS, 0, 8);
        RESCUE_REACH_RADIUS = builder.comment(
                        "Радиус, в котором дух проверяет досягаемость запертого, в блоках.",
                        "Шире — спасение находится дальше от запертого.")
                .defineInRange("rescueReachRadius", DEFAULT_RESCUE_REACH_RADIUS, 1, 8);
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

    public static int searchYRadius() {
        return loaded(SEARCH_Y_RADIUS, DEFAULT_SEARCH_Y_RADIUS);
    }

    public static int moreDangersMax() {
        return loaded(MORE_DANGERS_MAX, DEFAULT_MORE_DANGERS_MAX);
    }

    public static int moreDropsMax() {
        return loaded(MORE_DROPS_MAX, DEFAULT_MORE_DROPS_MAX);
    }

    public static int moreBrokenMax() {
        return loaded(MORE_BROKEN_MAX, DEFAULT_MORE_BROKEN_MAX);
    }

    public static int collectRadius() {
        return loaded(COLLECT_RADIUS, DEFAULT_COLLECT_RADIUS);
    }

    public static int tightCollectRadius() {
        return loaded(TIGHT_COLLECT_RADIUS, DEFAULT_TIGHT_COLLECT_RADIUS);
    }

    public static int storageSearchRadius() {
        return loaded(STORAGE_SEARCH_RADIUS, DEFAULT_STORAGE_SEARCH_RADIUS);
    }

    public static int waterSearchRadius() {
        return loaded(WATER_SEARCH_RADIUS, DEFAULT_WATER_SEARCH_RADIUS);
    }

    public static int forageWaterRadius() {
        return loaded(FORAGE_WATER_RADIUS, DEFAULT_FORAGE_WATER_RADIUS);
    }

    public static int standWalkRadius() {
        return loaded(STAND_WALK_RADIUS, DEFAULT_STAND_WALK_RADIUS);
    }

    public static int wallEscapeRadius() {
        return loaded(WALL_ESCAPE_RADIUS, DEFAULT_WALL_ESCAPE_RADIUS);
    }

    public static int buildMakeWayRadius() {
        return loaded(BUILD_MAKE_WAY_RADIUS, DEFAULT_BUILD_MAKE_WAY_RADIUS);
    }

    public static int followPickupRadius() {
        return loaded(FOLLOW_PICKUP_RADIUS, DEFAULT_FOLLOW_PICKUP_RADIUS);
    }

    public static int companionSearchRadius() {
        return loaded(COMPANION_SEARCH_RADIUS, DEFAULT_COMPANION_SEARCH_RADIUS);
    }

    public static int surveyRadius() {
        return loaded(SURVEY_RADIUS, DEFAULT_SURVEY_RADIUS);
    }

    public static int partyRadius() {
        return loaded(PARTY_RADIUS, DEFAULT_PARTY_RADIUS);
    }

    public static int nearRadius() {
        return loaded(NEAR_RADIUS, DEFAULT_NEAR_RADIUS);
    }

    public static int navTicketRadiusChunks() {
        return loaded(NAV_TICKET_RADIUS_CHUNKS, DEFAULT_NAV_TICKET_RADIUS_CHUNKS);
    }

    public static int rescueReachRadius() {
        return loaded(RESCUE_REACH_RADIUS, DEFAULT_RESCUE_REACH_RADIUS);
    }

    public static int moreCountMax() {
        return loaded(MORE_COUNT_MAX, DEFAULT_MORE_COUNT_MAX);
    }

    private static int loaded(ModConfigSpec.IntValue value, int fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }
}
