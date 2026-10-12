package com.theminespirit.neoforge;

import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.DropRef;
import com.theminespirit.core.pickup.DropRoute;
import com.theminespirit.core.pickup.PickupTuning;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public final class DropCollector {
    public enum State {
        RUNNING,
        DONE,
        FAILED
    }

    private final SpiritEntity spirit;
    private final IdFilter items;
    private final AABB area;
    private final PickupTuning tuning;
    private final Approach approach;
    private final LevelSafetyView safety;
    private final Set<String> skipped = new HashSet<>();
    private List<ItemEntity> nearby = List.of();
    private ItemEntity target;
    private String failureReason;
    private int elapsed;
    private int ticksSinceQuery;
    private int entityQueries;
    private int collected;
    private int delayWaited;
    private boolean queriedThisTick;

    public DropCollector(SpiritEntity spirit, IdFilter items, BlockCoord center, int radius, PickupTuning tuning) {
        this.spirit = spirit;
        this.items = items;
        this.tuning = tuning;
        this.approach = new Approach(spirit);
        this.safety = new LevelSafetyView(spirit.level());
        this.area = new AABB(center.x() - radius, center.y() - radius, center.z() - radius,
                center.x() + radius + 1.0, center.y() + radius + 1.0, center.z() + radius + 1.0);
    }

    public State tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез во время подбора");
        }
        if (elapsed++ >= tuning.totalTimeoutTicks()) {
            return fail("не успел подобрать предметы за " + tuning.totalTimeoutTicks() + " тиков");
        }
        queriedThisTick = false;
        if (entityQueries == 0 || ticksSinceQuery >= tuning.refreshTicks()) {
            refresh();
        }
        ticksSinceQuery++;
        if (target != null && (target.isRemoved() || skipped.contains(target.getStringUUID()))) {
            approach.cancel();
            target = null;
        }
        if (target == null) {
            target = chooseTarget();
            if (target == null && !queriedThisTick) {
                refresh();
                target = chooseTarget();
            }
            if (target == null) {
                return conclude();
            }
            delayWaited = 0;
        }
        return pursue();
    }

    public void cancel() {
        approach.cancel();
    }

    public String failureReason() {
        return failureReason;
    }

    public int collected() {
        return collected;
    }

    public int entityQueries() {
        return entityQueries;
    }

    private void refresh() {
        nearby = spirit.level().getEntitiesOfClass(ItemEntity.class, area,
                drop -> !drop.isRemoved() && addressedToAnyone(drop) && items.matches(itemId(drop.getItem())));
        entityQueries++;
        ticksSinceQuery = 0;
        queriedThisTick = true;
    }

    private ItemEntity chooseTarget() {
        Map<String, ItemEntity> byId = new HashMap<>();
        List<DropRef> refs = new ArrayList<>();
        for (ItemEntity drop : nearby) {
            if (!drop.isRemoved()) {
                byId.put(drop.getStringUUID(), drop);
                refs.add(toRef(drop));
            }
        }
        Optional<DropRef> next = DropRoute.next(spirit.getX(), spirit.getY(), spirit.getZ(), refs, skipped);
        return next.map(ref -> byId.get(ref.id())).orElse(null);
    }

    private State conclude() {
        DropRef unfit = null;
        int unreachable = 0;
        for (ItemEntity drop : nearby) {
            if (drop.isRemoved()) {
                continue;
            }
            DropRef ref = toRef(drop);
            if (skipped.contains(ref.id())) {
                unreachable += ref.count();
            } else if (!ref.pickable() && (unfit == null || distanceToSpirit(ref) < distanceToSpirit(unfit))) {
                unfit = ref;
            }
        }
        if (unfit != null) {
            return fail("инвентарь полон: не помещается " + unfit.item());
        }
        if (unreachable > 0) {
            return fail("не дотянулся до " + unreachable + " предметов");
        }
        return State.DONE;
    }

    private State pursue() {
        if (spirit.getBoundingBox().distanceToSqr(target.position())
                <= tuning.pickupReach() * tuning.pickupReach()) {
            approach.cancel();
            return pickUp();
        }
        if (!approach.isActive()) {
            approach.begin(target, tuning.pickupReach());
        }
        Approach.State state = approach.tick();
        if (state == Approach.State.MOVING && spirit.getNavigation().isDone() && canStepOnItem()) {
            spirit.getMoveControl().setWantedPosition(target.getX(), spirit.getY(), target.getZ(), 1.0);
        }
        return switch (state) {
            case MOVING -> State.RUNNING;
            case ARRIVED -> pickUp();
            case STUCK, TIMEOUT -> giveUpOnTarget();
        };
    }

    private boolean canStepOnItem() {
        BlockPos itemCell = target.blockPosition();
        BlockPos feet = spirit.blockPosition();
        return MiningSafety.canStepFrom(
                new BlockCoord(feet.getX(), feet.getY(), feet.getZ()),
                new BlockCoord(itemCell.getX(), itemCell.getY(), itemCell.getZ()), safety);
    }

    private boolean addressedToAnyone(ItemEntity drop) {
        return drop.getTarget() == null || drop.getTarget().equals(spirit.getUUID());
    }

    private State giveUpOnTarget() {
        skipped.add(target.getStringUUID());
        target = null;
        return State.RUNNING;
    }

    private State pickUp() {
        if (target.hasPickUpDelay()) {
            return ++delayWaited > tuning.maxDelayWaitTicks() ? giveUpOnTarget() : State.RUNNING;
        }
        ItemStack stack = target.getItem();
        ItemStack rest = spirit.getInventory().addItem(stack.copy());
        int taken = stack.getCount() - rest.getCount();
        if (taken == 0) {
            return fail("инвентарь полон: не помещается " + itemId(stack));
        }
        spirit.take(target, taken);
        collected += taken;
        if (rest.isEmpty()) {
            target.discard();
        } else {
            target.setItem(rest);
        }
        target = null;
        return State.RUNNING;
    }

    private DropRef toRef(ItemEntity drop) {
        ItemStack stack = drop.getItem();
        return new DropRef(drop.getStringUUID(), itemId(stack), stack.getCount(), drop.getX(), drop.getY(), drop.getZ(),
                spirit.getInventory().canAddItem(stack));
    }

    private double distanceToSpirit(DropRef ref) {
        double dx = ref.x() - spirit.getX();
        double dy = ref.y() - spirit.getY();
        double dz = ref.z() - spirit.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private State fail(String reason) {
        approach.cancel();
        failureReason = reason;
        return State.FAILED;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
