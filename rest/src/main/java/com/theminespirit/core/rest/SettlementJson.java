package com.theminespirit.core.rest;

import com.theminespirit.core.board.StageView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SettlementJson {
    private SettlementJson() {}

    public static String viewJson(StageView view) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stage", view.stage());
        body.put("stages", view.stages());
        body.put("missing", view.missing());
        List<Object> requirements = new ArrayList<>(view.requirements().size());
        for (StageView.Requirement requirement : view.requirements()) {
            requirements.add(requirementMap(requirement));
        }
        body.put("requirements", requirements);
        return RestJson.write(body);
    }

    public static String changeJson(SettlementStageChange change, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("previous", change.previous());
        body.put("stage", change.stage());
        body.put("changed", change.changed());
        body.put("forgotten", change.forgotten());
        body.put("cancelled", change.cancelled());
        body.put("request_id", requestId);
        return RestJson.write(body);
    }

    private static Map<String, Object> requirementMap(StageView.Requirement requirement) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stage", requirement.stage());
        map.put("id", requirement.id());
        map.put("level", requirement.level());
        map.put("closed", requirement.closed());
        map.put("world", requirement.world().name().toLowerCase(Locale.ROOT));
        map.put("optional", requirement.optional());
        return map;
    }
}
