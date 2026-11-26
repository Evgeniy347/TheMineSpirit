package com.theminespirit.neoforge;

import com.theminespirit.core.building.PathGround;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class BuildSupplyNeed {
    private BuildSupplyNeed() {}

    static Map<Item, Integer> need(Container inventory, SchematicSite site, Predicate<Schematic.PlacedEntry> phase) {
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (Schematic.PlacedEntry entry : site.primaries()) {
            if (!site.matches(entry) && phase.test(entry)) {
                if (BlockItems.hasBlockItem(entry.block()) && !SchematicActions.isActionPlaced(entry.block())) {
                    need.merge(BlockItems.itemFor(entry.block()), 1, Integer::sum);
                    continue;
                }
                String raise = SchematicActions.raiseItem(entry.block());
                if (raise != null && raiseNeeded(site, entry)) {
                    need.merge(itemOf(raise), 1, Integer::sum);
                }
            }
        }
        int baseMissing = SchematicFieldBase.missingBaseCount(site);
        if (baseMissing > inventory.countItem(Items.DIRT)) {
            need.merge(Items.DIRT, baseMissing - inventory.countItem(Items.DIRT), Integer::sum);
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                need.computeIfPresent(
                        stack.getItem(), (item, count) -> count > stack.getCount() ? count - stack.getCount() : null);
            }
        }
        return need;
    }

    private static boolean raiseNeeded(SchematicSite site, Schematic.PlacedEntry entry) {
        return PathGround.airCell(BlockStates.idOf(site.level.getBlockState(SchematicSite.toPos(entry.pos()))));
    }

    private static Item itemOf(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (Items.AIR.equals(item)) {
            throw new IllegalArgumentException("Нет предмета '" + id + "' для подвоза: проверьте id в схеме");
        }
        return item;
    }
}
