package com.theminespirit.neoforge;

import com.theminespirit.core.board.lease.LeaseCell;
import com.theminespirit.core.board.lease.LeaseKind;
import com.theminespirit.core.movement.EscapeRestore;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

final class DigOutRollback {
    private final SpiritEntity spirit;
    private final Level level;
    private final LeaseRun lease;

    DigOutRollback(SpiritEntity spirit, Level level, LeaseRun lease) {
        this.spirit = spirit;
        this.level = level;
        this.lease = lease;
    }

    void forgetReturned(List<BlockPos> returned) {
        for (BlockPos pos : returned) {
            if (lease.contains(pos)) {
                lease.forget(pos);
            }
        }
    }

    void rollbackStep() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        BlockPos feet = spirit.blockPosition();
        BlockCoord body = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        List<LeaseCell> cells = new ArrayList<>(lease.cells());
        Collections.reverse(cells);
        for (LeaseCell cell : cells) {
            if (cell.kind() != LeaseKind.PLACED) {
                continue;
            }
            BlockPos pos =
                    new BlockPos(cell.pos().x(), cell.pos().y(), cell.pos().z());
            if (EscapeRestore.blockedByBody(cell.pos(), body)
                    || spirit.getBoundingBox().intersects(new AABB(pos))) {
                continue;
            }
            BlockState current = level.getBlockState(pos);
            if (current.isAir()) {
                lease.forget(pos);
                continue;
            }
            String recorded = cell.after();
            if (recorded != null && !recorded.equals(BlockStates.idOf(current))) {
                lease.forget(pos);
                continue;
            }
            Item item = current.getBlock().asItem();
            SpiritBlocks.destroy(server, pos, false, spirit, "DIG_OUT_STEP");
            lease.forget(pos);
            if (!Items.AIR.equals(item)) {
                ItemStack leftover = spirit.getInventory().addItem(new ItemStack(item));
                if (!leftover.isEmpty()) {
                    Block.popResource(level, pos, leftover);
                }
            }
        }
    }

    void close() {
        if (lease.cells().isEmpty()) {
            lease.commit();
        } else {
            lease.orphan();
        }
    }
}
