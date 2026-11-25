package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;

final class ClearPlanFilter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SLICE = 500;
    private static final long SLOW_SLICE_NANOS = 50_000_000L;

    private enum Phase {
        IDLE,
        PLANNED,
        PROTECTED,
        FELLING
    }

    private Phase phase = Phase.IDLE;
    private List<BlockCoord> pending = List.of();
    private int cursor;
    private final Set<BlockCoord> drop = new HashSet<>();
    private final List<ClearSummary.Leftover> leftover = new ArrayList<>();

    boolean idle() {
        return phase == Phase.IDLE;
    }

    void start(List<BlockCoord> remaining) {
        phase = Phase.PLANNED;
        pending = List.copyOf(remaining);
        cursor = pending.size() - 1;
        drop.clear();
        leftover.clear();
    }

    void tick(ClearAreaSession session) {
        if (phase == Phase.IDLE) {
            return;
        }
        if (phase == Phase.FELLING) {
            session.noteSkipped(session.felling.dropPartial(session.remaining));
            phase = Phase.IDLE;
            pending = List.of();
            return;
        }
        long started = System.nanoTime();
        int budget = SLICE;
        while (budget-- > 0 && cursor >= 0) {
            BlockCoord cell = pending.get(cursor--);
            String reason = phase == Phase.PLANNED
                    ? session.blocked.plannedFenceReason(cell)
                    : session.blocked.protectedReason(session.spirit, cell, session.demolish);
            if (reason != null) {
                drop.add(cell);
                leftover.add(new ClearSummary.Leftover(cell, session.blocked.blockAt(cell), reason, 0));
            }
        }
        long spent = System.nanoTime() - started;
        if (spent >= SLOW_SLICE_NANOS) {
            LOGGER.warn(
                    "[clear] {}: проверка {} порции {} клеток заняла {} мс — тик стоит защитой поселения",
                    session.spirit.getName().getString(),
                    phase == Phase.PLANNED ? "плана ограды" : "защиты",
                    SLICE,
                    spent / 1_000_000L);
        }
        if (cursor < 0) {
            applyPhase(session);
        }
    }

    private void applyPhase(ClearAreaSession session) {
        int removed = removeDropped(session.remaining);
        session.apply(new ClearBlocked.Sweep(List.copyOf(leftover), 0, 0, 0, 0, removed));
        drop.clear();
        leftover.clear();
        if (phase == Phase.PLANNED) {
            phase = Phase.PROTECTED;
            pending = List.copyOf(session.remaining);
            cursor = pending.size() - 1;
        } else {
            phase = Phase.FELLING;
            pending = List.of();
        }
    }

    private int removeDropped(List<BlockCoord> remaining) {
        int removed = 0;
        for (int index = remaining.size() - 1; index >= 0; index--) {
            if (drop.contains(remaining.get(index))) {
                remaining.remove(index);
                removed++;
            }
        }
        return removed;
    }
}
