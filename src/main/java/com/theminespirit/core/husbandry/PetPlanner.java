package com.theminespirit.core.husbandry;

import java.util.Objects;

public final class PetPlanner {
    public enum Action {
        NONE,
        TAME,
        TRUST,
        BREED
    }

    private PetPlanner() {}

    public static Action action(PetKind kind, int owned, int wild, int spirits) {
        Objects.requireNonNull(kind, "Вид питомца не может быть null: без него неизвестно, что делать");
        if (owned < 0 || wild < 0 || spirits < 0) {
            throw new IllegalArgumentException("Поголовье " + owned + ", диких " + wild + ", духов " + spirits
                    + ": отрицательных чисел не бывает, иначе лимит прятал бы перебор");
        }
        if (owned >= PetRules.cap(kind, spirits)) {
            return Action.NONE;
        }
        if (wild > 0 && (kind.trust() || owned < 2)) {
            return kind.trust() ? Action.TRUST : Action.TAME;
        }
        return owned >= 2 ? Action.BREED : Action.NONE;
    }
}
