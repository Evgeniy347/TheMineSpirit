package com.theminespirit.core.policy;

import java.util.Objects;

public final class DemolitionVerdict {
    private DemolitionVerdict() {}

    public static boolean permissible(BlockProtection.Decision decision, boolean knownLeftover) {
        Objects.requireNonNull(decision, "Решение защиты не может быть null: без него не отличить остаток");
        if (knownLeftover) {
            return true;
        }
        return decision instanceof BlockProtection.Refused refused
                && (refused.policy() == PolicyId.PROTECTED_BLOCKS || refused.policy() == PolicyId.PROTECTED_ZONE);
    }
}
