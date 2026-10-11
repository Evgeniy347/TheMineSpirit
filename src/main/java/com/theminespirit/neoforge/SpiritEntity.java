package com.theminespirit.neoforge;

import com.theminespirit.core.chunk.ChunkCoord;
import com.theminespirit.core.chunk.ChunkDiff;
import com.theminespirit.core.chunk.ChunkSquare;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.TaskNode;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public final class SpiritEntity extends PathfinderMob {
    private static final int INVENTORY_SIZE = 36;

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private SpiritBrain brain;
    private int pathBuilds;
    private ChunkCoord trackedCenter;
    private int trackedRadius = -1;
    private Set<ChunkCoord> trackedChunks;

    public SpiritEntity(EntityType<? extends SpiritEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        updateChunkTickets();
        if (brain != null) {
            brain.tick();
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        releaseChunkTickets();
        super.remove(reason);
    }

    public Set<ChunkCoord> trackedChunks() {
        return trackedChunks == null ? Set.of() : trackedChunks;
    }

    private void updateChunkTickets() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ChunkCoord center = new ChunkCoord(chunkPosition().x, chunkPosition().z);
        int radius = SpiritConfig.radiusFor(serverLevel);
        if (trackedChunks != null && center.equals(trackedCenter) && radius == trackedRadius) {
            return;
        }
        Set<ChunkCoord> desired = ChunkSquare.chunks(center, radius);
        ChunkDiff diff = trackedChunks == null
                ? ChunkDiff.initial(desired)
                : ChunkDiff.between(trackedChunks, desired);
        if (!diff.isEmpty()) {
            SpiritChunkTickets.apply(serverLevel, this, diff);
        }
        trackedCenter = center;
        trackedRadius = radius;
        trackedChunks = desired;
    }

    private void releaseChunkTickets() {
        if (trackedChunks == null || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        SpiritChunkTickets.apply(serverLevel, this, new ChunkDiff(Set.of(), trackedChunks));
        trackedChunks = null;
        trackedCenter = null;
        trackedRadius = -1;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.put("SpiritInventory", inventory.createTag(registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("SpiritInventory", Tag.TAG_LIST)) {
            inventory.fromTag(compound.getList("SpiritInventory", Tag.TAG_COMPOUND), registryAccess());
        }
    }

    public Container getInventory() {
        return inventory;
    }

    public void startTask(TaskNode plan) {
        brain().start(plan);
    }

    public void cancelTask() {
        brain().cancel();
    }

    public Optional<RunStatus> taskStatus() {
        return brain == null ? Optional.empty() : brain.status();
    }

    public Optional<String> currentTaskScript() {
        return brain == null ? Optional.empty() : brain.currentScript();
    }

    public Optional<String> taskFailureReason() {
        return brain == null ? Optional.empty() : brain.failureReason();
    }

    public void countPathBuild() {
        pathBuilds++;
    }

    public int pathBuilds() {
        return pathBuilds;
    }

    private SpiritBrain brain() {
        if (brain == null) {
            brain = new SpiritBrain(this);
        }
        return brain;
    }
}