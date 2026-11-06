package com.theminespirit.neoforge.board;

import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;

final class GraphCrew {
    private GraphCrew() {}

    static int miners(List<SpiritEntity> spirits) {
        int miners = 0;
        for (SpiritEntity spirit : spirits) {
            var inventory = spirit.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (inventory.getItem(slot).getItem() instanceof PickaxeItem) {
                    miners++;
                    break;
                }
            }
        }
        return miners;
    }

    static Optional<UUID> furnaceHolder(List<SpiritEntity> spirits) {
        return spirits.stream()
                .filter(spirit -> !spirit.isRemoved() && spirit.getInventory().countItem(Items.FURNACE) > 0)
                .map(SpiritEntity::getUUID)
                .findFirst();
    }
}
