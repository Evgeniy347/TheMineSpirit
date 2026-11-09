package com.theminespirit.neoforge;

import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.DropRef;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.neoforge.inventory.OverflowDecider;
import com.theminespirit.neoforge.inventory.UnloadScript;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DropCollector {
    public enum State {
        RUNNING,
        DONE,
        FAILED
    }

    final SpiritEntity spirit;
    private final IdFilter items;
    private final AABB area;
    private final long startTick;
    final PickupTuning tuning;
    final OverflowMode overflow;
    final OverflowDecider decider;
    final Approach approach;
    final LevelSafetyView safety;
    final Set<String> skipped = new HashSet<>();
    final Set<BlockCoord> failedStands = new HashSet<>();
    List<ItemEntity> nearby = List.of();
    ItemEntity target;
    BlockCoord currentStand;
    int approachAttempts;
    WorkTarget held;
    UnloadScript relief;
    boolean reliefTried;
    private String failureReason;
    private int elapsed;
    private int ticksSinceQuery;
    private int entityQueries;
    int collected;
    int delayWaited;
    private boolean queriedThisTick;
    final DropPursuit pursuit;
    final DropPickup pickup;

    public DropCollector(
            SpiritEntity spirit,
            IdFilter items,
            BlockCoord center,
            int radius,
            PickupTuning tuning,
            OverflowMode overflow) {
        this.spirit = spirit;
        this.items = items;
        this.tuning = tuning;
        this.overflow = overflow;
        this.decider = new OverflowDecider(spirit);
        this.approach = new Approach(spirit);
        this.safety = new LevelSafetyView(spirit.level());
        this.area = new AABB(
                center.x() - radius,
                center.y() - radius,
                center.z() - radius,
                center.x() + radius + 1.0,
                center.y() + radius + 1.0,
                center.z() + radius + 1.0);
        this.startTick = spirit.level().getGameTime();
        this.pickup = new DropPickup(this);
        this.pursuit = new DropPursuit(this);
    }

    public State tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез во время подбора");
        }
        if (relief != null) {
            return pickup.reliefTick();
        }
        if (elapsed++ >= tuning.totalTimeoutTicks()) {
            return fail("не успел подобрать предметы за " + tuning.totalTimeoutTicks() + " тиков");
        }
        queriedThisTick = false;
        creditMagnetPulls();
        if (entityQueries == 0 || ticksSinceQuery >= tuning.refreshTicks()) {
            refresh();
        }
        ticksSinceQuery++;
        if (target != null && (target.isRemoved() || skipped.contains(target.getStringUUID()))) {
            approach.cancel();
            pursuit.releaseHeld();
            target = null;
        }
        if (target == null) {
            target = pursuit.chooseTarget();
            if (target == null && !queriedThisTick) {
                refresh();
                target = pursuit.chooseTarget();
            }
            if (target == null) {
                return conclude();
            }
            delayWaited = 0;
            approachAttempts = 0;
            currentStand = null;
            failedStands.clear();
        }
        return pursuit.pursue();
    }

    public void suspend() {
        approach.suspend();
        if (relief != null) {
            relief.suspend();
        }
    }

    public void cancel() {
        pursuit.releaseHeld();
        approach.cancel();
        if (relief != null) {
            relief.cancel();
        }
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

    public OverflowDecider decider() {
        return decider;
    }

    private void creditMagnetPulls() {
        collected += spirit.claimMagnetPulls(area, items, startTick);
    }

    private void refresh() {
        nearby = spirit.level()
                .getEntitiesOfClass(
                        ItemEntity.class,
                        area,
                        drop -> !drop.isRemoved()
                                && addressedToAnyone(drop)
                                && !isOwnRecentDrop(drop)
                                && items.matches(itemId(drop.getItem())));
        entityQueries++;
        ticksSinceQuery = 0;
        queriedThisTick = true;
    }

    private State conclude() {
        pursuit.releaseHeld();
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
            return fail("не дотянулся до " + unreachable + " предметов" + unreachableDetail());
        }
        return State.DONE;
    }

    private String unreachableDetail() {
        for (ItemEntity drop : nearby) {
            if (!drop.isRemoved() && skipped.contains(drop.getStringUUID())) {
                return " (" + itemId(drop.getItem()) + " в "
                        + drop.blockPosition().toShortString() + " дольше " + elapsed + " тиков)";
            }
        }
        return "";
    }

    private boolean addressedToAnyone(ItemEntity drop) {
        return drop.getTarget() == null || drop.getTarget().equals(spirit.getUUID());
    }

    private DropRef toRef(ItemEntity drop) {
        ItemStack stack = drop.getItem();
        return new DropRef(
                drop.getStringUUID(),
                itemId(stack),
                stack.getCount(),
                drop.getX(),
                drop.getY(),
                drop.getZ(),
                pickup.pickable(stack));
    }

    public static ItemEntity drop(SpiritEntity spirit, ItemStack stack) {
        ItemEntity dropped = new ItemEntity(spirit.level(), spirit.getX(), spirit.getY(), spirit.getZ(), stack.copy());
        dropped.setThrower(spirit);
        dropped.setPickUpDelay(PickupTuning.SELF_DROP_DELAY_TICKS);
        dropped.setDeltaMovement(Vec3.ZERO);
        spirit.level().addFreshEntity(dropped);
        return dropped;
    }

    private boolean isOwnRecentDrop(ItemEntity drop) {
        return drop.getOwner() != null
                && drop.getOwner().getUUID().equals(spirit.getUUID())
                && drop.getAge() < PickupTuning.OWN_DROP_SKIP_TICKS;
    }

    private double distanceToSpirit(DropRef ref) {
        double dx = ref.x() - spirit.getX();
        double dy = ref.y() - spirit.getY();
        double dz = ref.z() - spirit.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    State fail(String reason) {
        approach.cancel();
        failureReason = reason;
        return State.FAILED;
    }

    static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
