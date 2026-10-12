package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.ValueTable;
import java.util.concurrent.atomic.AtomicInteger;

public final class InventoryTables {
    private static volatile ValueTable values;
    private static volatile BaseKit kit;
    private static final AtomicInteger VALUE_LOADS = new AtomicInteger();
    private static final AtomicInteger KIT_LOADS = new AtomicInteger();

    private InventoryTables() {
    }

    public static ValueTable values() {
        ValueTable cached = values;
        if (cached == null) {
            synchronized (InventoryTables.class) {
                cached = values;
                if (cached == null) {
                    cached = ValueTable.load();
                    values = cached;
                    VALUE_LOADS.incrementAndGet();
                }
            }
        }
        return cached;
    }

    public static BaseKit kit() {
        BaseKit cached = kit;
        if (cached == null) {
            synchronized (InventoryTables.class) {
                cached = kit;
                if (cached == null) {
                    cached = BaseKit.load();
                    kit = cached;
                    KIT_LOADS.incrementAndGet();
                }
            }
        }
        return cached;
    }

    public static int valueLoads() {
        return VALUE_LOADS.get();
    }

    public static int kitLoads() {
        return KIT_LOADS.get();
    }
}
