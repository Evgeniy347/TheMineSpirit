package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.engine.RunStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class ActivityCards {
    private final Map<UUID, Launched> launched = new HashMap<>();

    void remember(UUID id, BoardCase activity, long generation, String script, @Nullable String target) {
        launched.put(id, new Launched(activity, generation, script, target, 0));
    }

    void forget(UUID id) {
        launched.remove(id);
    }

    boolean idleOrActivity(UUID id, long generation, @Nullable RunStatus status) {
        if (status != RunStatus.RUNNING) {
            return true;
        }
        Launched own = launched.get(id);
        return own != null && own.generation() == generation;
    }

    private record Launched(
            BoardCase activity,
            long generation,
            String script,
            @Nullable String target,
            long idleSince) {}
}
