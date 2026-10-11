package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.chunk.ChunkCoord;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.SnapshotFormatException;
import com.theminespirit.core.engine.TaskSnapshot;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.TaskNode;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import com.theminespirit.core.script.ConditionCatalog;

public final class SpiritEntity extends PathfinderMob {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INVENTORY_SIZE = 36;

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private final SpiritChunkLoader chunkLoader = new SpiritChunkLoader(this);
    private SpiritBrain brain;
    private int pathBuilds;
    private int snapshotBuilds;
    private TaskSnapshot pendingSnapshot;
    private String pendingRestoreFailure;

    public SpiritEntity(EntityType<? extends SpiritEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        applyPendingRestore();
        chunkLoader.tick();
        if (brain != null) {
            brain.tick();
        }
    }

    @Override
    public void onRemovedFromLevel() {
        Entity.RemovalReason reason = getRemovalReason();
        if (reason != null && (reason.shouldDestroy() || reason == Entity.RemovalReason.CHANGED_DIMENSION)) {
            chunkLoader.release();
        }
        super.onRemovedFromLevel();
    }

    public Set<ChunkCoord> trackedChunks() {
        return chunkLoader.trackedChunks();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.put("SpiritInventory", inventory.createTag(registryAccess()));
        if (brain != null) {
            brain.snapshot().ifPresent(snapshot -> {
                String json = TaskSnapshotCodec.encode(snapshot);
                SpiritTaskStore.write(compound, json);
            });
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("SpiritInventory", Tag.TAG_LIST)) {
            inventory.fromTag(compound.getList("SpiritInventory", Tag.TAG_COMPOUND), registryAccess());
        }
        SpiritTaskStore.read(compound).ifPresent(json -> {
            try {
                pendingSnapshot = TaskSnapshotCodec.decode(json);
            } catch (RuntimeException e) {
                pendingRestoreFailure = "не удалось восстановить задачу: " + e.getMessage();
            }
        });
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

    public Optional<String> taskSnapshot() {
        snapshotBuilds++;
        if (brain == null) {
            return Optional.empty();
        }
        return brain.snapshot().map(snapshot -> TaskSnapshotCodec.encode(snapshot));
    }

    public int snapshotBuilds() {
        return snapshotBuilds;
    }

    private SpiritBrain brain() {
        if (brain == null) {
            brain = new SpiritBrain(this);
        }
        return brain;
    }

    public void countPathBuild() {
        pathBuilds++;
    }

    public int pathBuilds() {
        return pathBuilds;
    }

    private void applyPendingRestore() {
        if (pendingSnapshot != null) {
            TaskSnapshot snapshot = pendingSnapshot;
            pendingSnapshot = null;
            restoreSnapshot(snapshot);
            return;
        }
        if (pendingRestoreFailure != null) {
            String reason = pendingRestoreFailure;
            pendingRestoreFailure = null;
            brain().failRestore(reason);
            LOGGER.warn("Дух {} не восстановил задачу: {}", getUUID(), reason);
        }
    }

    private void restoreSnapshot(TaskSnapshot snapshot) {
        try {
            if (snapshot.status() == RunStatus.RUNNING) {
                TaskNode plan = snapshot.plan();
                if (plan != null) {
                    PlanValidator.validate(plan, SpiritScripts.catalogFor(this), ConditionCatalog.empty());
                }
            }
            brain().restore(snapshot);
        } catch (RuntimeException e) {
            String reason = "не удалось восстановить задачу: " + e.getMessage();
            brain().failRestore(reason);
            LOGGER.warn("Дух {} не восстановил задачу: {}", getUUID(), reason, e);
        }
    }
}

