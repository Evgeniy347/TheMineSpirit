package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.api.ConfigStamp;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.PlotContour;
import com.theminespirit.core.town.TerrainGrid;
import com.theminespirit.core.town.TownMap;
import com.theminespirit.core.town.TownMapCodec;
import com.theminespirit.core.town.TownMapConvert;
import com.theminespirit.core.town.TownMapRules;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownRules;
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
        Frame frame = frame(level, center, rules);
        TownMap map = map(store, center, rules, frame, service, now);
        TownPlan plan = TownMapRules.build(map, rules, frame.region(), center, TownSurvey::stageOf);
        store.settlePlan(plan);
        return plan;
    }

    static Frame frame(ServerLevel level, BlockCoord center, TownRules rules) {
        TerrainGrid grid = GroundProbe.terrain(level, center, rules.ground().probeRadius());
        ColumnMask region = PlotContour.grow(grid, center.x(), center.z(), rules.ground());
        return new Frame(grid, region, water(grid, region));
    }

    static TownMap map(
            SettlementPlotStore store,
            BlockCoord center,
            TownRules rules,
            Frame frame,
            BoardService service,
            long now) {
        Optional<TownMap> stored = decodeStored(store);
        if (stored.isPresent()
                && TownMapCodec.format(store.mapJson().orElseThrow()) < TownMapCodec.FORMAT
                && store.town().isPresent()) {
            TownMap converted = TownMapConvert.of(store.town().orElseThrow(), frame.region());
            int revision = store.mapRevision() + 1;
            store.putMap(
                    TownMapCodec.encode(converted, revision, frame.region()),
                    store.town().orElseThrow(),
                    revision);
            LOGGER.info(
                    "[plot] карта пересобрана по новому формату: участков {}, улиц {}",
                    converted.plots().size(),
                    converted.streets().size());
            return converted;
        }
        if (stored.isPresent()) {
            return stored.orElseThrow();
        }
        if (store.town().isPresent()) {
            TownPlan legacy = store.town().orElseThrow();
            TownMap converted = TownMapConvert.of(legacy, frame.region());
            String json = TownMapCodec.encode(converted, 1, frame.region());
            store.putMap(json, legacy, 1);
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
        TownMap empty = new TownMap(new TownMap.MapPoint(center.x(), center.z()), List.of(), List.of(), List.of());
        TownPlan plan = TownMapRules.build(empty, rules, frame.region(), center, TownSurvey::stageOf);
        store.putMap(TownMapCodec.encode(empty, 1, frame.region()), plan, 1);
        LOGGER.info("[plot] карта поселения пуста: ждём раскладку стратега через REST");
        return empty;
    }

    private static Optional<TownMap> decodeStored(SettlementPlotStore store) {
        Optional<String> json = store.mapJson();
        if (json.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TownMapCodec.decode(json.orElseThrow()));
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
