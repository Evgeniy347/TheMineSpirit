package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.base.Camp;
import com.theminespirit.core.base.SpiritHome;
import com.theminespirit.core.chunk.ChunkCoord;
import com.theminespirit.core.engine.RunProgress;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.SnapshotRestorer;
import com.theminespirit.core.engine.TaskSnapshot;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.perception.Finding;
import com.theminespirit.core.perception.MemoryCodec;
import com.theminespirit.core.perception.MemoryFormatException;
import com.theminespirit.core.perception.MemoryStore;
import com.theminespirit.core.perception.MemoryType;
import com.theminespirit.core.perception.PerceptionResult;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.policy.DropPolicy;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.policy.WorkZone;
import com.theminespirit.core.spirit.SpiritNames;
import com.theminespirit.core.tactics.Layer;
import com.theminespirit.core.tactics.adapter.StoredTactics;
import com.theminespirit.core.tactics.adapter.TacticsFormatException;
import com.theminespirit.core.tactics.adapter.TacticsStateCodec;
import com.theminespirit.core.task.SpiritReading;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import com.theminespirit.neoforge.hunger.HungerState;
import com.theminespirit.neoforge.inventory.ArmorEquipPolicy;
import com.theminespirit.neoforge.inventory.SpiritInventory;
import com.theminespirit.neoforge.policy.NightLeashGate;
import com.theminespirit.neoforge.policy.ProtectionGate;
import com.theminespirit.neoforge.policy.SpiritNavigation;
import com.theminespirit.neoforge.policy.SpiritPolicies;
import com.theminespirit.neoforge.policy.SpiritWorkZoneStore;
import com.theminespirit.neoforge.tactics.SpiritGear;
import com.theminespirit.neoforge.tactics.SpiritTacticsStore;
import com.theminespirit.neoforge.tactics.TacticsService;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class SpiritEntity extends PathfinderMob {
    public static final String DEFAULT_NAME = "spirit";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityDataAccessor<ItemStack> DATA_SHOWN_ITEM =
            SynchedEntityData.defineId(SpiritEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final int INVENTORY_SIZE = 36;

    private final SpiritInventory inventory = new SpiritInventory(INVENTORY_SIZE);
    private final SpiritChunkLoader chunkLoader = new SpiritChunkLoader(this);
    private final SpiritPolicies policies = new SpiritPolicies(this);
    private final ProtectionGate protectionGate = new ProtectionGate(this);
    private final SpiritGear gear = new SpiritGear(this);
    private final ArmorEquipPolicy armorEquip = new ArmorEquipPolicy(this);
    private final HungerState hunger = new HungerState(this);
    private final TacticsService tactics = new TacticsService(this);
    private final DropMagnet magnet = new DropMagnet(this);
    private final SpiritReflexes reflexes = new SpiritReflexes(this);
    private SpiritBrain spiritBrain;
    private boolean mainHandBorrowed;
    private long backgroundGeneration = SpiritReading.UNKNOWN_GENERATION;
    private WorldObservation perception;
    private PerceptionResult perceptionResult;
    private Finding finding;
    private MemoryStore memory = MemoryStore.empty();
    private SpiritHome home = SpiritHome.none();
    private BlockCoord bed;
    private Camp camp;
    private int pathBuilds;
    private @Nullable BuildStats buildStats;
    private int entityQueries;
    private int snapshotBuilds;
    private long lastSayTick = Long.MIN_VALUE;
    private String pendingTaskJson;
    private TaskSnapshot pendingSnapshot;
    private String pendingRestoreFailure;
    private boolean child;
    private boolean demo;
    private boolean nightLeashArmed;
    private boolean defenceAssigned;
    private int toolBreaks;
    private ItemStack lastBrokenTool = ItemStack.EMPTY;

    public SpiritEntity(EntityType<? extends SpiritEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        recordSettlementAnchor();
        if (hasCustomName() || !(level() instanceof ServerLevel server)) {
            return;
        }
        Set<String> taken = new HashSet<>();
        for (Entity entity : server.getAllEntities()) {
            if (entity instanceof SpiritEntity other && other.hasCustomName()) {
                taken.add(other.getCustomName().getString());
            }
        }
        setCustomName(Component.literal(SpiritNames.next(taken)));
    }

    @Override
    public void aiStep() {
        // mc: взмах руки анимируется только у мобов, которые сами двигают swingTime (как Monster.aiStep): без этого
        // клиент получает пакет взмаха, но рука духа не опускается.
        updateSwingTime();
        super.aiStep();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        applyPendingRestore();
        WallEscape.tick(this);
        chunkLoader.tick();
        armorEquip.tick();
        hunger.tick();
        NightLeashGate.arm(this);
        magnet.tick();
        policies.tick();
        reflexes.tick();
        if (demo) {
            tactics.pause();
            if (spiritBrain != null) {
                spiritBrain.tick();
            }
            return;
        }
        if (policies.isOverriding()) {
            tactics.pause();
            return;
        }
        tactics.tick();
        if (spiritBrain != null && (!strategyOwned() || spiritBrain.observesWhileTacticsOwns())) {
            spiritBrain.tick();
        }
    }

    private boolean strategyOwned() {
        return tactics.wantsControl() || policies.owner() == Layer.TACTICS;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new SpiritNavigation(this, level);
    }

    @Override
    public int getMaxFallDistance() {
        return DropPolicy.MAX_DROP;
    }

    public void showInHand(ItemStack stack) {
        ItemStack shown = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        if (!ItemStack.isSameItemSameComponents(entityData.get(DATA_SHOWN_ITEM), shown)) {
            entityData.set(DATA_SHOWN_ITEM, shown);
        }
    }

    public ItemStack shownInHand() {
        return entityData.get(DATA_SHOWN_ITEM);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SHOWN_ITEM, ItemStack.EMPTY);
    }

    // mc: ItemInHandLayer рисует getMainHandItem → getItemBySlot(MAINHAND); на клиенте подменяется показанным.
    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND && level().isClientSide()) {
            ItemStack shown = shownInHand();
            if (!shown.isEmpty()) {
                return shown;
            }
        }
        return super.getItemBySlot(slot);
    }

    public void holdInMainHand(ItemStack stack) {
        setItemSlot(EquipmentSlot.MAINHAND, stack);
        mainHandBorrowed = false;
    }

    public void showCopyInMainHand(ItemStack stack) {
        setItemSlot(EquipmentSlot.MAINHAND, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        mainHandBorrowed = !stack.isEmpty();
    }

    public boolean mainHandOwned() {
        return !mainHandBorrowed;
    }

    public boolean releaseMainHand() {
        ItemStack held = super.getItemBySlot(EquipmentSlot.MAINHAND);
        if (held.isEmpty()) {
            mainHandBorrowed = false;
            return true;
        }
        if (!mainHandBorrowed) {
            if (!inventory.canAddAll(List.of(held))) {
                return false;
            }
            inventory.addItem(held.copy());
        }
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        mainHandBorrowed = false;
        return true;
    }

    // mc: Mob.checkDespawn убирает существо категории CREATURE дальше 128 блоков от ближнего игрока (и случайно —
    // дальше
    // 32 после 30 с без действия), если removeWhenFarAway не false; Animal переопределяет так же. Дух — не дикий моб:
    // игра 21.10 — духи дальних построек /spirit demo city 5 пропадали, пока владелец стоял на другом конце.
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return policies.exemptions().has(PolicyId.PORTAL) && super.canUsePortal(allowPassengers);
    }

    public TacticsService tactics() {
        return tactics;
    }

    public String displayName() {
        return hasCustomName() ? getCustomName().getString() : DEFAULT_NAME;
    }

    public SpiritGear gear() {
        return gear;
    }

    public ArmorEquipPolicy armorEquip() {
        return armorEquip;
    }

    public HungerState hunger() {
        return hunger;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt) {
            tactics.onHurt(source);
            hunger.onHurt();
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            hunger.onAttack();
        }
        return hit;
    }

    @Override
    public void onEquippedItemBroken(Item item, EquipmentSlot slot) {
        super.onEquippedItemBroken(item, slot);
        toolBreaks++;
        lastBrokenTool = new ItemStack(item);
    }

    public int toolBreaks() {
        return toolBreaks;
    }

    public ItemStack lastBrokenTool() {
        return lastBrokenTool;
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

    public Optional<BlockCoord> home() {
        return home.point();
    }

    public void setHome(BlockCoord home) {
        this.home = SpiritHome.at(home);
        recordSettlementAnchor();
    }

    public void clearHome() {
        this.home = SpiritHome.none();
        recordSettlementAnchor();
    }

    public Optional<BlockCoord> bed() {
        return Optional.ofNullable(bed);
    }

    public void setBed(BlockCoord bed) {
        this.bed = bed;
    }

    public boolean nightLeashArmed() {
        return nightLeashArmed;
    }

    public void setNightLeashArmed(boolean armed) {
        this.nightLeashArmed = armed;
    }

    public boolean isDefenceAssigned() {
        return defenceAssigned;
    }

    public Optional<Camp> camp() {
        return Optional.ofNullable(camp);
    }

    public void setCamp(Camp camp) {
        this.camp = camp;
        recordSettlementAnchor();
    }

    public void clearCamp() {
        this.camp = null;
        recordSettlementAnchor();
    }

    private void recordSettlementAnchor() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        Optional<BlockCoord> campPoint = camp().map(camp -> new BlockCoord(
                (int) Math.floor(camp.center().x()),
                (int) Math.floor(camp.center().y()),
                (int) Math.floor(camp.center().z())));
        SettlementAnchorStore.get(server).record(getUUID(), home(), campPoint);
    }

    public Optional<SpiritBrain> brainIfPresent() {
        return Optional.ofNullable(spiritBrain);
    }

    @Override
    public void onRemovedFromLevel() {
        WorkReserveHub.releaseAll(this);
        Entity.RemovalReason reason = getRemovalReason();
        if (reason != null && (reason.shouldDestroy() || reason == Entity.RemovalReason.CHANGED_DIMENSION)) {
            chunkLoader.release();
            if (level() instanceof ServerLevel server) {
                SettlementAnchorStore.get(server).remove(getUUID());
            }
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
        compound.putBoolean(
                "SpiritMainHandBorrowed",
                mainHandBorrowed && !super.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty());
        SpiritTaskStore.write(compound, taskJsonForSave(), getUUID());
        SpiritMemoryStore.write(compound, memory.all().isEmpty() ? null : MemoryCodec.encode(memory));
        SpiritWorkZoneStore.write(compound, policies.workZone().orElse(null));
        SpiritTacticsStore.write(compound, tactics.hasState() ? TacticsStateCodec.encode(tactics.snapshot()) : null);
        SpiritHomeStore.write(compound, home);
        SpiritBedStore.write(compound, bed);
        SpiritCampStore.write(compound, camp);
        SpiritChildStore.write(compound, child);
        SpiritDemoStore.write(compound, demo);
        SpiritHungerStore.write(compound, hunger.state());
    }

    private String taskJsonForSave() {
        if (spiritBrain != null) {
            String json = spiritBrain.snapshot().map(this::encodeForSave).orElse(null);
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
        mainHandBorrowed = compound.contains("SpiritMainHandBorrowed", Tag.TAG_BYTE)
                && compound.getBoolean("SpiritMainHandBorrowed");
        memory = SpiritMemoryStore.read(compound).map(this::decodeMemory).orElse(MemoryStore.empty());
        try {
            SpiritWorkZoneStore.read(compound).ifPresent(policies::setWorkZone);
        } catch (RuntimeException e) {
            LOGGER.warn("Дух {} не восстановил рабочую зону: {}", getUUID(), e.getMessage());
        }
        restoreHome(compound);
        restoreBed(compound);
        restoreCamp(compound);
        restoreTactics(compound);
        restoreHunger(compound);
        child = SpiritChildStore.read(compound);
        demo = SpiritDemoStore.read(compound);
        SpiritTaskStore.read(compound).ifPresent(json -> {
            pendingTaskJson = json;
            try {
                pendingSnapshot = TaskSnapshotCodec.decode(json);
            } catch (RuntimeException e) {
                pendingRestoreFailure = "не удалось восстановить задачу: " + e.getMessage();
            }
        });
    }

    private void restoreHome(CompoundTag compound) {
        try {
            BlockPos pos = blockPosition();
            home = SpiritHome.resolve(
                    SpiritHomeStore.read(compound),
                    memory.recallAll(MemoryType.BASE),
                    level().dimension().location().toString(),
                    new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
        } catch (RuntimeException e) {
            LOGGER.warn("Дух {} не восстановил дом: {}", getUUID(), e.getMessage());
        }
    }

    private void restoreBed(CompoundTag compound) {
        try {
            bed = SpiritBedStore.read(compound).orElse(null);
        } catch (RuntimeException e) {
            LOGGER.warn("Дух {} не восстановил свою кровать: {}", getUUID(), e.getMessage());
        }
    }

    private void restoreCamp(CompoundTag compound) {
        try {
            camp = SpiritCampStore.read(compound).orElse(null);
        } catch (RuntimeException e) {
            LOGGER.warn("Дух {} не восстановил лагерь: {}", getUUID(), e.getMessage());
        }
    }

    private void restoreHunger(CompoundTag compound) {
        try {
            SpiritHungerStore.read(compound).ifPresent(hunger::restore);
        } catch (RuntimeException e) {
            LOGGER.warn("Дух {} не восстановил сытость, начнёт сытым: {}", getUUID(), e.getMessage());
        }
    }

    private void restoreTactics(CompoundTag compound) {
        SpiritTacticsStore.read(compound).ifPresent(json -> {
            try {
                StoredTactics stored = TacticsStateCodec.decode(json);
                tactics.restore(stored);
            } catch (TacticsFormatException e) {
                tactics.failRestore(e.getMessage());
                LOGGER.error(
                        "Дух {} потерял состояние тактики, начнёт с чистого листа. Причина: {}",
                        getUUID(),
                        e.getMessage(),
                        e);
            }
        });
    }

    private MemoryStore decodeMemory(String json) {
        try {
            return MemoryCodec.decode(json);
        } catch (MemoryFormatException e) {
            LOGGER.error(
                    "Дух {} потерял память: сохранённые точки не прочитаны, память пуста. Причина: {}",
                    getUUID(),
                    e.getMessage(),
                    e);
            return MemoryStore.empty();
        }
    }

    public MemoryStore memory() {
        return memory;
    }

    public void updateMemory(MemoryStore updated) {
        this.memory = updated;
    }

    public SpiritInventory getInventory() {
        return inventory;
    }

    public void startTask(TaskNode plan) {
        brain().start(plan);
    }

    public boolean runsBackgroundTask() {
        return backgroundGeneration != SpiritReading.UNKNOWN_GENERATION
                && backgroundGeneration == taskGeneration()
                && taskStatus().orElse(null) == RunStatus.RUNNING;
    }

    public long taskGeneration() {
        return spiritBrain == null ? SpiritReading.UNKNOWN_GENERATION : spiritBrain.generation();
    }

    public void cancelTask() {
        brain().cancel();
    }

    public Optional<RunStatus> taskStatus() {
        return spiritBrain == null ? Optional.empty() : spiritBrain.status();
    }

    public Optional<TaskNode> currentPlan() {
        return spiritBrain == null ? Optional.empty() : spiritBrain.plan();
    }

    public Optional<String> currentActivity() {
        Optional<TaskNode> plan = currentPlan();
        if (plan.isEmpty()) {
            return Optional.empty();
        }
        return KitFor.activityOfPlan(plan.get());
    }

    public boolean isChild() {
        return child;
    }

    public void setChild(boolean child) {
        this.child = child;
    }

    public boolean isDemo() {
        return demo;
    }

    public void setDemo(boolean demo) {
        this.demo = demo;
    }

    public void stripEquipment() {
        showInHand(ItemStack.EMPTY);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.MAINHAND) {
                releaseMainHand();
            } else if (!getItemBySlot(slot).isEmpty()) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    public Optional<String> taskSuspendReason() {
        return spiritBrain == null ? Optional.empty() : spiritBrain.suspendReason();
    }

    public Optional<String> currentTaskScript() {
        return spiritBrain == null ? Optional.empty() : spiritBrain.currentScript();
    }

    public boolean isFollowingPlayer() {
        return currentTaskScript()
                .filter(NightLeashGate.FOLLOW_SCRIPTS::contains)
                .isPresent();
    }

    public Optional<RunProgress> taskProgress() {
        return spiritBrain == null ? Optional.empty() : spiritBrain.progress();
    }

    public Optional<RunProgress> taskFailedAt() {
        return spiritBrain == null ? Optional.empty() : spiritBrain.failedAt();
    }

    public Optional<String> taskFailureReason() {
        return spiritBrain == null ? Optional.empty() : spiritBrain.failureReason();
    }

    public Optional<ScriptResult> taskResult() {
        return spiritBrain == null ? Optional.empty() : spiritBrain.successResult();
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
        if (spiritBrain == null) {
            spiritBrain = new SpiritBrain(this);
        }
        return spiritBrain;
    }

    public void countPathBuild() {
        pathBuilds++;
    }

    public int claimMagnetPulls(AABB area, IdFilter items, long since) {
        return magnet.claim(area, items, since);
    }

    public int pathBuilds() {
        return pathBuilds;
    }

    void noteBuildStats(BuildStats stats) {
        buildStats = stats;
    }

    public Optional<BuildStats> buildStats() {
        return Optional.ofNullable(buildStats);
    }

    public void countEntityQuery() {
        entityQueries++;
    }

    public int entityQueries() {
        return entityQueries;
    }

    public long lastSayTick() {
        return lastSayTick;
    }

    public void setLastSayTick(long tick) {
        lastSayTick = tick;
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
            TaskSnapshot validated = SnapshotRestorer.withValidatedPlan(
                    snapshot, SpiritScripts.catalog(), SpiritScripts.conditionCatalog());
            brain().restore(validated);
        } catch (RuntimeException e) {
            String reason = "не удалось восстановить задачу: " + e.getMessage();
            brain().failRestore(reason);
            LOGGER.warn("Дух {} не восстановил задачу: {}", getUUID(), reason, e);
        }
    }
}
