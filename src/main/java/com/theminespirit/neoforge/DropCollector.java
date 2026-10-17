package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.OverflowPolicy;
import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.DropRef;
import com.theminespirit.core.pickup.DropRoute;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupSpot;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.neoforge.inventory.OverflowDecider;
import com.theminespirit.neoforge.inventory.UnloadScript;
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
import net.minecraft.world.phys.Vec3;

public final class DropCollector {
    public enum State {
        RUNNING,
        DONE,
        FAILED
    }

    private static final double STAND_ARRIVAL = 0.2;

    private final SpiritEntity spirit;
    private final IdFilter items;
    private final AABB area;
    private final PickupTuning tuning;
    private final OverflowMode overflow;
    private final OverflowDecider decider;
    private final Approach approach;
    private final LevelSafetyView safety;
    private final Set<String> skipped = new HashSet<>();
    private List<ItemEntity> nearby = List.of();
    private ItemEntity target;
    private UnloadScript relief;
    private boolean reliefTried;
    private String failureReason;
    private int elapsed;
    private int ticksSinceQuery;
    private int entityQueries;
    private int collected;
    private int delayWaited;
    private boolean queriedThisTick;

    public DropCollector(SpiritEntity spirit, IdFilter items, BlockCoord center, int radius, PickupTuning tuning,
            OverflowMode overflow) {
        this.spirit = spirit;
        this.items = items;
        this.tuning = tuning;
        this.overflow = overflow;
        this.decider = new OverflowDecider(spirit);
        this.approach = new Approach(spirit);
        this.safety = new LevelSafetyView(spirit.level());
        this.area = new AABB(center.x() - radius, center.y() - radius, center.z() - radius,
                center.x() + radius + 1.0, center.y() + radius + 1.0, center.z() + radius + 1.0);
        spirit.trackDropCollector(this);
    }

    public State tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез во время подбора");
        }
        if (relief != null) {
            return reliefTick();
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

    public void suspend() {
        approach.suspend();
        if (relief != null) {
            relief.suspend();
        }
    }

    public void cancel() {
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

    private void refresh() {
        nearby = spirit.level().getEntitiesOfClass(ItemEntity.class, area,
                drop -> !drop.isRemoved() && addressedToAnyone(drop) && !isOwnRecentDrop(drop)
                        && items.matches(itemId(drop.getItem())));
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
        if (withinPickupReach()) {
            approach.cancel();
            return pickUp();
        }
        if (!approach.isActive()) {
            beginApproach();
        }
        Approach.State state = approach.tick();
        if (withinPickupReach()) {
            approach.cancel();
            return pickUp();
        }
        if (state == Approach.State.MOVING && spirit.getNavigation().isDone() && canStepOnItem()) {
            spirit.getMoveControl().setWantedPosition(target.getX(), spirit.getY(), target.getZ(), 1.0);
        }
        return switch (state) {
            case MOVING -> State.RUNNING;
            case ARRIVED, STUCK, TIMEOUT -> giveUpOnTarget();
        };
    }

    private void beginApproach() {
        BlockCoord access = PickupSpot.accessCell(blockOf(target.blockPosition()), safety);
        Optional<BlockCoord> stand = PickupSpot.forDrop(access, blockOf(spirit.blockPosition()), safety);
        if (stand.isPresent() && !stand.get().equals(access)) {
            BlockCoord spot = stand.get();
            approach.begin(new Vec3(spot.x() + 0.5, spot.y(), spot.z() + 0.5), STAND_ARRIVAL);
            return;
        }
        approach.begin(target, tuning.pickupReach());
    }

    private boolean withinPickupReach() {
        double reach = tuning.pickupReach();
        if (spirit.getBoundingBox().distanceToSqr(target.position()) <= reach * reach) {
            return true;
        }
        BlockCoord access = PickupSpot.accessCell(blockOf(target.blockPosition()), safety);
        if (safety.cellAt(access) != Cell.HAZARD) {
            return false;
        }
        AABB cellBox = new AABB(access.x(), access.y(), access.z(),
                access.x() + 1.0, access.y() + 1.0, access.z() + 1.0);
        return spirit.getBoundingBox().inflate(reach).intersects(cellBox);
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
        if (taken == 0 && overflow == OverflowMode.BY_VALUE) {
            return overflowPickUp(stack);
        }
        if (taken == 0 && overflow == OverflowMode.RETURN_TO_BASE) {
            return startRelief(stack);
        }
        if (taken == 0) {
            return fail("инвентарь полон: не помещается " + itemId(stack));
        }
        reliefTried = false;
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
                pickable(stack));
    }

    private boolean pickable(ItemStack stack) {
        if (spirit.getInventory().canAddItem(stack)) {
            return true;
        }
        if (overflow == OverflowMode.RETURN_TO_BASE) {
            return true;
        }
        return overflow == OverflowMode.BY_VALUE
                && decider.decide(stack).action() != OverflowPolicy.Action.REFUSE;
    }

    private State startRelief(ItemStack stack) {
        if (reliefTried) {
            return fail("инвентарь полон: не помещается " + itemId(stack));
        }
        reliefTried = true;
        relief = UnloadScript.relief(spirit, -1);
        return State.RUNNING;
    }

    private State reliefTick() {
        ScriptStatus status = relief.tick();
        if (status == ScriptStatus.RUNNING) {
            return State.RUNNING;
        }
        int moved = relief.movedTotal();
        String reason = relief.failureReason();
        relief = null;
        if (moved > 0) {
            reliefTried = false;
            return State.RUNNING;
        }
        reliefTried = false;
        return fail("инвентарь полон: на базе нечего сдать"
                + (reason == null || reason.isBlank() ? "" : " (" + reason + ")"));
    }

    private State overflowPickUp(ItemStack stack) {
        OverflowPolicy.Decision decision = decider.decide(stack);
        return switch (decision.action()) {
            case TAKE_INTO_FREE_SLOT -> fail("инвентарь полон: не помещается " + itemId(stack));
            case DROP -> dropAndTake(stack, decision.slot());
            case REFUSE -> fail(decision.reason());
        };
    }

    private State dropAndTake(ItemStack stack, int slot) {
        ItemStack exile = spirit.getInventory().removeItemNoUpdate(slot);
        drop(spirit, exile);
        reliefTried = false;
        spirit.getInventory().setItem(slot, stack.copy());
        spirit.take(target, stack.getCount());
        collected += stack.getCount();
        target.discard();
        target = null;
        return State.RUNNING;
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
        return drop.getOwner() != null && drop.getOwner().getUUID().equals(spirit.getUUID())
                && drop.getAge() < PickupTuning.OWN_DROP_SKIP_TICKS;
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

    private static BlockCoord blockOf(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
