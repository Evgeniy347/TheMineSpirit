package com.theminespirit.neoforge;

import com.theminespirit.core.movement.WaterCrossingPlanner;
import com.theminespirit.neoforge.lease.LeaseRun;
import com.theminespirit.neoforge.movement.CheapBlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class DigOutFloor {
    private final SpiritEntity spirit;
    private final Level level;
    private final BlockPlacer placer;
    private final LeaseRun lease;

    DigOutFloor(SpiritEntity spirit, Level level, BlockPlacer placer, LeaseRun lease) {
        this.spirit = spirit;
        this.level = level;
        this.placer = placer;
        this.lease = lease;
    }

    Optional<String> place(BlockPos cell) {
        Optional<String> blockId = WaterCrossingPlanner.cheapestAvailable(CheapBlocks.counts(spirit));
        if (blockId.isEmpty()) {
            return Optional.of("в инвентаре нет дешёвых блоков для ступени");
        }
        String id = blockId.get();
        BlockState state = BlockStates.stateFor(id);
        if (!PlaceSupport.hasSupport(level, cell, state)) {
            return Optional.of("под клеткой " + cell.toShortString() + " нет опоры: ступень не подложить");
        }
        return switch (placer.begin(cell, state, id)) {
            case BlockPlacer.Started ignored -> {
                lease.placed(cell, id);
                yield Optional.empty();
            }
            case BlockPlacer.Refused refused -> Optional.of(refused.message());
            case BlockPlacer.Protected refused -> Optional.of(refused.reason());
        };
    }
}
