package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.ActivityGrace;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    Optional<BoardOccupant.Occupation> occupation(SpiritEntity spirit, boolean assigned) {
        Launched own = refresh(spirit);
        return BoardOccupant.of(
                spirit,
                own != null,
                own == null ? null : own.activity(),
                own == null ? null : own.script(),
                own == null ? null : own.target(),
                assigned);
    }

    private @Nullable Launched refresh(SpiritEntity spirit) {
        UUID id = spirit.getUUID();
        Launched own = launched.get(id);
        if (own == null) {
            return null;
        }
        if (own.generation() != spirit.taskGeneration()) {
            launched.remove(id);
            return null;
        }
        RunStatus status = spirit.taskStatus().orElse(null);
        long now = spirit.level().getGameTime();
        if (status == RunStatus.RUNNING) {
            if (own.idleSince() == 0) {
                return own;
            }
            Launched active = new Launched(own.activity(), own.generation(), own.script(), own.target(), 0);
            launched.put(id, active);
            return active;
        }
        if (own.idleSince() == 0) {
            Launched idle = new Launched(own.activity(), own.generation(), own.script(), own.target(), now);
            launched.put(id, idle);
            return idle;
        }
        if (ActivityGrace.keep(own.idleSince(), now)) {
            return own;
        }
        launched.remove(id);
        return null;
    }

    private record Launched(
            BoardCase activity,
            long generation,
            String script,
            @Nullable String target,
            long idleSince) {}
}
