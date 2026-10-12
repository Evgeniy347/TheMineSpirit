package com.theminespirit.core.policy;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class PolicyExemptions {
    private static final Set<PolicyId> NON_EXEMPTIBLE = EnumSet.of(
            PolicyId.LAVA_FIRE,
            PolicyId.AIR,
            PolicyId.EXPLOSIVE
    );

    private final EnumSet<PolicyId> exemptions;

    private PolicyExemptions(EnumSet<PolicyId> exemptions) {
        this.exemptions = exemptions;
    }

    public static PolicyExemptions of(PolicyId... ids) {
        for (PolicyId id : ids) {
            if (NON_EXEMPTIBLE.contains(id)) {
                throw new IllegalArgumentException("Политика " + id + " не может иметь исключения: она защищает жизнь духа и работает всегда");
            }
        }
        return new PolicyExemptions(EnumSet.copyOf(Set.of(ids)));
    }

    public static PolicyExemptions empty() {
        return new PolicyExemptions(EnumSet.noneOf(PolicyId.class));
    }

    public boolean has(PolicyId id) {
        return exemptions.contains(id);
    }

    public Set<PolicyId> all() {
        return Set.copyOf(exemptions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PolicyExemptions that)) return false;
        return Objects.equals(exemptions, that.exemptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exemptions);
    }
}