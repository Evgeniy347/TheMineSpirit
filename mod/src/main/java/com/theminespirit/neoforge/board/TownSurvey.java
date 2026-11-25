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
import com.theminespirit.core.town.TownDrawio;
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
        Optional<TownMap> stored = decodeStored(store, frame);
        if (stored.isPresent()) {
            return stored.orElseThrow();
        }
        if (store.town().isPresent()) {
            TownPlan legacy = store.town().orElseThrow();
            TownMap converted = TownMapConvert.of(legacy, frame.region());
            String xml = TownDrawio.encodeStored(
                    converted, 1, bounds(frame, 0), bounds(frame, 1), bounds(frame, 2), bounds(frame, 3), 2);
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
        TownMap empty = new TownMap(new TownMap.MapPoint(center.x(), center.z()), List.of(), List.of(), List.of());
        TownPlan plan = TownMapRules.build(empty, rules, frame.region(), center, TownSurvey::stageOf);
        store.putMap(
                TownDrawio.encodeStored(
                        empty, 1, bounds(frame, 0), bounds(frame, 1), bounds(frame, 2), bounds(frame, 3), 2),
                plan,
                1);
        LOGGER.info("[plot] карта поселения пуста: ждём раскладку стратега через REST");
        return empty;
    }

    private static Optional<TownMap> decodeStored(SettlementPlotStore store, Frame frame) {
        Optional<String> text = store.mapText();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        String raw = text.orElseThrow();
        try {
            return Optional.of(TownDrawio.decode(raw).map());
        } catch (IllegalArgumentException drawio) {
            try {
                TownMap legacy = TownMapCodec.decode(raw);
                TownMap cleaned = new TownMap(
                        legacy.anchor(),
                        legacy.plots(),
                        legacy.streets().stream()
                                .filter(street -> !"ring".equals(street.kind()))
                                .toList(),
                        legacy.wall());
                store.rewriteMap(TownDrawio.encodeStored(
                        cleaned,
                        store.mapRevision(),
                        bounds(frame, 0),
                        bounds(frame, 1),
                        bounds(frame, 2),
                        bounds(frame, 3),
                        2));
                LOGGER.info(
                        "[plot] карта переведена из JSON в .drawio: участков {}, улиц {}, отрезков стены {}",
                        cleaned.plots().size(),
                        cleaned.streets().size(),
                        cleaned.wall().size());
                return Optional.of(cleaned);
            } catch (IllegalArgumentException json) {
                LOGGER.error("[plot] сохранённая карта не читается, план пуст: {}", drawio.getMessage());
                return Optional.empty();
            }
        }
    }

    private static int bounds(Frame frame, int index) {
        return switch (index) {
            case 0 -> frame.region().minX();
            case 1 -> frame.region().minZ();
            case 2 -> frame.region().minX() + frame.region().width() - 1;
            default -> frame.region().minZ() + frame.region().depth() - 1;
        };
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
