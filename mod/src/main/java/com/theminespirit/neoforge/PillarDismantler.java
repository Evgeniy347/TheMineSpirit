package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.adapter.Visibility;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.board.SettlementPlotStore;
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

final class PillarDismantler {
    enum Outcome {
        RUNNING,
        DONE,
        FAILED
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final List<BlockCoord> pending;
    private int cursor;
    private int returned;
    private int dropped;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;

    PillarDismantler(SpiritEntity spirit, List<BlockCoord> topDown) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.pending = new ArrayList<>(topDown);
    }

    Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            failureReason = "дух исчез во время разбора столба";
            outcome = Outcome.FAILED;
            return outcome;
        }
        while (cursor < pending.size()) {
            BlockCoord cell = pending.get(cursor++);
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!isOwnMarked(pos)) {
                continue;
            }
            if (planned(pos)) {
                failureReason = "блок столба " + cell + " принадлежит плану постройки этапа: свои столбы "
                        + "разбирают вне построек, иначе хижина и двор теряют блоки";
                outcome = Outcome.FAILED;
                return outcome;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                unmark(pos);
                continue;
            }
            if (BreakSight.visible(spirit, pos, false) != Visibility.CLEAR) {
                failureReason = "блок столба " + cell + " не виден из глаз духа: разбор остановлен";
                outcome = Outcome.FAILED;
                return outcome;
            }
            Item item = state.getBlock().asItem();
            if (level instanceof ServerLevel server) {
                BreakNotice.broken(server, pos, state, spirit, "PILLAR_DOWN", PlotProtection.Ground.NONE);
            }
            SpiritBlocks.destroy((ServerLevel) spirit.level(), pos, false, spirit, "PILLAR_DOWN");
            SpiritGesture.workOn(spirit, pos);
            unmark(pos);
            if (!Items.AIR.equals(item)) {
                ItemStack leftover = spirit.getInventory().addItem(new ItemStack(item));
                if (!leftover.isEmpty()) {
                    Block.popResource(level, pos, leftover);
                    dropped++;
                }
            }
            returned++;
            return Outcome.RUNNING;
        }
        outcome = Outcome.DONE;
        return outcome;
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

    private boolean planned(BlockPos pos) {
        return level instanceof ServerLevel server
                && SettlementPlotStore.get(server)
                        .site()
                        .map(site -> site.planCell(pos.getX(), pos.getY(), pos.getZ()))
                        .orElse(false);
    }

    private boolean isOwnMarked(BlockPos pos) {
        return level instanceof ServerLevel server
                && PlacedBlocksData.get(server).isSpiritMarked(pos);
    }

    private void unmark(BlockPos pos) {
        if (level instanceof ServerLevel server) {
            PlacedBlocksData.get(server).unmarkSpirit(pos);
        }
    }
}
