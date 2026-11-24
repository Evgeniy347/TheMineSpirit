package com.theminespirit.core.rest;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SettlementLevelJson {
    private SettlementLevelJson() {}

    public static String viewJson(SettlementLevelView view) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("level", view.level());
        body.put("y0", view.y0());
        body.put("anchor", Map.of("x", view.x(), "z", view.z()));
        body.put("rule", view.rule());
        return RestJson.write(body);
    }

    public static String previewJson(SettlementLevelPreview preview) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("level", preview.level());
        body.put("y0", preview.y0());
        body.put("anchor", Map.of("x", preview.x(), "z", preview.z()));
        body.put("cut", preview.cut());
        body.put("fill", preview.fill());
        body.put("surplusPercent", preview.surplusPercent());
        Map<String, Object> stock = new LinkedHashMap<>();
        stock.put("blocks", preview.stockBlocks());
        stock.put("covered", preview.stockCovered());
        body.put("stock", stock);
        Map<String, Object> water = new LinkedHashMap<>();
        water.put("blocks", preview.waterBlocks());
        water.put("median", preview.waterMedian());
        water.put("floor", preview.waterFloor());
        body.put("water", water);
        Map<String, Object> area = new LinkedHashMap<>();
        area.put("columns", preview.columns());
        area.put("min", preview.min());
        area.put("max", preview.max());
        body.put("area", area);
        return RestJson.write(body);
    }

    public static String changeJson(SettlementLevelChangeResult result, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("previous", result.previous());
        body.put("level", result.level());
        body.put("y0", result.level() - 1);
        body.put("changed", result.changed());
        body.put("request_id", requestId);
        return RestJson.write(body);
    }
}
