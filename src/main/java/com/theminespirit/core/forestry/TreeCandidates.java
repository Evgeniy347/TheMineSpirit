package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class TreeCandidates {
    private final int limit;
    private final Set<BlockCoord> live = new LinkedHashSet<>();
    private final Set<BlockCoord> known = new LinkedHashSet<>();

    public TreeCandidates(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit=" + limit + ": лимит живых кандидатов меньше единицы, "
                    + "поиск не получил бы ни одного бревна и дерево не нашлось бы никогда");
        }
        this.limit = limit;
    }

    public void offer(BlockCoord log) {
        Objects.requireNonNull(log, "log не может быть null: кандидат — координата найденного бревна");
        if (!known.contains(log)) {
            live.add(log);
        }
    }

    public void markKnown(Collection<BlockCoord> logs) {
        Objects.requireNonNull(
                logs, "logs не может быть null: разобранное дерево или отвергнутая группа несут свои брёвна");
        known.addAll(logs);
        live.removeAll(logs);
    }

    public boolean isFull() {
        return live.size() >= limit;
    }

    public int liveCount() {
        return live.size();
    }

    public boolean isKnown(BlockCoord log) {
        return known.contains(log);
    }

    public Optional<BlockCoord> nearest(int fromX, int fromY, int fromZ) {
        return TreeChooser.next(fromX, fromY, fromZ, List.copyOf(live), Set.of());
    }
}
