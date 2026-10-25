package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageWork;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

final class GroveStock {
    private static final String MINECRAFT = "minecraft:";

    private GroveStock() {}

    static Optional<TicketTarget> trimmed(ServerLevel level, TicketTarget target, StockView stock) {
        StageWork work = target.work();
        if (work == null) {
            return Optional.empty();
        }
        List<PlannedBlock> kept = new ArrayList<>();
        int spare = 0;
        for (PlannedBlock block : work.blocks()) {
            String sapling = block.block().substring(MINECRAFT.length());
            spare = stock.available().getOrDefault(sapling, 0);
            String found = BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(new BlockPos(
                                    block.at().x(), block.at().y(), block.at().z()))
                            .getBlock())
                    .getPath();
            if (found.equals(sapling)) {
                kept.add(block);
            }
        }
        List<PlannedBlock> open =
                work.blocks().stream().filter(block -> !kept.contains(block)).toList();
        int planted = Math.min(spare, open.size());
        if (planted == 0) {
            return Optional.empty();
        }
        return Optional.of(target.withWork(new StageWork(work.site(), open.subList(0, planted))));
    }
}
