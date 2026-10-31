package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.script.ContainerTarget;
import com.theminespirit.neoforge.SpiritEntity;
import org.jspecify.annotations.Nullable;

final class ContainerScan {
    record Choice(
            @Nullable ContainerFinder finder, @Nullable AutoContainerScan scan) {}

    private ContainerScan() {}

    static Choice of(SpiritEntity spirit, ContainerTarget target, int radius) {
        if (target instanceof ContainerTarget.Auto) {
            return new Choice(null, new AutoContainerScan(spirit.level(), spirit.blockPosition(), radius));
        }
        if (target instanceof ContainerTarget.Warehouse) {
            return new Choice(null, AutoContainerScan.warehouse(spirit.level(), spirit.blockPosition(), false));
        }
        if (target instanceof ContainerTarget.Yard) {
            return new Choice(null, AutoContainerScan.warehouse(spirit.level(), spirit.blockPosition(), true));
        }
        return new Choice(new ContainerFinder(spirit.level(), spirit.blockPosition(), target, radius), null);
    }
}
