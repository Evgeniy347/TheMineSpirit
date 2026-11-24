package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.town.TownMap;
import com.theminespirit.core.town.TownMapCheck;
import com.theminespirit.core.town.TownMapCodec;
import com.theminespirit.core.town.TownMapFinding;
import com.theminespirit.core.town.TownMapRules;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class TownMapService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerLevel level;

    private TownMapService(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "Мир не может быть null: карта поселения живёт в нём");
    }

    public static TownMapService of(ServerLevel level) {
        return new TownMapService(level);
    }

    public String planJson() {
        TownRules rules = DataTables.INSTANCE.town().current();
        BlockCoord center = SettlementCenter.of(level);
        TownSurvey.Frame frame = TownSurvey.frame(level, center, rules);
        SettlementPlotStore store = SettlementPlotStore.get(level);
        TownMap map = TownSurvey.map(store, center, rules, frame, BoardRegistry.get(level), level.getGameTime());
        return TownMapCodec.encode(map, store.mapRevision(), frame.region());
    }

    public String apply(String body) {
        TownRules rules = DataTables.INSTANCE.town().current();
        BlockCoord center = SettlementCenter.of(level);
        TownSurvey.Frame frame = TownSurvey.frame(level, center, rules);
        SettlementPlotStore store = SettlementPlotStore.get(level);
        TownMap map = TownMapCodec.decode(body);
        if (map.anchor().x() != center.x() || map.anchor().z() != center.z()) {
            throw new ApiException(
                    ErrorCode.INVALID_PLAN,
                    "Якорь карты " + map.anchor().x() + "," + map.anchor().z() + " не совпадает с центром поселения "
                            + center.x() + "," + center.z() + ": координаты обязаны считаться от якоря",
                    Map.of("object", "anchor"));
        }
        List<TownMapFinding> findings =
                TownMapCheck.validate(map, rules, frame.region(), frame.water(), previous(store));
        if (!findings.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_PLAN, message(findings), details(findings));
        }
        int revision = TownMapCodec.revision(body);
        if (revision >= 0 && revision != store.mapRevision()) {
            throw new ApiException(
                    ErrorCode.CONFLICT,
                    "Ревизия карты " + revision + " устарела: в мире уже ревизия " + store.mapRevision()
                            + ", заберите карту заново — иначе правка затрёт чужую",
                    Map.of("expected", store.mapRevision(), "received", revision));
        }
        TownPlan plan = TownMapRules.build(map, rules, frame.region(), center, TownSurvey::stageOf);
        int next = Math.max(store.mapRevision(), revision) + 1;
        store.putMap(TownMapCodec.encode(map, next, frame.region()), plan, next);
        LOGGER.info(
                "[plot] карта применена: ревизия {}, участков {}, улиц {}, отрезков стены {}",
                next,
                map.plots().size(),
                map.streets().size(),
                map.wall().size());
        return "{\"ok\":true,\"revision\":" + next + ",\"plots\":" + map.plots().size() + ",\"streets\":"
                + map.streets().size() + ",\"wall\":" + map.wall().size() + "}";
    }

    private static TownMap previous(SettlementPlotStore store) {
        try {
            return store.mapJson().map(TownMapCodec::decode).orElse(null);
        } catch (IllegalArgumentException broken) {
            LOGGER.warn("[plot] прежняя карта не читается, унаследованных объектов нет: {}", broken.getMessage());
            return null;
        }
    }

    public byte[] previewPng(String body) {
        TownMap map = TownMapCodec.decode(body);
        TownRules rules = DataTables.INSTANCE.town().current();
        BlockCoord center = SettlementCenter.of(level);
        TownSurvey.Frame frame = TownSurvey.frame(level, center, rules);
        TownPlan plan;
        try {
            plan = TownMapRules.build(map, rules, frame.region(), center, TownSurvey::stageOf);
        } catch (IllegalArgumentException broken) {
            throw new ApiException(ErrorCode.INVALID_PLAN, broken.getMessage(), Map.of());
        }
        return TownPreview.png(level, frame, plan, TownPreview.SCALE);
    }

    public byte[] terrainPng(int x1, int z1, int x2, int z2, int scale) {
        return TownPreview.terrain(level, x1, z1, x2, z2, scale);
    }

    private static String message(List<TownMapFinding> findings) {
        String first = findings.get(0).message();
        return findings.size() == 1 ? first : first + " (всего " + findings.size() + " ошибок)";
    }

    private static Map<String, Object> details(List<TownMapFinding> findings) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (TownMapFinding finding : findings) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("object", finding.object());
            error.put("kind", finding.kind());
            error.put("message", finding.message());
            errors.add(error);
        }
        return Map.of("errors", errors);
    }
}
