package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.inventory.NeedsOfPlan;
import com.theminespirit.core.inventory.OverflowPolicy;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.planner.ResourceKnowledge;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public final class OverflowDecider {
    private final SpiritEntity spirit;
    private ResourceKnowledge recipes;

    public OverflowDecider(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public OverflowPolicy.Decision decide(ItemStack heap) {
        StackMapper mapper = new StackMapper();
        List<Stack> inventory = mapper.map(spirit.getInventory());
        return OverflowPolicy.decide(inventory, mapper.of(heap), InventoryTables.values(), planNeeds(),
                InventoryTables.kit());
    }

    private List<String> planNeeds() {
        if (recipes == null) {
            recipes = RecipeKnowledge.of(spirit.level());
        }
        return spirit.currentPlan().map(plan -> NeedsOfPlan.collect(plan, recipes)).orElse(List.of());
    }
}
