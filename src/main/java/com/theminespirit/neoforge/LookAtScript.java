package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.CellReport;
import com.theminespirit.core.perception.LoadedCell;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

public final class LookAtScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final BlockCoord target;
    private int blockReads;
    private String failureReason;

    public LookAtScript(SpiritEntity spirit, BlockCoord target) {
        this.spirit = spirit;
        this.target = target;
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            failureReason = "дух исчез, не успев посмотреть";
            return ScriptStatus.FAILED;
        }
        spirit.updateFinding(CellReport.describe(target, readCell()));
        return ScriptStatus.SUCCEEDED;
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    public int blockReads() {
        return blockReads;
    }

    private Optional<LoadedCell> readCell() {
        BlockPos pos = new BlockPos(target.x(), target.y(), target.z());
        LevelChunk chunk = spirit.level().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return Optional.empty();
        }
        blockReads++;
        BlockState state = chunk.getBlockState(pos);
        BlockEntity blockEntity = chunk.getBlockEntity(pos);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return Optional.of(new LoadedCell(blockId, blockEntity instanceof Container, entityTypesAt(pos)));
    }

    private List<String> entityTypesAt(BlockPos pos) {
        return spirit.level().getEntities((Entity) null, new AABB(pos), e -> !e.isRemoved() && e != spirit
                        && e.blockPosition().equals(pos)).stream()
                .map(entity -> EntityType.getKey(entity.getType()).toString())
                .toList();
    }
}
