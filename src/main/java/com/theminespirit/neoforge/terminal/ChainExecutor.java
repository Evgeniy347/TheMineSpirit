package com.theminespirit.neoforge.terminal;

import com.theminespirit.core.crafting.ChainPlan;
import com.theminespirit.core.crafting.ChainStep;
import com.theminespirit.neoforge.crafting.ItemIds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

final class ChainExecutor {
    private ChainExecutor() {}

    sealed interface Outcome permits Done, Refused {}

    record Done(List<ItemStack> targets, List<ItemStack> extras) implements Outcome {}

    record Refused(String reason) implements Outcome {}

    static Outcome run(ServerLevel level, SettlementStorage storage, ChainPlan plan) {
        if (!plan.feasible()) {
            return new Refused("план невыполним: не хватает " + plan.missing());
        }
        List<ItemStack> targets = new ArrayList<>();
        List<ItemStack> extras = new ArrayList<>();
        Optional<String> simulated = simulate(level, plan, targets, extras);
        if (simulated.isPresent()) {
            return new Refused(simulated.get());
        }
        List<ItemStack> taken = new ArrayList<>();
        for (Map.Entry<String, Integer> use : plan.consumed().entrySet()) {
            Optional<Item> item = ItemIds.item(use.getKey());
            int got = item.map(found -> take(storage, found, use.getValue(), taken))
                    .orElse(0);
            if (got < use.getValue()) {
                taken.forEach(stack -> storage.insert(stack));
                return new Refused("в сундуках поселения " + use.getKey() + " меньше, чем " + use.getValue()
                        + ": запас изменился между расчётом и крафтом, повторите");
            }
        }
        return new Done(targets, extras);
    }

    private static int take(SettlementStorage storage, Item item, int count, List<ItemStack> taken) {
        int got = 0;
        while (got < count) {
            ItemStack part = storage.extract(new ItemStack(item), count - got);
            if (part.isEmpty()) {
                break;
            }
            got += part.getCount();
            taken.add(part);
        }
        return got;
    }

    private static Optional<String> simulate(
            ServerLevel level, ChainPlan plan, List<ItemStack> targets, List<ItemStack> extras) {
        ItemStack target = ItemStack.EMPTY;
        for (int index = 0; index < plan.steps().size(); index++) {
            ChainStep step = plan.steps().get(index);
            Optional<RecipeHolder<CraftingRecipe>> holder =
                    RecipeGrid.crafting(level, step.recipe().id());
            if (holder.isEmpty()) {
                return Optional.of("рецепт " + step.recipe().id() + " пропал с сервера: перезагрузка данных?");
            }
            CraftingRecipe recipe = holder.get().value();
            for (int craft = 0; craft < step.crafts(); craft++) {
                List<ItemStack> items = new ArrayList<>();
                for (String id : step.itemsForCraft(craft)) {
                    items.add(ItemIds.item(id).map(ItemStack::new).orElse(ItemStack.EMPTY));
                }
                CraftingInput input = RecipeGrid.input(recipe, items);
                if (!recipe.matches(input, level)) {
                    return Optional.of("рецепт " + step.recipe().id() + " не принял раскладку " + items
                            + ": модель рецепта разошлась с игрой");
                }
                if (index == plan.steps().size() - 1 && target.isEmpty()) {
                    target = recipe.assemble(input, level.registryAccess());
                }
                for (ItemStack remainder : recipe.getRemainingItems(input)) {
                    if (!remainder.isEmpty()) {
                        extras.add(remainder.copy());
                    }
                }
            }
        }
        if (target.isEmpty()) {
            return Optional.of("цепочка не дошла до цели " + plan.target().id());
        }
        addSplit(targets, target, plan.targetCount());
        for (Map.Entry<String, Integer> left : plan.leftovers().entrySet()) {
            Optional<Item> item = ItemIds.item(left.getKey());
            item.ifPresent(found -> addSplit(extras, new ItemStack(found), left.getValue()));
        }
        return Optional.empty();
    }

    private static void addSplit(List<ItemStack> outputs, ItemStack sample, int count) {
        int left = count;
        while (left > 0) {
            int part = Math.min(left, sample.getMaxStackSize());
            outputs.add(sample.copyWithCount(part));
            left -= part;
        }
    }
}
