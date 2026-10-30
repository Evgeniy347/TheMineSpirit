package com.theminespirit.neoforge.hunger;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.hunger.HungerEffect;
import com.theminespirit.core.hunger.HungerRules;
import com.theminespirit.core.hunger.HungerTick;
import com.theminespirit.core.hunger.SpiritHunger;
import com.theminespirit.core.intellect.FoodNorm;
import com.theminespirit.core.intellect.NutritionTable;
import com.theminespirit.core.intellect.StockCatalog;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class HungerState {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final NutritionTable FOOD_TABLE = StockCatalog.defaultTable();
    private static final double MAX_STEP_DISTANCE = 4.0;

    private final SpiritEntity spirit;
    private SpiritHunger hunger = SpiritHunger.full();
    private Vec3 lastPos;

    public HungerState(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public SpiritHunger state() {
        return hunger;
    }

    public void restore(SpiritHunger restored) {
        hunger = restored;
        lastPos = null;
    }

    public HungerRules rules() {
        return HungerRules.loadDefault();
    }

    public double satietyFraction() {
        return hunger.satietyFraction();
    }

    public int foodLevel() {
        return hunger.foodLevel();
    }

    public String foodText() {
        return hunger.foodText();
    }

    public void tick() {
        spendOnMovement();
        HungerTick result = hunger.tick(rules(), spirit.getHealth(), spirit.getMaxHealth());
        hunger = result.hunger();
        apply(result.effect());
    }

    public void onAttack() {
        hunger = hunger.addExhaustion(rules().combatExhaustionPerAttack());
    }

    public void onBlockBroken() {
        hunger = hunger.addExhaustion(rules().digExhaustionPerBlock());
    }

    public void onHurt() {
        hunger = hunger.addExhaustion(rules().damageExhaustionPerHit());
    }

    public boolean hasFood() {
        return !bestFoodId().isEmpty();
    }

    public double personalNutrition() {
        double total = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.isEmpty() || stack.get(DataComponents.FOOD) == null) {
                continue;
            }
            Double satiety = FOOD_TABLE.nutrition().get(keyOf(stack));
            if (satiety != null) {
                total += satiety * stack.getCount();
            }
        }
        return total;
    }

    public String bestFoodId() {
        return FoodNorm.best(FOOD_TABLE, foodStock());
    }

    public boolean eatBest() {
        String id = bestFoodId();
        return !id.isEmpty() && eat(id);
    }

    public boolean eat(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException(
                    "Предмет для еды пуст: что съесть, решает вызывающий — id или «лучшее из инвентаря»");
        }
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.isEmpty() || !keyOf(stack).equals(itemId)) {
                continue;
            }
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) {
                continue;
            }
            hunger = hunger.eat(food.nutrition(), food.saturation());
            stack.shrink(1);
            LOGGER.debug("Дух {} съел {}: сытость {}", spirit.getUUID(), itemId, hunger.foodLevel());
            return true;
        }
        return false;
    }

    private Map<String, Integer> foodStock() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.isEmpty() || stack.get(DataComponents.FOOD) == null) {
                continue;
            }
            counts.merge(keyOf(stack), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static String keyOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private void spendOnMovement() {
        Vec3 now = spirit.position();
        if (lastPos == null) {
            lastPos = now;
            return;
        }
        double dx = now.x - lastPos.x;
        double dz = now.z - lastPos.z;
        lastPos = now;
        if (!spirit.isSprinting()) {
            return;
        }
        double step = Math.min(Math.sqrt(dx * dx + dz * dz), MAX_STEP_DISTANCE);
        if (step > 0) {
            hunger = hunger.addExhaustion(rules().sprintExhaustionPerMeter() * step);
        }
    }

    private void apply(HungerEffect effect) {
        switch (effect) {
            case HEAL -> spirit.heal(1.0F);
            case STARVE -> spirit.hurt(spirit.damageSources().starve(), 1.0F);
            case NONE -> {}
        }
    }
}
