package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTuning;
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

    Optional<BoardOccupant.Occupation> occupation(SpiritEntity spirit, boolean assigned, long now) {
        Launched own = refresh(spirit, now);
        return BoardOccupant.of(
                spirit,
                own != null,
                own == null ? null : own.activity(),
                own == null ? null : own.script(),
                own == null ? null : own.target(),
                assigned);
    }

    private @Nullable Launched refresh(SpiritEntity spirit, long now) {
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
        if (status == RunStatus.RUNNING) {
            if (own.idleSince() == 0) {
                return own;
            }
            Launched active = new Launched(own.activity(), own.generation(), own.script(), own.target(), 0);
            launched.put(id, active);
            return active;
        }
        if (status != RunStatus.FAILED) {
            launched.remove(id);
            return null;
        }
        long since = own.idleSince() == 0 ? now : own.idleSince();
        if (now - since > BoardTuning.failBackoffTicks()) {
            launched.remove(id);
            return null;
        }
        if (own.idleSince() == 0) {
            Launched paused = new Launched(own.activity(), own.generation(), own.script(), own.target(), since);
            launched.put(id, paused);
            return paused;
        }
        return own;
    }

    private record Launched(
            BoardCase activity,
            long generation,
            String script,
            @Nullable String target,
            long idleSince) {}
}
