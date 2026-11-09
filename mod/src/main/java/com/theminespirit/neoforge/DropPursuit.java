package com.theminespirit.neoforge;

import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.pickup.PickupSpot;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class DropPursuit {
    private static final double STAND_ARRIVAL = 0.2;

    private final DropCollector owner;

    DropPursuit(DropCollector owner) {
        this.owner = owner;
    }

    ItemEntity chooseTarget() {
        List<ItemEntity> candidates = new ArrayList<>();
        List<WorkTarget> targets = new ArrayList<>();
        for (ItemEntity drop : owner.nearby) {
            if (!drop.isRemoved() && !owner.skipped.contains(drop.getStringUUID())) {
                candidates.add(drop);
                targets.add(dropTarget(drop));
            }
        }
        Optional<WorkTarget> pick = WorkReserveHub.choose(owner.spirit, targets);
        if (pick.isEmpty() || !WorkReserveHub.reserve(owner.spirit, pick.get())) {
            releaseHeld();
            return null;
        }
        owner.held = pick.get();
        return candidates.stream()
                .filter(drop -> dropTarget(drop).equals(owner.held))
                .findFirst()
                .orElse(null);
    }

    private static WorkTarget dropTarget(ItemEntity drop) {
        BlockPos pos = drop.blockPosition();
        return new WorkTarget(WorkKind.DROP, new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
    }

    void releaseHeld() {
        if (owner.held != null) {
            WorkReserveHub.release(owner.spirit, owner.held);
            owner.held = null;
        }
    }

    DropCollector.State pursue() {
        if (withinPickupReach()) {
            owner.approach.cancel();
            return owner.pickup.pickUp();
        }
        if (owner.target.blockPosition().equals(owner.spirit.blockPosition())) {
            owner.approach.cancel();
            return owner.pickup.pickUp();
        }
        if (!owner.approach.isActive()) {
            beginApproach();
        }
        Approach.State state = owner.approach.tick();
        if (withinPickupReach()) {
            owner.approach.cancel();
            return owner.pickup.pickUp();
        }
        if (state == Approach.State.MOVING && owner.spirit.getNavigation().isDone() && canStepOnItem()) {
            owner.spirit
                    .getMoveControl()
                    .setWantedPosition(owner.target.getX(), owner.spirit.getY(), owner.target.getZ(), 1.0);
        }
        return switch (state) {
            case MOVING -> DropCollector.State.RUNNING;
            case ARRIVED, STUCK, TIMEOUT -> retryApproach();
        };
    }

    private DropCollector.State retryApproach() {
        owner.approach.cancel();
        if (owner.currentStand != null) {
            owner.failedStands.add(owner.currentStand);
        }
        owner.currentStand = null;
        owner.approachAttempts++;
        if (owner.approachAttempts < PickupTuning.MAX_APPROACH_ATTEMPTS && hasUntriedStand()) {
            return DropCollector.State.RUNNING;
        }
        return giveUpOnTarget();
    }

    private boolean hasUntriedStand() {
        BlockCoord access = PickupSpot.accessCell(blockOf(owner.target.blockPosition()), owner.safety);
        return PickupSpot.standsFor(access, blockOf(owner.spirit.blockPosition()), owner.safety).stream()
                .anyMatch(spot -> !spot.equals(access) && !owner.failedStands.contains(spot));
    }

    private void beginApproach() {
        BlockCoord access = PickupSpot.accessCell(blockOf(owner.target.blockPosition()), owner.safety);
        Optional<BlockCoord> stand =
                PickupSpot.standsFor(access, blockOf(owner.spirit.blockPosition()), owner.safety).stream()
                        .filter(spot -> !spot.equals(access) && !owner.failedStands.contains(spot))
                        .findFirst();
        if (stand.isPresent()) {
            BlockCoord spot = stand.get();
            owner.currentStand = spot;
            owner.approach.begin(new Vec3(spot.x() + 0.5, spot.y(), spot.z() + 0.5), STAND_ARRIVAL);
            return;
        }
        owner.currentStand = null;
        owner.approach.begin(owner.target, owner.tuning.pickupReach());
    }

    private boolean withinPickupReach() {
        double reach = owner.tuning.pickupReach();
        if (owner.spirit.getBoundingBox().distanceToSqr(owner.target.position()) <= reach * reach) {
            return true;
        }
        BlockCoord access = PickupSpot.accessCell(blockOf(owner.target.blockPosition()), owner.safety);
        if (owner.safety.cellAt(access) != Cell.HAZARD) {
            return false;
        }
        AABB cellBox =
                new AABB(access.x(), access.y(), access.z(), access.x() + 1.0, access.y() + 1.0, access.z() + 1.0);
        return owner.spirit.getBoundingBox().inflate(reach).intersects(cellBox);
    }

    private boolean canStepOnItem() {
        BlockPos itemCell = owner.target.blockPosition();
        BlockPos feet = owner.spirit.blockPosition();
        return MiningSafety.canStepFrom(
                new BlockCoord(feet.getX(), feet.getY(), feet.getZ()),
                new BlockCoord(itemCell.getX(), itemCell.getY(), itemCell.getZ()),
                owner.safety);
    }

    DropCollector.State giveUpOnTarget() {
        owner.skipped.add(owner.target.getStringUUID());
        releaseHeld();
        owner.target = null;
        return DropCollector.State.RUNNING;
    }

    private static BlockCoord blockOf(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
