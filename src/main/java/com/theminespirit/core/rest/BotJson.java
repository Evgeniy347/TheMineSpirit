package com.theminespirit.core.rest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class BotJson {
    private BotJson() {}

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
        map.put("activity", bot.activity());
        map.put("script", bot.script());
        map.put("board_task", boardTask(bot.boardTask()));
        map.put("tick", bot.tick());
        map.put("position_tick", bot.positionTick());
        map.put("state_since_tick", bot.stateSinceTick());
        map.put("food", bot.food());
        return map;
    }

    private static @Nullable Object boardTask(@Nullable BotBoardTask task) {
        if (task == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", task.id());
        map.put("case", task.caseName());
        map.put("status", task.status());
        return map;
    }
}
