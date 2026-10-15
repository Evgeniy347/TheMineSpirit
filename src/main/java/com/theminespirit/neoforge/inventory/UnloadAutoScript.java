package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.ContainerChoice;
import com.theminespirit.core.inventory.ContainerPicker;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Slice;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.UnloadParams;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class UnloadAutoScript implements ScriptInstance {
    private static final double ARRIVAL = 2.5;

    private enum Phase {
        FIND,
        APPROACH,
        TRANSFER
    }

    private final SpiritEntity spirit;
    private final UnloadParams params;
    private final AutoContainerScan scan;
    private final Approach approach;
    private final List<KindGroup> pending = new ArrayList<>();
    private Phase phase = Phase.FIND;
    private ScriptStatus status = ScriptStatus.RUNNING;
    private String reason;
    private String lastRefusal;
    private int movedTotal;
    private int pendingIndex;
    private int chosen = -1;
    private TransferRunner runner;
    private boolean refusedOnce;

    public UnloadAutoScript(SpiritEntity spirit, UnloadParams params) {
        this.spirit = spirit;
        this.params = params;
        this.scan = new AutoContainerScan(spirit.level(), spirit.blockPosition(), params.radius());
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (status != ScriptStatus.RUNNING) {
            return status;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время разгрузки");
        }
        switch (phase) {
            case FIND -> find();
            case APPROACH -> approach();
            case TRANSFER -> transfer();
        }
        return status;
    }

    @Override
    public void cancel() {
        approach.cancel();
        if (runner != null) {
            runner.cancel();
        }
    }

    @Override
    public String failureReason() {
        return reason;
    }

    public int maxSlicesPerTick() {
        return scan.maxSlicesPerTick();
    }

    public int maxMovesPerTick() {
        return runner == null ? 0 : runner.maxMovesPerTick();
    }

    public int movedTotal() {
        return movedTotal;
    }

    private void find() {
        if (!scan.tick()) {
            return;
        }
        if (!params.force() && occupiedSlots() < CatalogLimits.UNLOAD_TRIGGER_SLOTS) {
            succeed();
            return;
        }
        List<Slice> surplus = surplusSlices();
        if (surplus.isEmpty()) {
            succeed();
            return;
        }
        groupByKind(surplus);
        advance();
    }

    private void advance() {
        while (pendingIndex < pending.size()) {
            KindGroup group = pending.get(pendingIndex);
            ContainerChoice choice = ContainerPicker.choose(scan.snapshots(), blockCoord(), group.kind);
            if (choice instanceof ContainerChoice.Refused refused) {
                refusedOnce = true;
                lastRefusal = refused.reason();
                pendingIndex++;
                continue;
            }
            chosen = ((ContainerChoice.Chosen) choice).index();
            ContainerSnapshot target = scan.snapshots().get(chosen);
            approach.begin(new Vec3(target.pos().x() + 0.5, target.pos().y() + 0.5, target.pos().z() + 0.5),
                    ARRIVAL);
            phase = Phase.APPROACH;
            return;
        }
        if (movedTotal == 0 && refusedOnce) {
            fail(lastRefusal);
        } else {
            succeed();
        }
    }

    private void approach() {
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> {
                KindGroup group = pending.get(pendingIndex);
                runner = TransferRunner.slices(spirit.getInventory(), scan.containers().get(chosen), group.slices);
                phase = Phase.TRANSFER;
            }
            case STUCK, TIMEOUT -> fail("дух не дошёл до контейнера: путь заблокирован или слишком далеко");
        }
    }

    private void transfer() {
        switch (runner.tick()) {
            case RUNNING -> {
            }
            case DONE -> {
                movedTotal += runner.moved();
                ContainerSort.apply(scan.containers().get(chosen), InventoryTables.values());
                scan.updateSnapshot(chosen);
                runner = null;
                pendingIndex++;
                advance();
            }
            case FAILED -> fail(runner.failureReason());
            case CANCELLED -> fail("перенос прерван");
        }
    }

    private List<Slice> surplusSlices() {
        List<Stack> inventory = scan.mapper().map(spirit.getInventory());
        return InventoryTables.kit().surplus(inventory, InventoryTables.values());
    }

    private void groupByKind(List<Slice> surplus) {
        List<Stack> inventory = scan.mapper().map(spirit.getInventory());
        pending.clear();
        for (Slice slice : surplus) {
            Stack kind = inventory.get(slice.slot());
            if (kind == null) {
                continue;
            }
            groupFor(kind).slices.add(slice);
        }
        pendingIndex = 0;
        movedTotal = 0;
    }

    private KindGroup groupFor(Stack kind) {
        for (KindGroup group : pending) {
            if (group.kind.sameKindAs(kind)) {
                return group;
            }
        }
        KindGroup created = new KindGroup(kind);
        pending.add(created);
        return created;
    }

    private BlockCoord blockCoord() {
        return new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
    }

    private int occupiedSlots() {
        int occupied = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }

    private ScriptStatus succeed() {
        status = ScriptStatus.SUCCEEDED;
        cancel();
        return status;
    }

    private ScriptStatus fail(String message) {
        status = ScriptStatus.FAILED;
        reason = message;
        cancel();
        return status;
    }

    private static final class KindGroup {
        private final Stack kind;
        private final List<Slice> slices = new ArrayList<>();

        private KindGroup(Stack kind) {
            this.kind = kind;
        }
    }
}