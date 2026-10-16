package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.crafting.CraftTuning;
import com.theminespirit.core.crafting.FuelLoad;
import com.theminespirit.core.crafting.SmeltTiming;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.experience.ExperienceAwards;
import com.theminespirit.neoforge.experience.FurnaceExperience;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class FurnaceSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private static final int SLOT_INPUT = 0;
    private static final int SLOT_FUEL = 1;
    private static final int SLOT_RESULT = 2;

    private record FuelPortion(Item fuel, int count) {
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final BlockPos pos;
    private final Item input;
    private final int wanted;
    private final Deque<FuelPortion> fuelQueue = new ArrayDeque<>();
    private final int timeoutTicks;
    private int inputQueue;
    private int collected;
    private int ticks;
    private int polls;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;

    public record Contents(int input, int fuel, int result) {
        public boolean isEmpty() {
            return input == 0 && fuel == 0 && result == 0;
        }
    }

    public record Job(Item input, int inputToFeed, int outputPerInput, List<FuelLoad.Portion> fuel, int cookTicks,
            int inputsInPlay, int collectedSoFar, int wantedTotal) {
    }

    public FurnaceSession(SpiritEntity spirit, BlockPos pos, Job job) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.pos = pos;
        this.input = job.input();
        this.inputQueue = job.inputToFeed();
        this.wanted = job.wantedTotal();
        this.collected = job.collectedSoFar();
        this.timeoutTicks = SmeltTiming.timeoutTicks(Math.max(1, job.inputsInPlay()), job.cookTicks());
        for (FuelLoad.Portion portion : job.fuel()) {
            ItemIds.item(portion.fuel()).ifPresent(item -> fuelQueue.add(new FuelPortion(item, portion.count())));
        }
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время плавки: содержимое осталось в печи " + pos.toShortString());
        }
        Optional<AbstractFurnaceBlockEntity> furnace = furnaceAt();
        if (furnace.isEmpty()) {
            return fail("печь в " + pos.toShortString() + " исчезла: переплавлено " + collected + " из " + wanted);
        }
        if (ticks > 0 && ticks >= timeoutTicks) {
            return failAndReturn(furnace.get(), "печь не успела за " + timeoutTicks + " тиков: переплавлено "
                    + collected + " из " + wanted);
        }
        boolean pollNow = ticks % CraftTuning.POLL_TICKS == 0;
        ticks++;
        return pollNow ? poll(furnace.get()) : Outcome.RUNNING;
    }

    public void cancel() {
        if (outcome == Outcome.RUNNING && !spirit.isRemoved()) {
            furnaceAt().ifPresent(this::giveBackContents);
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

    private Outcome poll(AbstractFurnaceBlockEntity furnace) {
        polls++;
        Optional<String> noRoom = collectResult(furnace);
        if (noRoom.isPresent()) {
            return failAndReturn(furnace, noRoom.get());
        }
        if (collected >= wanted) {
            giveBackContents(furnace);
            outcome = Outcome.SUCCEEDED;
            return outcome;
        }
        if (fuelExhausted(furnace)) {
            return failAndReturn(furnace, "топливо кончилось: переплавлено " + collected + " из " + wanted);
        }
        feedInput(furnace);
        feedFuel(furnace);
        return Outcome.RUNNING;
    }

    private Optional<String> collectResult(AbstractFurnaceBlockEntity furnace) {
        ItemStack result = furnace.getItem(SLOT_RESULT);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        int before = result.getCount();
        ItemStack remainder = spirit.getInventory().addItem(result.copy());
        furnace.setItem(SLOT_RESULT, remainder);
        collected += before - remainder.getCount();
        if (!remainder.isEmpty()) {
            return Optional.of("нет места в инвентаре для продуктов: переплавлено " + collected + " из " + wanted);
        }
        awardWithdrawnExperience(furnace);
        return Optional.empty();
    }

    private void awardWithdrawnExperience(AbstractFurnaceBlockEntity furnace) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        ExperienceAwards.award(server, FurnaceExperience.withdraw(furnace, level));
    }

    private boolean fuelExhausted(AbstractFurnaceBlockEntity furnace) {
        boolean lit = furnace.getBlockState().getValue(AbstractFurnaceBlock.LIT);
        boolean fuelLeft = !furnace.getItem(SLOT_FUEL).isEmpty() || !fuelQueue.isEmpty();
        boolean workLeft = !furnace.getItem(SLOT_INPUT).isEmpty() || inputQueue > 0;
        return !lit && !fuelLeft && workLeft;
    }

    private void feedInput(AbstractFurnaceBlockEntity furnace) {
        ItemStack slot = furnace.getItem(SLOT_INPUT);
        if (inputQueue <= 0 || !(slot.isEmpty() || slot.is(input))) {
            return;
        }
        int room = input.getDefaultMaxStackSize() - slot.getCount();
        int moved = removeFromInventory(input, Math.min(inputQueue, room));
        if (moved > 0) {
            furnace.setItem(SLOT_INPUT, new ItemStack(input, slot.getCount() + moved));
            inputQueue -= moved;
        }
    }

    private void feedFuel(AbstractFurnaceBlockEntity furnace) {
        FuelPortion next = fuelQueue.peek();
        if (next == null || !furnace.getItem(SLOT_FUEL).isEmpty()) {
            return;
        }
        int moved = removeFromInventory(next.fuel(), Math.min(next.count(), next.fuel().getDefaultMaxStackSize()));
        fuelQueue.poll();
        if (moved > 0) {
            furnace.setItem(SLOT_FUEL, new ItemStack(next.fuel(), moved));
        }
        if (next.count() > moved && moved > 0) {
            fuelQueue.addFirst(new FuelPortion(next.fuel(), next.count() - moved));
        }
    }

    private int removeFromInventory(Item item, int wantedCount) {
        int available = Math.min(wantedCount, spirit.getInventory().countItem(item));
        return available > 0 ? spirit.getInventory().removeItemType(item, available).getCount() : 0;
    }

    private void giveBackContents(AbstractFurnaceBlockEntity furnace) {
        List<String> stuck = new ArrayList<>();
        for (int slot = SLOT_INPUT; slot <= SLOT_RESULT; slot++) {
            ItemStack stack = furnace.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = spirit.getInventory().addItem(stack.copy());
            furnace.setItem(slot, remainder);
            if (!remainder.isEmpty()) {
                stuck.add(remainder.getCount() + " " + ItemIds.of(remainder.getItem()));
            }
        }
        if (!stuck.isEmpty()) {
            StringJoiner joiner = new StringJoiner(", ");
            stuck.forEach(joiner::add);
            failureReason = (failureReason == null ? "" : failureReason + "; ") + "в печи осталось (нет места в "
                    + "инвентаре): " + joiner;
        }
    }

    public static Optional<Contents> contentsAt(Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (level.getBlockState(pos).is(Blocks.FURNACE) && entity instanceof AbstractFurnaceBlockEntity furnace) {
            return Optional.of(new Contents(furnace.getItem(SLOT_INPUT).getCount(), furnace.getItem(SLOT_FUEL).getCount(),
                    furnace.getItem(SLOT_RESULT).getCount()));
        }
        return Optional.empty();
    }

    private Optional<AbstractFurnaceBlockEntity> furnaceAt() {
        BlockEntity entity = level.getBlockEntity(pos);
        if (level.getBlockState(pos).is(Blocks.FURNACE) && entity instanceof AbstractFurnaceBlockEntity furnace) {
            return Optional.of(furnace);
        }
        return Optional.empty();
    }

    private Outcome failAndReturn(AbstractFurnaceBlockEntity furnace, String reason) {
        failureReason = reason;
        giveBackContents(furnace);
        outcome = Outcome.FAILED;
        return outcome;
    }

    private Outcome fail(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        return outcome;
    }
}
