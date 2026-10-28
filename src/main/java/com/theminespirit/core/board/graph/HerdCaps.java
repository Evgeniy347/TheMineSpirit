package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;

final class HerdCaps {
    private HerdCaps() {}

    static Map<String, HerdCap> parse(JsonNode root, String origin) {
        Map<String, HerdCap> caps = new LinkedHashMap<>();
        root.path("herdCaps").properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            HerdCap cap;
            if (value.isNumber()) {
                cap = new HerdCap(value.asInt(), null);
            } else if (value.isObject()) {
                cap = new HerdCap(
                        value.has("fixed") ? value.get("fixed").asInt() : null,
                        value.has("perSpirit") ? value.get("perSpirit").asDouble() : null);
            } else {
                throw new IllegalStateException("Файл '" + origin + "': лимит стада '" + entry.getKey()
                        + "' — не число и не объект с fixed/perSpirit: неизвестно, с чем сравнивать поголовье");
            }
            caps.put(entry.getKey(), cap);
        });
        return caps;
    }
}
