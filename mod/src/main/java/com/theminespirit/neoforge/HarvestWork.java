package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.inventory.InventorySpace;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.BreakSkipTally;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class HarvestWork {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritEntity spirit;
    private final Level level;
    private final HarvestSpec spec;
    private final LevelSafetyView safety;
    private final BlockBreaker breaker;
    private final HarvestRearm rearm;
    private final HarvestSearch search;
    private final HarvestLoot loot;
    private final HarvestCounters counters;
    private final Consumer<String> fail;
    private final Function<BlockCoord, BlockBreaker.Begin> beginBreak;

    private WorkTarget reserved;

    HarvestWork(
            SpiritEntity spirit,
            HarvestSpec spec,
            LevelSafetyView safety,
            BlockBreaker breaker,
            HarvestRearm rearm,
            HarvestSearch search,
            HarvestLoot loot,
            HarvestCounters counters,
            Consumer<String> fail,
            Function<BlockCoord, BlockBreaker.Begin> beginBreak) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.spec = spec;
        this.safety = safety;
        this.breaker = breaker;
        this.rearm = rearm;
        this.search = search;
        this.loot = loot;
        this.counters = counters;
        this.fail = fail;
        this.beginBreak = beginBreak;
    }

    HarvestSession.State acceptTarget(BlockCoord target) {
        BlockState targetState = level.getBlockState(toPos(target));
        search.removeBuffered(target);
        if (!breaker.dropsAllowedFor(targetState)) {
            String brokenTool = breaker.brokenToolSuffix();
            if (!brokenTool.isEmpty()) {
                if (!rearm.tried()) {
                    search.offerTarget(target);
                    rearm.start(brokenTool);
                    return HarvestSession.State.REARM;
                }
                fail.accept("инструмент сломался: добыто " + counters.mined() + " из " + spec.count()
                        + (rearm.failure() == null ? "" : "; замена не добыта: " + rearm.failure()));
                return HarvestSession.State.PICK;
            }
            if (counters.mined() == 0 && !spec.skipWithoutTool()) {
                fail.accept(HarvestReasonText.tierReason(targetState, spec.label()));
                return HarvestSession.State.PICK;
            }
            counters.addNoTool();
            return HarvestSession.State.PICK;
        }
        search.target(target);
        return null;
    }

    HarvestSession.State prepareBreak() {
        BlockCoord target = search.target();
        BlockCoord stand = search.stand();
        if (!(MiningSafety.canBreak(target, safety) instanceof BreakSafety.Safe)) {
            counters.addUnsafe();
            search.clearTargets();
            return HarvestSession.State.PICK;
        }
        BlockBreaker.Begin begin = beginBreak.apply(target);
        if (begin instanceof BlockBreaker.Protected) {
            counters.addProtected();
            search.clearTargets();
            return HarvestSession.State.PICK;
        }
        if (begin instanceof BlockBreaker.Refused refused) {
            if (refused.reason() == BlockBreaker.RefuseReason.NEED_TOOL) {
                fail.accept(refused.message());
                return HarvestSession.State.PICK;
            }
            BreakSkipTally.Reason reason = BreakSkips.reason(refused.reason());
            counters.recordSkip(reason, target, stand)
                    .ifPresent(sample -> SkipLog.skipped(LOGGER, spirit, "harvest", sample));
            if (BreakSight.retryable(refused.reason()) && retryOtherStand()) {
                return HarvestSession.State.PICK;
            }
            search.clearTargets();
            return HarvestSession.State.PICK;
        }
        if (breaker.dropsAllowed()) {
            BlockPos pos = toPos(target);
            BlockState stateToBreak = level.getBlockState(pos);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            ItemStack tool = breaker.tool();
            List<ItemStack> drops = Block.getDrops(stateToBreak, (ServerLevel) level, pos, blockEntity, spirit, tool);
            StackMapper mapper = new StackMapper();
            List<Stack> inventory = mapper.map(spirit.getInventory());
            List<Stack> incoming = new ArrayList<>();
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    incoming.add(mapper.of(drop));
                }
            }
            boolean fits = incoming.isEmpty() || InventorySpace.holds(inventory, incoming);
            boolean nearlyFull = HarvestDeposit.occupiedSlots(spirit) >= CatalogLimits.UNLOAD_TRIGGER_SLOTS;
            if (!fits || (nearlyFull && !loot.depositTried())) {
                if (!loot.depositTried()) {
                    return loot.startDeposit(incoming, !fits);
                }
                fail.accept("инвентарь полон: не помещается добыча");
                return HarvestSession.State.PICK;
            }
        }
        WorkTarget workTarget = new WorkTarget(spec.kind(), target);
        if (!WorkReserveHub.reserve(spirit, workTarget)) {
            breaker.cancel();
            search.clearTargets();
            return HarvestSession.State.PICK;
        }
        reserved = workTarget;
        return HarvestSession.State.BREAK;
    }

    HarvestSession.State breakTick() {
        return switch (breaker.tick()) {
            case BREAKING -> HarvestSession.State.BREAK;
            case BROKEN -> {
                releaseReserved();
                counters.addMined();
                counters.progress();
                rearm.broken(breaker);
                loot.begin(search.target());
                yield HarvestSession.State.COLLECT;
            }
            case GONE -> {
                releaseReserved();
                search.clearTargets();
                yield HarvestSession.State.PICK;
            }
        };
    }

    private boolean retryOtherStand() {
        BlockCoord target = search.target();
        if (target == null) {
            return false;
        }
        search.noteUnreachable(target);
        BlockCoord stand = search.stand();
        if (stand != null) {
            search.excludeStand(stand, target);
        }
        search.offerTarget(target);
        search.clearTargets();
        return true;
    }

    void releaseReserved() {
        if (reserved != null) {
            WorkReserveHub.release(spirit, reserved);
            reserved = null;
        }
    }

    void suspendBreak() {
        breaker.cancel();
        search.offerTarget(search.target());
        search.clearTargets();
    }

    void cancelBreaker() {
        breaker.cancel();
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
