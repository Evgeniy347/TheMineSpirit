package com.theminespirit.core.log;

public enum LogType {
    BLOCK_CHANGE("block_change", "block_changes"),
    DEATH("death", "deaths"),
    TASK_EVENT("task_event", "task_events"),
    GRAPH_DECISION("graph_decision", "graph_decisions"),
    ROUTE("route", "routes"),
    STUCK("stuck", "stuck"),
    ITEM_MOVE("item_move", "item_moves"),
    SPIRIT_SAMPLE("spirit_sample", "spirit_samples"),
    SERVER_HEALTH("server_health", "server_health"),
    MESSAGE("message", "messages");

    private final String json;
    private final String table;

    LogType(String json, String table) {
        this.json = json;
        this.table = table;
    }

    public String json() {
        return json;
    }

    public String table() {
        return table;
    }
}
