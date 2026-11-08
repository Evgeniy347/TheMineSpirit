package com.theminespirit.core.rest;

import com.theminespirit.core.board.WarehouseOrder;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class WarehouseJson {
    private WarehouseJson() {}

    public static String viewJson(WarehouseView view) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stage", view.stage());
        body.put("required_level", view.requiredLevel());
        body.put("built_level", view.builtLevel());
        body.put("needed", view.needed());
        body.put("order", orderMap(view.order()));
        body.put("plan_site", view.planSite() == null ? null : coord(view.planSite()));
        body.put("materials", view.materials());
        return RestJson.write(body);
    }

    public static String changeJson(WarehouseChange change, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("previous", orderMap(change.previous()));
        body.put("order", orderMap(change.order()));
        body.put("changed", change.changed());
        body.put("request_id", requestId);
        return RestJson.write(body);
    }

    private static String coord(BlockCoord at) {
        return at.x() + "," + at.y() + "," + at.z();
    }

    private static @Nullable Map<String, Object> orderMap(@Nullable WarehouseOrder order) {
        if (order == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("level", order.level());
        map.put("site", coord(order.site()));
        map.put("by", order.by());
        map.put("reason", order.reason());
        map.put("tick", order.tick());
        return map;
    }
}
