package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.alchemy.BrewRecipes;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

final class BrewingSession {
    enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private static final int SLOT_FIRST_BOTTLE = 0;
    private static final int SLOT_LAST_BOTTLE = 2;
    private static final int SLOT_INGREDIENT = 3;
    private static final int SLOT_FUEL = 4;
    private static final int STEP_TIMEOUT = 900;

    private final SpiritEntity spirit;
    private final Level level;
    private final String product;
    private final int wanted;
    private final int radius;
    private final List<BrewRecipes.Step> steps;
    private final StationAccess station;
    private final long startedAt;
    private BlockPos pos;
    private ItemStack expected;
    private int step;
    private int made;
    private int batchLeft;
    private int ticks;
    private boolean running;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;

    BrewingSession(SpiritEntity spirit, String product, int wanted, int radius) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.product = product;
        this.wanted = wanted;
        this.radius = radius;
        this.steps = BrewRecipes.steps(product)
                .orElseThrow(() ->
                        new IllegalArgumentException("Нет ванильного рецепта зелья '" + product + "': варить нечего"));
        this.station = new StationAccess(spirit, StationType.BREWING_STAND, radius);
        this.startedAt = spirit.level().getGameTime();
    }

    Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время варки");
        }
        if (pos == null) {
            return approach();
        }
        Optional<BrewingStandBlockEntity> stand = standAt();
        if (stand.isEmpty()) {
            return fail("варочная стойка в " + pos.toShortString() + " исчезла: сварено " + made + " из " + wanted);
        }
        if (!running) {
            return startBatch(stand.orElseThrow());
        }
        if (step >= steps.size()) {
            return finishBatch(stand.orElseThrow());
        }
        return brew(stand.orElseThrow());
    }

    void cancel() {
        station.cancel();
        if (pos != null && outcome == Outcome.RUNNING) {
            standAt().ifPresent(this::giveBackBottles);
        }
    }

    String failureReason() {
        return failureReason;
    }

    private Outcome approach() {
        return switch (station.tick()) {
            case WORKING -> Outcome.RUNNING;
            case READY -> {
                pos = station.pos();
                yield Outcome.RUNNING;
            }
            case FAILED -> fail(station.failureReason());
        };
    }

    private Outcome startBatch(BrewingStandBlockEntity stand) {
        int batch = Math.min(BrewRecipes.BOTTLES_PER_BREW, wanted - made);
        if (batch <= 0) {
            outcome = Outcome.SUCCEEDED;
            return outcome;
        }
        if (!ensureWater(batch)) {
            return Outcome.FAILED;
        }
        int placed = 0;
        for (int slot = SLOT_FIRST_BOTTLE; slot <= SLOT_LAST_BOTTLE && placed < batch; slot++) {
            if (!stand.getItem(slot).isEmpty()) {
                continue;
            }
            ItemStack water = BrewBottles.take(spirit);
            if (water.isEmpty()) {
                break;
            }
            stand.setItem(slot, water);
            placed++;
        }
        if (placed == 0) {
            return fail("в инвентаре нет бутылок воды для варки " + product);
        }
        batchLeft = placed;
        expected = BrewBottles.waterPotion();
        step = 0;
        ticks = 0;
        running = true;
        return Outcome.RUNNING;
    }

    private Outcome brew(BrewingStandBlockEntity stand) {
        BrewRecipes.Step current = steps.get(step);
        Item ingredient = item(current.ingredient());
        if (ingredient == null) {
            return fail("ингредиент '" + current.ingredient() + "' отсутствует в реестре предметов");
        }
        if (stand.getItem(SLOT_INGREDIENT).isEmpty() && !feed(stand, SLOT_INGREDIENT, ingredient, 1)) {
            return fail("не хватает ингредиента: " + current.ingredient());
        }
        if (stand.getItem(SLOT_FUEL).isEmpty() && !feed(stand, SLOT_FUEL, Items.BLAZE_POWDER, 1)) {
            return fail("нет топлива: духу нужен огненный порошок (blaze_powder)");
        }
        ItemStack result = mixed(stand.getItem(SLOT_INGREDIENT));
        if (result.isEmpty()) {
            return fail("в ванильных рецептах нет варки '" + product + "' из '" + current.ingredient() + "'");
        }
        if (bottlesReady(stand, result)) {
            expected = result;
            step++;
            ticks = 0;
            return Outcome.RUNNING;
        }
        if (++ticks > STEP_TIMEOUT) {
            return fail("стойка не сварила шаг " + (step + 1) + " зелья " + product + " за " + STEP_TIMEOUT + " тиков");
        }
        return Outcome.RUNNING;
    }

    private Outcome finishBatch(BrewingStandBlockEntity stand) {
        made += collect(stand);
        running = false;
        for (int slot = SLOT_FIRST_BOTTLE; slot <= SLOT_LAST_BOTTLE; slot++) {
            if (!stand.getItem(slot).isEmpty()) {
                return fail("нет места в инвентаре для зелий: сварено " + made + " из " + wanted);
            }
        }
        if (made >= wanted) {
            outcome = Outcome.SUCCEEDED;
            return outcome;
        }
        return Outcome.RUNNING;
    }

    private boolean ensureWater(int bottles) {
        Optional<String> error = BrewBottles.ensure(spirit, bottles, radius);
        if (error.isPresent()) {
            fail(error.orElseThrow());
            return false;
        }
        return true;
    }

    private boolean feed(BrewingStandBlockEntity stand, int slot, Item item, int count) {
        int have = Math.min(count, spirit.getInventory().countItem(item));
        if (have <= 0) {
            return false;
        }
        spirit.getInventory().removeItemType(item, have);
        stand.setItem(slot, new ItemStack(item, have));
        return true;
    }

    private ItemStack mixed(ItemStack ingredient) {
        if (!(level instanceof ServerLevel server)) {
            return ItemStack.EMPTY;
        }
        ItemStack result = server.potionBrewing().mix(expected, ingredient);
        if (result.isEmpty() || ItemStack.isSameItemSameComponents(result, expected)) {
            return ItemStack.EMPTY;
        }
        return result;
    }

    private boolean bottlesReady(BrewingStandBlockEntity stand, ItemStack result) {
        int seen = 0;
        for (int slot = SLOT_FIRST_BOTTLE; slot <= SLOT_LAST_BOTTLE; slot++) {
            ItemStack stack = stand.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(stack, result)) {
                return false;
            }
            seen++;
        }
        return seen > 0;
    }

    private int collect(BrewingStandBlockEntity stand) {
        int collected = 0;
        for (int slot = SLOT_FIRST_BOTTLE; slot <= SLOT_LAST_BOTTLE; slot++) {
            ItemStack stack = stand.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = spirit.getInventory().addItem(stack.copy());
            stand.setItem(slot, rest);
            if (rest.isEmpty()) {
                collected += stack.getCount();
            }
        }
        return collected;
    }

    private void giveBackBottles(BrewingStandBlockEntity stand) {
        collect(stand);
        for (int slot = SLOT_INGREDIENT; slot <= SLOT_FUEL; slot++) {
            ItemStack stack = stand.getItem(slot);
            if (!stack.isEmpty()) {
                stand.setItem(slot, spirit.getInventory().addItem(stack.copy()));
            }
        }
    }

    private Optional<BrewingStandBlockEntity> standAt() {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof BrewingStandBlockEntity stand ? Optional.of(stand) : Optional.empty();
    }

    private static Item item(String id) {
        return ItemIds.item(id).orElse(null);
    }

    private Outcome fail(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        return outcome;
    }

    long startedAt() {
        return startedAt;
    }

    String product() {
        return product;
    }

    int made() {
        return made;
    }

    void restoreMade(int count) {
        made = Math.max(made, count);
    }

    int batchLeft() {
        return batchLeft;
    }
}
