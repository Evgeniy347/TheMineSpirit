package com.theminespirit.core.forestry;

import java.util.Objects;

public record NotATree(Reason reason) implements TreeDetection {
    public NotATree {
        Objects.requireNonNull(reason, "reason не может быть null: по причине ведётся счёт отвергнутых деревьев");
    }

    public enum Reason {
        NO_LEAVES,
        NO_SOIL,
        TOO_BIG
    }
}
