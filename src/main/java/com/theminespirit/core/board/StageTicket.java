package com.theminespirit.core.board;

import org.jspecify.annotations.Nullable;

public final class StageTicket {
    private StageTicket() {}

    public static boolean stage(String id) {
        return id.startsWith("first:") || id.startsWith("stone:") || id.startsWith("fence:") || id.startsWith("clear:");
    }

    public static boolean removable(@Nullable BoardStatus status, String id) {
        if (status == BoardStatus.FAILED) {
            return !stage(id);
        }
        return status == BoardStatus.CANCELLED;
    }
}
