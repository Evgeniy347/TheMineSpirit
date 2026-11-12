package com.theminespirit.neoforge;

import com.theminespirit.core.board.lease.LeaseStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.adapter.Visibility;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.lease.LeaseRun;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class PillarDismantler {
    enum Outcome {
        RUNNING,
        DONE,
        FAILED
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final List<BlockCoord> pending;
    private final @Nullable LeaseRun lease;
    private final boolean keepLease;
    static final int BREAK_INTERVAL_TICKS = 20;
    private int sinceBreak = BREAK_INTERVAL_TICKS;
    private int cursor;
    private int returned;
    private int dropped;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;

    PillarDismantler(SpiritEntity spirit, List<BlockCoord> topDown) {
        this(spirit, topDown, null);
    }

    PillarDismantler(SpiritEntity spirit, List<BlockCoord> topDown, @Nullable LeaseRun lease) {
        this(spirit, topDown, lease, false);
    }

    PillarDismantler(SpiritEntity spirit, List<BlockCoord> topDown, @Nullable LeaseRun lease, boolean keepLease) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.pending = new ArrayList<>(topDown);
        this.lease = lease;
        this.keepLease = keepLease;
    }

    Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return failed("дух исчез во время разбора столба");
        }
        sinceBreak++;
        while (cursor < pending.size()) {
            BlockCoord cell = pending.get(cursor++);
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!isOwnMarked(pos)) {
                continue;
            }
            if (PlanCells.keep(level, pos)) {
                return failed("блок столба " + cell + " принадлежит плану постройки этапа: свои столбы "
                        + "разбирают вне построек, иначе хижина и двор теряют блоки");
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                unmark(pos);
                forget(pos);
                continue;
            }
            if (underFeet(pos) && !ready(sinceBreak, spirit.onGround())) {
                cursor--;
                return Outcome.RUNNING;
            }
            if (BreakSight.visible(spirit, pos, false) != Visibility.CLEAR) {
                return failed("блок столба " + cell + " не виден из глаз духа: разбор остановлен");
            }
            Item item = state.getBlock().asItem();
            if (level instanceof ServerLevel server) {
                BreakNotice.broken(server, pos, state, spirit, "PILLAR_DOWN", PlotProtection.Ground.TEMPORARY_MOVEMENT);
            }
            SpiritBlocks.destroy((ServerLevel) spirit.level(), pos, false, spirit, "PILLAR_DOWN");
            SpiritGesture.workOn(spirit, pos);
            unmark(pos);
            forget(pos);
            if (!Items.AIR.equals(item)) {
                ItemStack leftover = spirit.getInventory().addItem(new ItemStack(item));
                if (!leftover.isEmpty()) {
                    Block.popResource(level, pos, leftover);
                    dropped++;
                }
            }
            returned++;
            sinceBreak = 0;
            return Outcome.RUNNING;
        }
        outcome = Outcome.DONE;
        if (lease != null && !keepLease) {
            if (lease.cells().isEmpty()) {
                if (lease.status() == LeaseStatus.ACTIVE) {
                    lease.commit();
                }
            } else if (lease.status() == LeaseStatus.ACTIVE) {
                lease.orphan();
            }
        }
        return outcome;
    }

    private boolean underFeet(BlockPos pos) {
        BlockPos feet = spirit.blockPosition();
        return pos.getX() == feet.getX() && pos.getZ() == feet.getZ() && pos.getY() < feet.getY();
    }

    static boolean ready(int ticksSinceBreak, boolean onGround) {
        return onGround && ticksSinceBreak >= BREAK_INTERVAL_TICKS;
    }

    int returned() {
        return returned;
    }

    int dropped() {
        return dropped;
    }

    String failureReason() {
        return failureReason;
    }

    private Outcome failed(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        if (lease != null && !keepLease && lease.status() == LeaseStatus.ACTIVE) {
            lease.orphan();
        }
        return outcome;
    }

    private void forget(BlockPos pos) {
        if (lease != null && lease.contains(pos)) {
            if (lease.status() == LeaseStatus.ORPHANED) {
                lease.rolledBack(pos);
            } else {
                lease.forget(pos);
            }
        }
    }

    private boolean isOwnMarked(BlockPos pos) {
        return level instanceof ServerLevel server
                && (PlacedBlocksData.get(server).isOwn(pos) || (lease != null && lease.contains(pos)));
    }

    private void unmark(BlockPos pos) {
        if (level instanceof ServerLevel server) {
            PlacedBlocksData.get(server).unmarkSpirit(pos);
        }
    }
}
