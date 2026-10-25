package com.theminespirit.core.planner;

import com.theminespirit.core.plan.ScriptCall;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FreshSupplies implements AcquireSupplies {
    private static final String NAMESPACE = "minecraft:";

    private final AcquireSupplies delegate;
    private final String item;

    public FreshSupplies(AcquireSupplies delegate, String item) {
        this.delegate =
                Objects.requireNonNull(delegate, "delegate не может быть null: инвентарь читает исходное снабжение");
        this.item =
                normalize(Objects.requireNonNull(item, "item не может быть null: не понять, что не брать из сундуков"));
    }

    @Override
    public int inInventory(String id) {
        return delegate.inInventory(id);
    }

    @Override
    public List<ScriptCall> withdraw(String id, int maxCount) {
        return item.equals(normalize(id)) ? List.of() : delegate.withdraw(id, maxCount);
    }

    @Override
    public Map<String, Integer> inventory() {
        return delegate.inventory();
    }

    @Override
    public Set<String> nearbyStations() {
        return delegate.nearbyStations();
    }

    private static String normalize(String id) {
        return id.startsWith(NAMESPACE) ? id.substring(NAMESPACE.length()) : id;
    }
}
