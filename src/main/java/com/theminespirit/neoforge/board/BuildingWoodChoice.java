package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BuildingWood;
import com.theminespirit.core.board.BuildingWoods;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSite;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class BuildingWoodChoice {
    public static final Set<BoardCase> WOODEN = EnumSet.of(BoardCase.BUILD_FENCE, BoardCase.BUILD_HOUSE);
    private static final String JOURNAL_ID = "wood";
    private static final Logger LOGGER = LogUtils.getLogger();

    private BuildingWoodChoice() {}

    public static String settle(
            ServerLevel level,
            BoardService service,
            BoardCase building,
            StageSite stage,
            Map<String, Integer> stock,
            String fallback,
            long now) {
        if (!WOODEN.contains(building)) {
            return fallback;
        }
        SettlementPlotStore store = SettlementPlotStore.get(level);
        String key = BuildingWoods.key(building, originOf(building, stage));
        BuildingWoods woods = store.woods();
        if (woods.of(key).isPresent()) {
            return woods.of(key).orElseThrow();
        }
        BlockCoord origin = originOf(building, stage);
        BuildingWood choice = BuildingWood.choose(
                stock,
                SettlementWood.around(level, origin),
                biomeId(level, origin),
                DataTables.INSTANCE.wood().current());
        store.fixWoods(woods.with(key, choice));
        LOGGER.info("[wood] постройка {}: порода {}, {}", key, choice.species(), choice.reason());
        service.journal()
                .recordEntry(BoardLog.note(
                        JOURNAL_ID,
                        building,
                        now,
                        List.of("building=" + key, "wood=" + choice.species(), "reason=" + choice.reason()),
                        null,
                        ConfigStamp.current()));
        return choice.species();
    }

    public static String stored(ServerLevel level, BoardCase building, StageSite stage, String fallback) {
        if (!WOODEN.contains(building)) {
            return fallback;
        }
        return SettlementPlotStore.get(level)
                .woods()
                .of(BuildingWoods.key(building, originOf(building, stage)))
                .orElse(fallback);
    }

    static void rebuilt(ServerLevel level, BoardCase building, StageSite stage) {
        SettlementPlotStore store = SettlementPlotStore.get(level);
        String key = BuildingWoods.key(building, originOf(building, stage));
        if (store.woods().of(key).isPresent()) {
            store.fixWoods(store.woods().rebuilt(key));
            LOGGER.info("[wood] постройка {}: перестройка, порода будет выбрана заново", key);
        }
    }

    public static Set<String> allowed(ServerLevel level, String fallback) {
        Set<String> species =
                new HashSet<>(SettlementPlotStore.get(level).woods().species());
        species.add(fallback);
        return species;
    }

    private static BlockCoord originOf(BoardCase building, StageSite stage) {
        return building == BoardCase.BUILD_HOUSE ? stage.houseOrigin() : stage.palisadeOrigin();
    }

    private static String biomeId(ServerLevel level, BlockCoord place) {
        return level.getBiome(new BlockPos(place.x(), place.y(), place.z()))
                .unwrapKey()
                .map(key -> key.location().toString())
                .orElse("minecraft:plains");
    }
}
