package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Set;

public record NotATree(Reason reason, Set<BlockCoord> logs) implements TreeDetection {
    public NotATree {
        Objects.requireNonNull(reason, "reason не может быть null: по причине ведётся счёт отвергнутых деревьев");
        Objects.requireNonNull(logs, "logs не может быть null: адаптер заносит брёвна отвергнутой группы в known");
        logs = Set.copyOf(logs);
    }

    public enum Reason {
        NO_LEAVES,
        NO_SOIL,
        TOO_BIG
    }
}