package com.theminespirit.core.rest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BotJson {
    private BotJson() {
    }

    public static String toJson(BotInfo bot) {
        return RestJson.write(toMap(bot));
    }

    public static String listToJson(List<BotInfo> bots) {
        List<Map<String, Object>> entries = new ArrayList<>(bots.size());
        for (BotInfo bot : bots) {
            entries.add(toMap(bot));
        }
        return RestJson.write(Map.of("bots", entries));
    }

    private static Map<String, Object> toMap(BotInfo bot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", bot.id());
        map.put("name", bot.name());
        map.put("state", bot.state().wire());
        map.put("position", List.of(bot.x(), bot.y(), bot.z()));
        map.put("dimension", bot.dimension());
        map.put("current_task", bot.currentTask());
        return map;
    }
}
