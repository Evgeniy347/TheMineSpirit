package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.crafting.CraftTuning;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class StationAccess {
    enum State {
        WORKING,
        READY,
        FAILED
    }

    @FunctionalInterface
    interface Supply {
        boolean provide(SpiritEntity spirit);
    }

    private static final Supply NO_SUPPLY = spirit -> false;

    private final SpiritEntity spirit;
    private final StationType type;
    private final int radius;
    private final StationFinder finder;
    private final Approach approach;
    private final Supply supply;
    private State state = State.WORKING;
    private BlockPos pos;
    private String failureReason;

    StationAccess(SpiritEntity spirit, StationType type, int radius) {
        this(spirit, type, radius, NO_SUPPLY);
    }

    StationAccess(SpiritEntity spirit, StationType type, int radius, Supply supply) {
        this.spirit = spirit;
        this.type = type;
        this.radius = radius;
        this.supply = supply;
        this.finder = new StationFinder(spirit.level(), spirit.blockPosition(), radius, type);
        this.approach = new Approach(spirit);
    }

    State tick() {
        if (state != State.WORKING) {
            return state;
        }
        if (pos == null) {
            search();
        } else {
            walk();
        }
        return state;
    }

    void cancel() {
        approach.cancel();
    }

    BlockPos pos() {
        return pos;
    }

    String failureReason() {
        return failureReason;
    }

    boolean stillStanding() {
        return spirit.level().getBlockState(pos).is(type.block());
    }

    private void search() {
        if (!finder.step()) {
            return;
        }
        Optional<BlockPos> existing = finder.found();
        if (existing.isPresent()) {
            headTo(existing.get());
            return;
        }
        if (spirit.getInventory().countItem(type.item()) < 1 && !supply.provide(spirit)) {
            fail(type.needed() + ": рядом (" + radius + ") нет, в инвентаре нет");
            return;
        }
        Optional<BlockPos> placed = StationPlacer.place(spirit, type);
        if (placed.isEmpty()) {
            fail("негде поставить " + type.noun() + ": вокруг духа нет свободной клетки на твёрдом полу");
            return;
        }
        headTo(placed.get());
    }

    private void headTo(BlockPos target) {
        pos = target;
        approach.begin(Vec3.atCenterOf(target), CraftTuning.STATION_REACH);
        walk();
    }

    private void walk() {
        if (!stillStanding()) {
            approach.cancel();
            fail(type.noun() + " в " + pos.toShortString() + " исчез до начала работы");
            return;
        }
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> state = State.READY;
            case STUCK, TIMEOUT -> fail("не дошёл до станции (" + type.noun() + ") в " + pos.toShortString());
        }
    }

    private void fail(String reason) {
        failureReason = reason;
        state = State.FAILED;
    }
}
