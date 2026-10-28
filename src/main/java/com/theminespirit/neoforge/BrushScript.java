package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.script.BrushParams;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

final class BrushScript implements ScriptInstance {
    private static final String SCUTE = "minecraft:armadillo_scute";
    private static final double ARRIVAL = 2.0;
    private static final double REACH = 3.0;
    private static final int COLLECT_RADIUS = 4;
    private static final int BRUSH_DAMAGE = 16;
    private static final int TIMEOUT_TICKS = 2400;

    private enum State {
        FIND,
        APPROACH,
        BRUSH,
        COLLECT,
        SUCCEEDED,
        FAILED
    }

    private final SpiritEntity spirit;
    private final int radius;
    private final int goal;
    private final Approach approach;
    private State state = State.FIND;
    private @Nullable Armadillo target;
    private @Nullable DropCollector collector;
    private int done;
    private int elapsed;
    private String failure;

    BrushScript(SpiritEntity spirit, BrushParams params) {
        this.spirit = spirit;
        this.radius = params.radius();
        this.goal = params.count();
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (state == State.SUCCEEDED) {
            return ScriptStatus.SUCCEEDED;
        }
        if (state == State.FAILED) {
            return ScriptStatus.FAILED;
        }
        if (++elapsed > TIMEOUT_TICKS) {
            return fail("не успел почистить броненосцев за " + TIMEOUT_TICKS + " тиков: щетин " + done + " из " + goal);
        }
        return switch (state) {
            case FIND -> find();
            case APPROACH -> approach();
            case BRUSH -> brush();
            case COLLECT -> collect();
            case SUCCEEDED, FAILED -> throw new IllegalStateException("Состояние щётки " + state + " уже итоговое");
        };
    }

    private ScriptStatus find() {
        if (done >= goal) {
            state = State.SUCCEEDED;
            return ScriptStatus.SUCCEEDED;
        }
        target = spirit
                .level()
                .getEntitiesOfClass(Armadillo.class, spirit.getBoundingBox().inflate(radius))
                .stream()
                .filter(armadillo -> !armadillo.isRemoved() && armadillo.isAlive() && !armadillo.isBaby())
                .min(Comparator.comparingDouble(armadillo -> armadillo.distanceToSqr(spirit)))
                .orElse(null);
        if (target == null) {
            return fail("рядом нет взрослого броненосца в радиусе " + radius + ": щетин " + done + " из " + goal);
        }
        approach.begin(target, ARRIVAL);
        state = State.APPROACH;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus approach() {
        if (target == null || target.isRemoved()) {
            target = null;
            state = State.FIND;
            return ScriptStatus.RUNNING;
        }
        return switch (approach.tick()) {
            case MOVING -> ScriptStatus.RUNNING;
            case ARRIVED -> {
                state = State.BRUSH;
                yield ScriptStatus.RUNNING;
            }
            case STUCK, TIMEOUT -> {
                if (inReach(target)) {
                    state = State.BRUSH;
                } else {
                    target = null;
                    state = State.FIND;
                }
                yield ScriptStatus.RUNNING;
            }
        };
    }

    private ScriptStatus brush() {
        if (target == null || target.isRemoved() || target.isBaby()) {
            target = null;
            state = State.FIND;
            return ScriptStatus.RUNNING;
        }
        if (!inReach(target)) {
            approach.begin(target, ARRIVAL);
            state = State.APPROACH;
            return ScriptStatus.RUNNING;
        }
        ItemStack brush = brushStack();
        if (brush.isEmpty()) {
            return fail("нет щётки в инвентаре: броненосец не тронут, щетин " + done + " из " + goal);
        }
        if (!target.brushOffScute()) {
            target = null;
            state = State.FIND;
            return ScriptStatus.RUNNING;
        }
        brush.hurtAndBreak(BRUSH_DAMAGE, spirit, EquipmentSlot.MAINHAND);
        done++;
        BlockPos at = target.blockPosition();
        collector = new DropCollector(
                spirit,
                IdFilter.of(List.of(SCUTE)),
                new BlockCoord(at.getX(), at.getY(), at.getZ()),
                COLLECT_RADIUS,
                PickupTuning.DEFAULT,
                OverflowMode.forSpirit(spirit.isFollowingPlayer()));
        target = null;
        state = State.COLLECT;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus collect() {
        if (collector == null) {
            state = State.FIND;
            return ScriptStatus.RUNNING;
        }
        return switch (collector.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case DONE -> {
                collector = null;
                state = done >= goal ? State.SUCCEEDED : State.FIND;
                yield state == State.SUCCEEDED ? ScriptStatus.SUCCEEDED : ScriptStatus.RUNNING;
            }
            case FAILED -> fail("не удалось подобрать щетину: " + collector.failureReason());
        };
    }

    private boolean inReach(Armadillo armadillo) {
        return spirit.getEyePosition().distanceTo(armadillo.position()) <= REACH;
    }

    private ItemStack brushStack() {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(Items.BRUSH)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private ScriptStatus fail(String reason) {
        failure = reason;
        state = State.FAILED;
        return ScriptStatus.FAILED;
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(done, goal));
    }

    @Override
    public void cancel() {
        approach.cancel();
        if (collector != null) {
            collector.cancel();
        }
    }

    @Override
    public void suspend() {
        approach.suspend();
        if (collector != null) {
            collector.suspend();
        }
    }

    @Override
    public String failureReason() {
        return failure;
    }
}
