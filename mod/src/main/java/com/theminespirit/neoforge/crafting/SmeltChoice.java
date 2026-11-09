package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.crafting.RecipeSpec;
import com.theminespirit.core.crafting.Stations;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;

final class SmeltChoice {
    record Choice(String input, RecipeSpec recipe, int inputCount, int have) {}

    private SmeltChoice() {}

    static Optional<FurnaceSession.Contents> stationContentsAt(Level level, BlockPos pos, String product) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof CampfireBlockEntity campfire) {
            if (!Stations.isFoodProduct(product)) {
                return Optional.empty();
            }
            int onFire = 0;
            for (ItemStack stack : campfire.getItems()) {
                onFire += stack.getCount();
            }
            return Optional.of(new FurnaceSession.Contents(onFire, 0, 0));
        }
        return FurnaceSession.contentsAt(level, pos);
    }

    static Choice choose(
            List<RecipeSpec> recipes, int remaining, FurnaceSession.Contents resident, Map<String, Integer> available) {
        Choice best = null;
        for (RecipeSpec recipe : recipes) {
            int perInput = recipe.outputCount();
            int stillToProduce = Math.max(0, remaining - resident.result() - resident.input() * perInput);
            int needed = (stillToProduce + perInput - 1) / perInput;
            for (String alternative : recipe.ingredients().get(0).alternatives()) {
                int have = available.getOrDefault(alternative, 0);
                if (have >= needed) {
                    return new Choice(alternative, recipe, needed, have);
                }
                if (best == null || have > best.have()) {
                    best = new Choice(alternative, recipe, needed, have);
                }
            }
        }
        return best;
    }
}
