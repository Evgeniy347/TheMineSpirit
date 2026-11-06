package com.theminespirit.core.rest;

import com.theminespirit.core.board.graph.WorldReport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorldJson {
    private WorldJson() {}

    public static String viewJson(WorldReport report) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("strays", report.strayBlocks());
        body.put("strayGroups", report.strayGroups());
        body.put("pits", report.pits());
        body.put("mounds", report.mounds());
        body.put("natural", report.natural());
        body.put("hanging", report.hanging());
        body.put("damagedBuildings", report.damaged());
        body.put("unknown", report.unknown());
        List<Object> groups = new ArrayList<>();
        for (WorldReport.Stray stray : report.strays()) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("type", stray.type());
            group.put("position", List.of(stray.x(), stray.y(), stray.z()));
            group.put("blocks", stray.blocks());
            group.put("builder", stray.builder());
            groups.add(group);
        }
        body.put("groups", groups);
        List<Object> buildings = new ArrayList<>();
        for (WorldReport.Damaged building : report.buildings()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", building.id());
            entry.put("damage", building.damage());
            entry.put("unknown", building.unknown());
            buildings.add(entry);
        }
        body.put("buildings", buildings);
        return RestJson.write(body);
    }
}
