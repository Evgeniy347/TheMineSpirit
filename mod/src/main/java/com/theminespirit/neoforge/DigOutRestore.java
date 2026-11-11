package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.movement.EscapeRestore;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class DigOutRestore {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritEntity spirit;
    private final Level level;
    private final Map<BlockPos, BlockState> broken = new LinkedHashMap<>();
    private int restored;

    DigOutRestore(SpiritEntity spirit, Level level) {
        this.spirit = spirit;
        this.level = level;
    }

    void remember(BlockPos cell, BlockState state) {
        if (!state.isAir()) {
            broken.putIfAbsent(cell.immutable(), state);
        }
    }

    boolean pendingOver(BlockPos feet) {
        return broken.keySet().stream().anyMatch(cell -> cell.getX() == feet.getX() && cell.getZ() == feet.getZ());
    }

    boolean pending(BlockPos cell) {
        return broken.containsKey(cell);
    }

    @Nullable
    BlockState pendingState(BlockCoord cell) {
        return broken.get(new BlockPos(cell.x(), cell.y(), cell.z()));
    }

    List<BlockPos> returnSpoil() {
        if (broken.isEmpty()) {
            return List.of();
        }
        List<BlockPos> returnedCells = new ArrayList<>();
        BlockPos feet = spirit.blockPosition();
        BlockCoord body = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        Iterator<Map.Entry<BlockPos, BlockState>> iterator = broken.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, BlockState> entry = iterator.next();
            BlockPos cell = entry.getKey();
            if (EscapeRestore.blockedByBody(new BlockCoord(cell.getX(), cell.getY(), cell.getZ()), body)
                    || spirit.getBoundingBox().intersects(new AABB(cell))) {
                continue;
            }
            if (level.getBlockState(cell).equals(entry.getValue())) {
                iterator.remove();
                continue;
            }
            SpiritBlocks.restore((ServerLevel) spirit.level(), cell, entry.getValue(), spirit, "DIG_OUT_BACKFILL");
            if (level instanceof ServerLevel server) {
                PlacedBlocksData.get(server).markSpirit(cell);
            }
            iterator.remove();
            returnedCells.add(cell);
            restored++;
        }
        if (restored > 0) {
            LOGGER.info("[dig_out] {}: вернул на место {} клеток после выхода", spirit.displayName(), restored);
        }
        return returnedCells;
    }
}
