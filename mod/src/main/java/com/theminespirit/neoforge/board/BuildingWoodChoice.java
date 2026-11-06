package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BuildingWood;
import com.theminespirit.core.board.BuildingWoods;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.SiteLevels;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.OwnWood;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
            return keepStored(level, service, building, stage, key, now);
        }
        BlockCoord origin = originOf(building, stage);
        BuildingWood fresh = BuildingWood.choose(
                stock,
                SettlementWood.around(level, origin),
                biomeId(level, origin),
                DataTables.INSTANCE.wood().current());
        BuildingWood choice = BuildingWood.fixed(standing(level, building, stage), fresh);
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

    private static String keepStored(
            ServerLevel level, BoardService service, BoardCase building, StageSite stage, String key, long now) {
        SettlementPlotStore store = SettlementPlotStore.get(level);
        BuildingWoods woods = store.woods();
        String stored = woods.of(key).orElseThrow();
        BuildingWood fixed =
                BuildingWood.fixed(standing(level, building, stage), new BuildingWood(stored, "сохранённая"));
        if (fixed.species().equals(stored)) {
            return stored;
        }
        store.fixWoods(woods.rebuilt(key).with(key, fixed));
        LOGGER.info(
                "[wood] постройка {}: сохранённая порода {} не совпала со стоящей {}, закрепляю {}",
                key,
                stored,
                fixed.species(),
                fixed.species());
        service.journal()
                .recordEntry(BoardLog.note(
                        JOURNAL_ID,
                        building,
                        now,
                        List.of(
                                "building=" + key,
                                "wood=" + fixed.species(),
                                "was=" + stored,
                                "reason=" + fixed.reason()),
                        null,
                        ConfigStamp.current()));
        return fixed.species();
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

    private static Map<String, Integer> standing(ServerLevel level, BoardCase building, StageSite stage) {
        Map<String, Integer> species = new HashMap<>();
        if (building == BoardCase.BUILD_HOUSE) {
            count(
                    level,
                    stage,
                    species,
                    PlanBuilding.HOUSE,
                    new Area(
                            stage.hut().x(),
                            stage.hut().z(),
                            stage.hut().width(),
                            stage.hut().depth(),
                            StageSite.BUILDING_HEIGHT));
        } else if (building == BoardCase.BUILD_FENCE) {
            int fenceY = SiteLevels.fenceY(stage);
            for (ColumnMask.Cell post : stage.ring().posts()) {
                for (int y = fenceY - 1; y <= fenceY + 1; y++) {
                    countCell(level, stage, species, PlanBuilding.FENCE, post.x(), y, post.z());
                }
            }
        }
        return species;
    }

    private record Area(int minX, int minZ, int width, int depth, int height) {}

    private static void count(
            ServerLevel level, StageSite stage, Map<String, Integer> species, PlanBuilding target, Area area) {
        for (int x = area.minX(); x < area.minX() + area.width(); x++) {
            for (int z = area.minZ(); z < area.minZ() + area.depth(); z++) {
                for (int y = stage.anchor().y() - 1; y <= stage.anchor().y() + area.height(); y++) {
                    countCell(level, stage, species, target, x, y, z);
                }
            }
        }
    }

    private static void countCell(
            ServerLevel level,
            StageSite stage,
            Map<String, Integer> species,
            PlanBuilding target,
            int x,
            int y,
            int z) {
        if (stage.planBuilding(x, y, z) != target) {
            return;
        }
        String id = BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(new BlockPos(x, y, z)).getBlock())
                .getPath();
        String found = OwnWood.species(id);
        if (!found.isEmpty() && WoodSpecies.ALL.contains(found)) {
            species.merge(found, 1, Integer::sum);
        }
    }

    public static Set<String> allowed(ServerLevel level, String fallback) {
        Set<String> species =
                new HashSet<>(SettlementPlotStore.get(level).woods().species());
        species.add(fallback);
        return species;
    }

    private static BlockCoord originOf(BoardCase building, StageSite stage) {
        return building == BoardCase.BUILD_HOUSE ? stage.houseOrigin() : stage.anchor();
    }

    private static String biomeId(ServerLevel level, BlockCoord place) {
        return level.getBiome(new BlockPos(place.x(), place.y(), place.z()))
                .unwrapKey()
                .map(key -> key.location().toString())
                .orElse("minecraft:plains");
    }
}
