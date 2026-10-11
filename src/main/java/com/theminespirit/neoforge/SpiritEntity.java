package com.theminespirit.neoforge;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.TaskNode;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public final class SpiritEntity extends PathfinderMob {
    private static final int INVENTORY_SIZE = 36;

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private SpiritBrain brain;

    public SpiritEntity(EntityType<? extends SpiritEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (brain != null) {
            brain.tick();
        }
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

    private SpiritBrain brain() {
        if (brain == null) {
            brain = new SpiritBrain(this);
        }
        return brain;
    }
}