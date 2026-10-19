package com.theminespirit.core.policy;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class PolicyExemptions {
    private static final Set<PolicyId> NON_EXEMPTIBLE =
            EnumSet.of(PolicyId.LAVA_FIRE, PolicyId.AIR, PolicyId.EXPLOSIVE);
    private static final PolicyExemptions NONE = new PolicyExemptions(EnumSet.noneOf(PolicyId.class));

    private final Set<PolicyId> exemptions;

    private PolicyExemptions(EnumSet<PolicyId> exemptions) {
        this.exemptions = Set.copyOf(exemptions);
    }

    public static PolicyExemptions of(PolicyId... ids) {
        EnumSet<PolicyId> chosen = EnumSet.noneOf(PolicyId.class);
        for (PolicyId id : ids) {
            Objects.requireNonNull(
                    id, "Политика в списке исключений не может быть null: проверьте вызов PolicyExemptions.of");
            if (NON_EXEMPTIBLE.contains(id)) {
                throw new IllegalArgumentException(
                        "Политика " + id + " не может иметь исключения: она защищает жизнь духа и работает всегда, "
                                + "уберите её из policyExemptions() скрипта");
            }
            chosen.add(id);
        }
        return chosen.isEmpty() ? NONE : new PolicyExemptions(chosen);
    }

    public static PolicyExemptions empty() {
        return NONE;
    }

    public boolean has(PolicyId id) {
        return exemptions.contains(id);
    }

    public PolicyExemptions union(PolicyExemptions other) {
        Objects.requireNonNull(
                other, "Исключения для объединения не могут быть null: пустые задаются PolicyExemptions.empty()");
        if (other.exemptions.isEmpty()) {
            return this;
        }
        if (exemptions.isEmpty()) {
            return other;
        }
        EnumSet<PolicyId> merged = EnumSet.copyOf(exemptions);
        merged.addAll(other.exemptions);
        return new PolicyExemptions(merged);
    }

    public Set<PolicyId> all() {
        return exemptions;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PolicyExemptions that && exemptions.equals(that.exemptions);
    }

    @Override
    public int hashCode() {
        return exemptions.hashCode();
    }

    @Override
    public String toString() {
        return "PolicyExemptions" + new TreeSet<>(exemptions);
    }
}
