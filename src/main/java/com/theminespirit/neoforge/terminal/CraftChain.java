package com.theminespirit.neoforge.terminal;

import com.theminespirit.core.crafting.ChainCraft;
import com.theminespirit.core.crafting.ChainPlan;
import com.theminespirit.core.crafting.RecipeIndex;
import com.theminespirit.core.crafting.RecipeSpec;
import com.theminespirit.neoforge.crafting.ItemIds;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import com.theminespirit.neoforge.crafting.RecipeSource;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel.ChainReply;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel.Line;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class CraftChain {
    private static final String KEY = "theminespirit.craft.";

    private CraftChain() {}

    public static ChainReply chain(ServerPlayer player, String target, boolean byRecipe, int requested, boolean run) {
        Optional<SettlementStorage> storage = CraftTerminalService.storage(player);
        if (storage.isEmpty()) {
            return refusal(target, CraftTerminalService.denial(CraftTerminalService.access(player)));
        }
        ServerLevel level = player.serverLevel();
        Map<String, Integer> stock = storage.get().freePlain();
        RecipeIndex index = RecipeKnowledge.of(level).index();
        Optional<RecipeSpec> recipe = byRecipe
                ? RecipeGrid.crafting(level, target)
                        .flatMap(found -> RecipeSource.crafting(found, level.registryAccess()))
                : ChainCraft.ofItem(index, ItemIds.normalize(target), 1, stock).map(ChainPlan::target);
        if (recipe.isEmpty()) {
            return refusal(target, Component.translatable(KEY + "no_recipe"));
        }
        RecipeSpec spec = recipe.get();
        int limit = Math.max(
                1,
                ItemIds.item(spec.outputItem())
                                .map(item -> new ItemStack(item).getMaxStackSize())
                                .orElse(CraftTerminalService.MAX_REQUEST)
                        / spec.outputCount());
        int max = ChainCraft.maxCrafts(index, spec, stock, limit);
        int crafts = requested <= 0 ? Math.max(1, max) : Math.min(requested, CraftTerminalService.MAX_REQUEST);
        ChainPlan plan = ChainCraft.ofRecipe(index, spec, crafts, stock);
        Component notice = Component.empty();
        if (run) {
            ChainExecutor.Outcome outcome = ChainExecutor.run(level, storage.get(), plan);
            if (outcome instanceof ChainExecutor.Done done) {
                done.targets().forEach(stack -> CraftTerminalService.give(player, stack, storage));
                done.extras().forEach(stack -> CraftTerminalService.give(player, stack, storage));
                notice = Component.translatable(
                        KEY + "crafted",
                        plan.targetCount(),
                        ItemIds.item(spec.outputItem())
                                .map(item -> new ItemStack(item).getHoverName())
                                .orElse(Component.literal(spec.outputItem())));
                CraftTerminalService.sendSnapshot(player);
                return reply(
                        spec, ChainCraft.ofRecipe(index, spec, 1, storage.get().freePlain()), max, notice);
            }
            notice = Component.literal(((ChainExecutor.Refused) outcome).reason());
        }
        return reply(spec, plan, max, notice);
    }

    private static ChainReply refusal(String target, Component notice) {
        return new ChainReply(target, ItemStack.EMPTY, 0, 0, 0, List.of(), List.of(), List.of(), notice);
    }

    private static ChainReply reply(RecipeSpec spec, ChainPlan plan, int max, Component notice) {
        ItemStack target = ItemIds.item(spec.outputItem()).map(ItemStack::new).orElse(ItemStack.EMPTY);
        return new ChainReply(
                spec.id(),
                target.copyWithCount(plan.targetCount()),
                plan.crafts(),
                max,
                plan.steps().size(),
                lines(plan.consumed()),
                lines(plan.leftovers()),
                lines(plan.missing()),
                notice);
    }

    static List<Line> lines(Map<String, Integer> items) {
        List<Line> lines = new ArrayList<>();
        items.forEach((id, count) ->
                ItemIds.item(id).ifPresent(item -> lines.add(new Line(new ItemStack(item), count))));
        return lines;
    }
}
