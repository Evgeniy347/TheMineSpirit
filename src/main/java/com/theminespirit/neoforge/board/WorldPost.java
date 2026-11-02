package com.theminespirit.neoforge.board;

final class WorldPost {
    private WorldPost() {}

    static boolean handles(String node) {
        return switch (node) {
            case "demolish_stray", "backfill", "clear_floating", "level_lot", "restore_trace" -> true;
            default -> node.startsWith("repair:");
        };
    }

    static boolean post() {
        return false;
    }
}
