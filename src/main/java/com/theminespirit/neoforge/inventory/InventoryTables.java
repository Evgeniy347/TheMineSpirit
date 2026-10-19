package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.armor.ArmorTable;
import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.OverflowTuning;
import com.theminespirit.core.inventory.ValueTable;
import com.theminespirit.core.reload.DataTables;

public final class InventoryTables {
    private InventoryTables() {
    }

    public static ValueTable values() {
        return DataTables.INSTANCE.values().current();
    }

    public static BaseKit kit() {
        return DataTables.INSTANCE.kit().current();
    }

    public static OverflowTuning overflow() {
        return DataTables.INSTANCE.overflow().current();
    }

    public static ArmorTable armor() {
        return DataTables.INSTANCE.armor().current();
    }

    public static int valueLoads() {
        return DataTables.INSTANCE.valueSeeds();
    }

    public static int kitLoads() {
        return DataTables.INSTANCE.kitSeeds();
    }

}