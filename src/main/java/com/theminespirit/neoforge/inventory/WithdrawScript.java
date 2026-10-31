package com.theminespirit.neoforge.inventory;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.script.TransferParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public final class WithdrawScript extends ContainerTask {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final TransferParams params;
    private final Set<Integer> tried = new HashSet<>();
    private String scanned = "";

    public WithdrawScript(SpiritEntity spirit, TransferParams params) {
        super(spirit, params.container(), params.radius());
        this.params = params;
    }

    @Override
    protected int requestedCount() {
        return params.count();
    }

    @Override
    protected boolean persistsProgress() {
        return true;
    }

    @Override
    protected ItemStack shownWhileTransferring() {
        ResourceLocation id = ResourceLocation.tryParse(params.item());
        return id == null
                ? ItemStack.EMPTY
                : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override
    protected TransferRunner beginTransfer(Container container) {
        if (transferRemaining() <= 0) {
            return null;
        }
        LOGGER.info(
                "[withdraw] {}: контейнер{} — свободно {} из {} слотов, беру '{}' ×{}, осталось {}",
                spirit.displayName(),
                at(),
                freeSlots(container),
                container.getContainerSize(),
                params.item(),
                params.count(),
                transferRemaining());
        return TransferRunner.filtered(
                container, spirit.getInventory(), IdFilter.of(List.of(params.item())), transferRemaining());
    }

    @Override
    protected ScriptStatus finish(int moved, TransferRunner.Shortage shortage) {
        Container current = container;
        LOGGER.info(
                "[withdraw] {}: контейнер{} — свободно {} из {} слотов, перенесено {}, решение: {}",
                spirit.displayName(),
                at(),
                current == null ? -1 : freeSlots(current),
                current == null ? -1 : current.getContainerSize(),
                moved,
                shortage == TransferRunner.Shortage.NONE ? "взято" : "в контейнере не хватает предмета");
        return switch (shortage) {
            case NONE -> ScriptStatus.SUCCEEDED;
            case SOURCE_LACK ->
                failed("в контейнере только " + moved + " из " + params.count() + " '" + params.item()
                        + "': перенесено " + moved + ", остального в контейнере нет");
            case DEST_FULL ->
                failed("инвентарь духа полон: перенесено " + moved + " из " + params.count() + " '" + params.item()
                        + "', остальное осталось в контейнере");
        };
    }

    @Override
    protected AutoMove nextAutoMove(AutoContainerScan scan) {
        int remaining = params.count() - movedTotal();
        if (remaining <= 0) {
            return null;
        }
        IdFilter filter = IdFilter.of(List.of(params.item()));
        BlockCoord at = new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
        int best = -1;
        long bestDistance = Long.MAX_VALUE;
        List<ContainerSnapshot> snapshots = scan.snapshots();
        scanned = "; осмотрено контейнеров " + snapshots.size() + " у " + at;
        for (int index = 0; index < snapshots.size(); index++) {
            if (tried.contains(index) || !holds(snapshots.get(index), filter)) {
                continue;
            }
            long distance = distance(snapshots.get(index).pos(), at);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        if (best < 0) {
            return null;
        }
        tried.add(best);
        ContainerSnapshot chosen = snapshots.get(best);
        LOGGER.info(
                "[withdraw] {}: контейнер {} — свободно {} из {} слотов, беру '{}' ×{}",
                spirit.displayName(),
                chosen.pos(),
                chosen.freeSlots(),
                chosen.slotCount(),
                params.item(),
                remaining);
        TransferRunner runner =
                TransferRunner.filtered(scan.containers().get(best), spirit.getInventory(), filter, remaining);
        return new AutoMove(scan.containers().get(best), snapshots.get(best).pos(), best, runner);
    }

    @Override
    protected ScriptStatus finishAuto() {
        int moved = movedTotal();
        if (moved >= params.count()) {
            return ScriptStatus.SUCCEEDED;
        }
        return failed("в сундуках рядом только " + moved + " из " + params.count() + " '" + params.item()
                + "': перенесено " + moved + ", остального нет ни в одном" + scanned);
    }

    private String at() {
        return containerPos == null ? "" : " " + containerPos.toShortString();
    }

    private static int freeSlots(Container container) {
        int free = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    private static boolean holds(ContainerSnapshot snapshot, IdFilter filter) {
        for (Stack stack : snapshot.slots()) {
            if (stack != null && stack.count() > 0 && filter.matches(stack.item())) {
                return true;
            }
        }
        return false;
    }

    private static long distance(BlockCoord a, BlockCoord b) {
        long dx = (long) a.x() - b.x();
        long dy = (long) a.y() - b.y();
        long dz = (long) a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
