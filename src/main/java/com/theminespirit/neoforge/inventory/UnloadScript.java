package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.DepositSelection;
import com.theminespirit.core.inventory.DepositSpread;
import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.Slice;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.inventory.WarehouseSpace;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.ContainerTarget;
import com.theminespirit.core.script.UnloadParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class UnloadScript extends ContainerTask {
    private final UnloadParams params;
    private final int protectedSlot;
    private final int reservePercent;
    private final String activity;
    private boolean refusedOnce;
    private String lastRefusal;

    public UnloadScript(SpiritEntity spirit, UnloadParams params) {
        this(spirit, params, -1, 0, null);
    }

    private UnloadScript(
            SpiritEntity spirit, UnloadParams params, int protectedSlot, int reservePercent, String activity) {
        super(spirit, params.container(), params.radius());
        this.params = params;
        this.protectedSlot = protectedSlot;
        this.reservePercent = reservePercent;
        this.activity = activity;
    }

    public static UnloadScript relief(SpiritEntity spirit, int protectedSlot) {
        return reliefFor(spirit, protectedSlot, null);
    }

    public static UnloadScript reliefFor(SpiritEntity spirit, int protectedSlot, String activity) {
        return new UnloadScript(
                spirit,
                new UnloadParams(ContainerTarget.AUTO, CatalogLimits.CONTAINER_RADIUS_DEFAULT, true),
                protectedSlot,
                WarehouseSpace.MIN_FREE_PERCENT,
                activity);
    }

    @Override
    protected boolean skipAsDone() {
        return !params.force() && occupiedSlots() < CatalogLimits.UNLOAD_TRIGGER_SLOTS;
    }

    @Override
    protected TransferRunner beginTransfer(Container container) {
        BaseKit kit = kit();
        List<Slice> surplus = DepositSelection.surplus(
                new StackMapper().map(spirit.getInventory()), kit, InventoryTables.values(), protectedSlot);
        if (surplus.isEmpty()) {
            return null;
        }
        return TransferRunner.slices(spirit.getInventory(), container, surplus);
    }

    @Override
    protected ScriptStatus finish(int moved, TransferRunner.Shortage shortage) {
        return switch (shortage) {
            case NONE, SOURCE_LACK -> ScriptStatus.SUCCEEDED;
            case DEST_FULL ->
                failed("контейнер полон: разгружено " + moved + " предметов, остаток излишка остался у духа");
        };
    }

    @Override
    protected AutoMove nextAutoMove(AutoContainerScan scan) {
        for (KindGroup group : groupByKind(scan)) {
            DepositSpread.Plan plan =
                    DepositSpread.plan(scan.snapshots(), blockCoord(), group.kind, group.total(), reservePercent);
            if (plan.steps().isEmpty()) {
                refusedOnce = true;
                lastRefusal = plan.refusal();
                continue;
            }
            DepositSpread.Step step = plan.steps().get(0);
            int index = step.index();
            TransferRunner runner = TransferRunner.slices(
                    spirit.getInventory(), scan.containers().get(index), capped(group.slices, step.count()));
            return new AutoMove(
                    scan.containers().get(index), scan.snapshots().get(index).pos(), index, runner);
        }
        return null;
    }

    private static List<Slice> capped(List<Slice> slices, int limit) {
        List<Slice> capped = new ArrayList<>();
        int left = limit;
        for (Slice slice : slices) {
            if (left <= 0) {
                break;
            }
            int count = Math.min(slice.count(), left);
            capped.add(new Slice(slice.slot(), count));
            left -= count;
        }
        return capped;
    }

    @Override
    protected ScriptStatus finishAuto() {
        if (movedTotal() == 0 && refusedOnce) {
            return failed(lastRefusal == null ? "контейнеры склада заполнены: излишек остался у духа" : lastRefusal);
        }
        return ScriptStatus.SUCCEEDED;
    }

    private List<KindGroup> groupByKind(AutoContainerScan scan) {
        List<Stack> inventory = scan.mapper().map(spirit.getInventory());
        BaseKit kit = kit();
        List<Slice> surplus = DepositSelection.surplus(inventory, kit, InventoryTables.values(), protectedSlot);
        List<KindGroup> result = new ArrayList<>();
        for (Slice slice : surplus) {
            Stack kind = inventory.get(slice.slot());
            if (kind == null) {
                continue;
            }
            KindGroup group = groupFor(result, kind);
            group.slices.add(slice);
        }
        return result;
    }

    private KindGroup groupFor(List<KindGroup> result, Stack kind) {
        for (KindGroup group : result) {
            if (group.kind.sameKindAs(kind)) {
                return group;
            }
        }
        KindGroup created = new KindGroup(kind);
        result.add(created);
        return created;
    }

    private BaseKit kit() {
        return KitFor.kitFor(
                activity != null ? activity : spirit.currentActivity().orElse(null), spirit.isChild());
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

    private static final class KindGroup {
        private final Stack kind;
        private final List<Slice> slices = new ArrayList<>();

        private KindGroup(Stack kind) {
            this.kind = kind;
        }

        private int total() {
            int total = 0;
            for (Slice slice : slices) {
                total += slice.count();
            }
            return total;
        }
    }
}
