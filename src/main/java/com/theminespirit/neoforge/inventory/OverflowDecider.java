package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.NeedsOfPlan;
import com.theminespirit.core.inventory.OverflowPolicy;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.planner.ResourceKnowledge;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

public final class OverflowDecider {
    private final SpiritEntity spirit;
    private ResourceKnowledge recipes;
    private long policyCalls;
    private String cachedItem;
    private ItemStack cachedHeap;
    private int cachedCount;
    private long cachedChanges;
    private long cachedGeneration;
    private List<String> cachedNeeds = List.of();
    private String cachedActivity;
    private boolean cachedChild;
    private OverflowPolicy.Decision cachedDecision;

    public OverflowDecider(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public OverflowPolicy.Decision decide(ItemStack heap) {
        String item = StackMapper.itemId(heap);
        long changes = spirit.getInventory().changeCount();
        long generation = spirit.taskGeneration();
        List<String> needs = planNeeds();
        String activity = spirit.currentActivity().orElse(null);
        boolean child = spirit.isChild();
        if (cacheHit(heap, item, changes, generation, needs, activity, child)) {
            return cachedDecision;
        }
        StackMapper mapper = new StackMapper();
        List<Stack> inventory = mapper.map(spirit.getInventory());
        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, mapper.of(heap),
                InventoryTables.values(), needs, KitFor.kitFor(activity, child));
        policyCalls++;
        cachedItem = item;
        cachedHeap = heap.copyWithCount(1);
        cachedCount = heap.getCount();
        cachedChanges = changes;
        cachedGeneration = generation;
        cachedNeeds = needs;
        cachedActivity = activity;
        cachedChild = child;
        cachedDecision = decision;
        return decision;
    }

    public long policyCalls() {
        return policyCalls;
    }

    private boolean cacheHit(ItemStack heap, String item, long changes, long generation, List<String> needs,
            String activity, boolean child) {
        return cachedDecision != null && cachedChanges == changes && cachedGeneration == generation
                && cachedCount == heap.getCount() && cachedItem.equals(item)
                && ItemStack.isSameItemSameComponents(cachedHeap, heap) && cachedNeeds.equals(needs)
                && Objects.equals(cachedActivity, activity) && cachedChild == child;
    }

    private List<String> planNeeds() {
        if (recipes == null) {
            recipes = RecipeKnowledge.of(spirit.level());
        }
        return spirit.currentPlan().map(plan -> NeedsOfPlan.collect(plan, recipes)).orElse(List.of());
    }
}
