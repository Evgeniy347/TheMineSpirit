package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.api.ConfigStamp;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.TerrainGrid;
import com.theminespirit.core.town.TownDrawio;
import com.theminespirit.core.town.TownMap;
import com.theminespirit.core.town.TownMapConvert;
import com.theminespirit.core.town.TownMapRules;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownRules;
import com.theminespirit.core.town.WallRegion;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class TownSurvey {
    static final String JOURNAL_ID = "town";
    private static final Logger LOGGER = LogUtils.getLogger();

    private TownSurvey() {}

    record Frame(TerrainGrid grid, ColumnMask region, ColumnMask water) {}

    static TownPlan town(
            ServerLevel level, BoardService service, SettlementPlotStore store, BlockCoord center, long now) {
        TownRules rules = DataTables.INSTANCE.town().current();
        TownMap map = map(store, center, rules, service, now);
        TownPlan plan;
        if (map.wall().isEmpty()) {
            LOGGER.warn("[plot] стены в плане нет: границы поселения не заданы, стройка и расчистка ждут план");
            plan = TownMapRules.build(blank(center), rules, emptyRegion(center), center, TownSurvey::stageOf);
        } else {
            Frame frame = frame(level, center, map, rules);
            plan = TownMapRules.build(map, rules, frame.region(), center, TownSurvey::stageOf);
        }
        store.settlePlan(plan);
        return plan;
    }

    static Frame frame(ServerLevel level, BlockCoord center, TownMap map, TownRules rules) {
        ColumnMask region = WallRegion.of(map.wall());
        int cx = region.minX() + region.width() / 2;
        int cz = region.minZ() + region.depth() / 2;
        int reach = Math.max(
                Math.max(cx - region.minX(), region.minX() + region.width() - 1 - cx),
                Math.max(cz - region.minZ(), region.minZ() + region.depth() - 1 - cz));
        int radius = Math.max(rules.ground().probeRadius(), reach + 2);
        TerrainGrid grid = GroundProbe.terrain(level, new BlockCoord(cx, center.y(), cz), radius);
        return new Frame(grid, region, water(grid, region));
    }

    static Frame canvas(ServerLevel level, BlockCoord center, TownRules rules) {
        int radius = rules.ground().probeRadius();
        TerrainGrid grid = GroundProbe.terrain(level, center, radius);
        ColumnMask region =
                ColumnMask.rect(center.x() - radius, center.z() - radius, center.x() + radius, center.z() + radius);
        return new Frame(grid, region, water(grid, region));
    }

    private static TownMap blank(BlockCoord center) {
        return new TownMap(new TownMap.MapPoint(center.x(), center.z()), List.of(), List.of(), List.of());
    }

    private static ColumnMask emptyRegion(BlockCoord center) {
        return new ColumnMask(center.x(), center.z(), 1, 1, new BitSet());
    }

    static TownMap map(SettlementPlotStore store, BlockCoord center, TownRules rules, BoardService service, long now) {
        Optional<TownMap> stored = decodeStored(store);
        if (stored.isPresent()) {
            return stored.orElseThrow();
        }
        if (store.town().isPresent()) {
            TownPlan legacy = store.town().orElseThrow();
            TownMap converted = TownMapConvert.of(legacy);
            String xml = TownDrawio.encodeStored(
                    converted,
                    1,
                    legacy.region().minX(),
                    legacy.region().minZ(),
                    legacy.region().minX() + legacy.region().width() - 1,
                    legacy.region().minZ() + legacy.region().depth() - 1,
                    2);
            store.putMap(xml, legacy, 1);
            LOGGER.info(
                    "[plot] старый план пересобран в карту: участков {}, улиц {}, отрезков стены {}",
                    converted.plots().size(),
                    converted.streets().size(),
                    converted.wall().size());
            service.journal()
                    .recordEntry(BoardLog.note(
                            JOURNAL_ID,
                            BoardCase.CLEAR_GROUNDS,
                            now,
                            List.of(
                                    "план переведён в карту",
                                    "участков=" + converted.plots().size()),
                            null,
                            ConfigStamp.current()));
            return converted;
        }
        TownMap empty = blank(center);
        TownPlan plan = TownMapRules.build(empty, rules, emptyRegion(center), center, TownSurvey::stageOf);
        int radius = rules.ground().probeRadius();
        store.putMap(
                TownDrawio.encodeStored(
                        empty,
                        1,
                        center.x() - radius,
                        center.z() - radius,
                        center.x() + radius,
                        center.z() + radius,
                        2),
                plan,
                1);
        LOGGER.info("[plot] карта поселения пуста: ждём раскладку стратега через REST");
        return empty;
    }

    private static Optional<TownMap> decodeStored(SettlementPlotStore store) {
        Optional<String> text = store.mapText();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TownDrawio.decode(text.orElseThrow()).map());
        } catch (IllegalArgumentException broken) {
            LOGGER.error("[plot] сохранённая карта не читается, план пуст: {}", broken.getMessage());
            return Optional.empty();
        }
    }

    static int stageOf(String building) {
        List<List<String>> plans = DataTables.INSTANCE.stages().current().plans();
        for (int index = 0; index < plans.size(); index++) {
            if (plans.get(index).contains(building)) {
                return index + 1;
            }
        }
        return 1;
    }

    private static ColumnMask water(TerrainGrid grid, ColumnMask region) {
        BitSet bits = new BitSet(region.width() * region.depth());
        for (int x = region.minX(); x < region.minX() + region.width(); x++) {
            for (int z = region.minZ(); z < region.minZ() + region.depth(); z++) {
                if (grid.water(x, z)) {
                    bits.set((z - region.minZ()) * region.width() + x - region.minX());
                }
            }
        }
        return new ColumnMask(region.minX(), region.minZ(), region.width(), region.depth(), bits);
    }
}
