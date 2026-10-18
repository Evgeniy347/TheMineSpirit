package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.crafting.CraftTuning;
import com.theminespirit.core.crafting.SmeltTiming;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.phys.AABB;

public final class CampfireSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    public record Job(Item input, int inputToFeed, Item output, int cookTicks, int collectedSoFar, int wantedTotal) {
    }

    private static final double DROP_RADIUS = 2.0;

    private final SpiritEntity spirit;
    private final Level level;
    private final BlockPos pos;
    private final Item input;
    private final Item output;
    private final int cookTicks;
    private final int wanted;
    private final int timeoutTicks;
    private int inputQueue;
    private int collected;
    private int ticks;
    private int polls;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;

    public CampfireSession(SpiritEntity spirit, BlockPos pos, Job job) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.pos = pos;
        this.input = job.input();
        this.output = job.output();
        this.cookTicks = job.cookTicks();
        this.inputQueue = job.inputToFeed();
        this.wanted = job.wantedTotal();
        this.collected = job.collectedSoFar();
        this.timeoutTicks = SmeltTiming.campfireTimeoutTicks(Math.max(1, wanted - collected), cookTicks);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время готовки на костре: предметы остались на костре " + pos.toShortString());
        }
        Optional<CampfireBlockEntity> campfire = campfireAt();
        if (campfire.isEmpty()) {
            return fail("костёр в " + pos.toShortString() + " исчез: приготовлено " + collected + " из " + wanted);
        }
        if (ticks > 0 && ticks >= timeoutTicks) {
            takeBack(campfire.get());
            return fail("костёр не успел за " + timeoutTicks + " тиков: приготовлено " + collected + " из "
                    + wanted);
        }
        // Подбор готового — каждый тик, а не раз в POLL_TICKS: при ускоренной готовке (GameTest-помпа)
        // несколько предметов вызревают в один тик и падают одним местом; подбор раз в 10 тиков терял
        // часть выхода, и сессия висела в RUNNING с пустым костром (флак 18.10). Постановка сырья —
        // по-прежнему раз в POLL_TICKS, чтобы не дёргать слоты каждый тик.
        collectDrops();
        if (collected >= wanted) {
            takeBack(campfire.get());
            outcome = Outcome.SUCCEEDED;
            return outcome;
        }
        boolean pollNow = ticks % CraftTuning.POLL_TICKS == 0;
        ticks++;
        return pollNow ? poll(campfire.get()) : Outcome.RUNNING;
    }

    public void cancel() {
        if (outcome == Outcome.RUNNING && !spirit.isRemoved()) {
            campfireAt().ifPresent(this::takeBack);
        }
    }

    public String failureReason() {
        return failureReason;
    }

    public int collected() {
        return collected;
    }

    public int polls() {
        return polls;
    }

    public BlockPos pos() {
        return pos;
    }

    private Outcome poll(CampfireBlockEntity campfire) {
        polls++;
        placeFood(campfire);
        if (inputQueue > 0 && spirit.getInventory().countItem(input) < 1) {
            takeBack(campfire);
            return fail("сырьё кончилось: приготовлено " + collected + " из " + wanted);
        }
        return Outcome.RUNNING;
    }

    private void placeFood(CampfireBlockEntity campfire) {
        if (inputQueue <= 0) {
            return;
        }
        for (int slot = 0; slot < campfire.getItems().size() && inputQueue > 0; slot++) {
            if (!campfire.getItems().get(slot).isEmpty()) {
                continue;
            }
            if (spirit.getInventory().countItem(input) < 1) {
                return;
            }
            spirit.getInventory().removeItemType(input, 1);
            ItemStack one = new ItemStack(input, 1);
            if (!campfire.placeFood(spirit, one, cookTicks)) {
                spirit.getInventory().addItem(new ItemStack(input, 1));
                return;
            }
            inputQueue--;
        }
    }

    private void collectDrops() {
        AABB box = new AABB(pos).inflate(DROP_RADIUS);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            ItemStack stack = entity.getItem();
            if (!stack.is(output)) {
                continue;
            }
            ItemStack remainder = spirit.getInventory().addItem(stack.copy());
            collected += stack.getCount() - remainder.getCount();
            if (remainder.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(remainder);
            }
        }
    }

    private void takeBack(CampfireBlockEntity campfire) {
        for (int slot = 0; slot < campfire.getItems().size(); slot++) {
            ItemStack stack = campfire.getItems().get(slot);
            if (stack.isEmpty() || !stack.is(input)) {
                continue;
            }
            ItemStack remainder = spirit.getInventory().addItem(stack.copy());
            campfire.getItems().set(slot, remainder);
        }
        collectDrops();
    }

    private Optional<CampfireBlockEntity> campfireAt() {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof CampfireBlockEntity campfire && level.getBlockState(pos).is(Blocks.CAMPFIRE)) {
            return Optional.of(campfire);
        }
        return Optional.empty();
    }

    private Outcome fail(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        return outcome;
    }
}
