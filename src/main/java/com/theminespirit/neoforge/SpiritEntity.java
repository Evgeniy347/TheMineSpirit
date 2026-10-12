package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.chunk.ChunkCoord;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.SnapshotRestorer;
import com.theminespirit.core.engine.TaskSnapshot;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.perception.Finding;
import com.theminespirit.core.perception.MemoryCodec;
import com.theminespirit.core.perception.MemoryFormatException;
import com.theminespirit.core.perception.MemoryStore;
import com.theminespirit.core.perception.PerceptionResult;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.policy.DropPolicy;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.policy.WorkZone;
import com.theminespirit.neoforge.policy.ProtectionGate;
import com.theminespirit.neoforge.policy.SpiritNavigation;
import com.theminespirit.neoforge.policy.SpiritPolicies;
import com.theminespirit.neoforge.policy.SpiritWorkZoneStore;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public final class SpiritEntity extends PathfinderMob {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INVENTORY_SIZE = 36;

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private final SpiritChunkLoader chunkLoader = new SpiritChunkLoader(this);
    private final SpiritPolicies policies = new SpiritPolicies(this);
    private final ProtectionGate protectionGate = new ProtectionGate(this);
    private SpiritBrain brain;
    private WorldObservation perception;
    private PerceptionResult perceptionResult;
    private Finding finding;
    private MemoryStore memory = MemoryStore.empty();
    private int pathBuilds;
    private int snapshotBuilds;
    private String pendingTaskJson;
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
        policies.tick();
        if (brain != null && !policies.isOverriding()) {
            brain.tick();
        }
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new SpiritNavigation(this, level);
    }

    @Override
    public int getMaxFallDistance() {
        return DropPolicy.MAX_DROP;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return policies.exemptions().has(PolicyId.PORTAL) && super.canUsePortal(allowPassengers);
    }

    public SpiritPolicies policies() {
        return policies;
    }

    public ProtectionGate protectionGate() {
        return protectionGate;
    }

    public Optional<WorkZone> workZone() {
        return policies.workZone();
    }

    public void setWorkZone(WorkZone zone) {
        policies.setWorkZone(zone);
    }

    public void clearWorkZone() {
        policies.clearWorkZone();
    }

    public Optional<SpiritBrain> brainIfPresent() {
        return Optional.ofNullable(brain);
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
        SpiritTaskStore.write(compound, taskJsonForSave());
        SpiritMemoryStore.write(compound, memory.all().isEmpty() ? null : MemoryCodec.encode(memory));
        SpiritWorkZoneStore.write(compound, policies.workZone().orElse(null));
    }

    private String taskJsonForSave() {
        if (brain != null) {
            String json = brain.snapshot().map(this::encodeForSave).orElse(null);
            if (json != null) {
                return json;
            }
        }
        return pendingTaskJson;
    }

    private String encodeForSave(TaskSnapshot snapshot) {
        snapshotBuilds++;
        TaskSnapshotCodec.SaveEncoded encoded = TaskSnapshotCodec.encodeForSave(snapshot);
        if (encoded.failureReason() != null) {
            LOGGER.warn("Дух {} не сохранил задачу: {}", getUUID(), encoded.failureReason());
        }
        return encoded.json();
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("SpiritInventory", Tag.TAG_LIST)) {
            inventory.fromTag(compound.getList("SpiritInventory", Tag.TAG_COMPOUND), registryAccess());
        }
        memory = SpiritMemoryStore.read(compound).map(this::decodeMemory).orElse(MemoryStore.empty());
        try {
            SpiritWorkZoneStore.read(compound).ifPresent(policies::setWorkZone);
        } catch (RuntimeException e) {
            LOGGER.warn("Дух {} не восстановил рабочую зону: {}", getUUID(), e.getMessage());
        }
        SpiritTaskStore.read(compound).ifPresent(json -> {
            pendingTaskJson = json;
            try {
                pendingSnapshot = TaskSnapshotCodec.decode(json);
            } catch (RuntimeException e) {
                pendingRestoreFailure = "не удалось восстановить задачу: " + e.getMessage();
            }
        });
    }

    private MemoryStore decodeMemory(String json) {
        try {
            return MemoryCodec.decode(json);
        } catch (MemoryFormatException e) {
            SpiritMemoryStore.countCorruptLoad();
            LOGGER.error("Дух {} потерял память: сохранённые точки не прочитаны, память пуста. Причина: {}",
                    getUUID(), e.getMessage(), e);
            return MemoryStore.empty();
        }
    }

    public MemoryStore memory() {
        return memory;
    }

    public void updateMemory(MemoryStore updated) {
        this.memory = updated;
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public float equipmentDropChance(EquipmentSlot slot) {
        return getEquipmentDropChance(slot);
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
        if (brain == null) {
            return Optional.empty();
        }
        return brain.snapshot().map(snapshot -> TaskSnapshotCodec.encode(snapshot));
    }

    public Optional<WorldObservation> observation() {
        return Optional.ofNullable(perception);
    }

    public void updateObservation(WorldObservation observation) {
        this.perception = observation;
    }

    public Optional<PerceptionResult> perceptionResult() {
        return Optional.ofNullable(perceptionResult);
    }

    public void updatePerceptionResult(PerceptionResult result) {
        this.perceptionResult = result;
    }

    public Optional<Finding> finding() {
        return Optional.ofNullable(finding);
    }

    public void updateFinding(Finding finding) {
        this.finding = finding;
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
            pendingTaskJson = null;
            restoreSnapshot(snapshot);
            return;
        }
        if (pendingRestoreFailure != null) {
            String reason = pendingRestoreFailure;
            pendingRestoreFailure = null;
            pendingTaskJson = null;
            brain().failRestore(reason);
            LOGGER.warn("Дух {} не восстановил задачу: {}", getUUID(), reason);
        }
    }

    private void restoreSnapshot(TaskSnapshot snapshot) {
        try {
            TaskSnapshot validated = SnapshotRestorer.withValidatedPlan(snapshot, SpiritScripts.catalogFor(this),
                    SpiritScripts.conditionCatalog());
            brain().restore(validated);
        } catch (RuntimeException e) {
            String reason = "не удалось восстановить задачу: " + e.getMessage();
            brain().failRestore(reason);
            LOGGER.warn("Дух {} не восстановил задачу: {}", getUUID(), reason, e);
        }
    }
}

