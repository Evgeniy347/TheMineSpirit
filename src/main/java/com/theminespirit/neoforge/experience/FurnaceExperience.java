package com.theminespirit.neoforge.experience;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * Забирает накопленный печью опыт плавки, не бросая сферы: ванильный
 * {@link AbstractFurnaceBlockEntity#awardUsedRecipesAndPopExperience} отдаёт опыт сферами тому, кто забрал
 * результат, а дух только складывает результат в свой инвентарь. Здесь тот же опыт считается из карты
 * использованных рецептов и возвращается числом вызывающему, а карта очищается, как в ванили.
 */
public final class FurnaceExperience {
    private FurnaceExperience() {}

    public static int withdraw(AbstractFurnaceBlockEntity furnace, Level level) {
        if (furnace.recipesUsed.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Object2IntMap.Entry<ResourceLocation> entry : furnace.recipesUsed.object2IntEntrySet()) {
            Optional<RecipeHolder<?>> recipe = level.getRecipeManager().byKey(entry.getKey());
            if (recipe.isPresent() && recipe.get().value() instanceof AbstractCookingRecipe cooking) {
                total += experience(entry.getIntValue(), cooking.getExperience(), level);
            }
        }
        furnace.recipesUsed.clear();
        return total;
    }

    private static int experience(int smelts, float perItem, Level level) {
        float exact = smelts * perItem;
        int whole = Mth.floor(exact);
        float fraction = Mth.frac(exact);
        return fraction != 0.0F && level.getRandom().nextFloat() < fraction ? whole + 1 : whole;
    }
}
