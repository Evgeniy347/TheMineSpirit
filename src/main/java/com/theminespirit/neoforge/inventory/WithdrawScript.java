package com.theminespirit.neoforge.inventory;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.WithdrawSpread;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.script.ContainerTarget;
import com.theminespirit.core.script.TransferParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public final class WithdrawScript extends ContainerTask {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final TransferParams params;
    private List<WithdrawSpread.Step> steps;
    private int stepIndex;
    private String scanned = "";

    public WithdrawScript(SpiritEntity spirit, TransferParams params) {
        super(spirit, scanTarget(params), params.radius());
        this.params = params;
    }

    private static ContainerTarget scanTarget(TransferParams params) {
        return params.container() instanceof ContainerTarget.Nearest ? ContainerTarget.AUTO : params.container();
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
        int remaining = transferRemaining();
        if (remaining <= 0) {
            return null;
        }
        BlockCoord at = new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
        if (steps == null) {
            steps = WithdrawSpread.plan(scan.snapshots(), at, params.item(), params.count())
                    .steps();
            scanned = "; осмотрено контейнеров " + scan.snapshots().size() + " у " + at;
        }
        if (stepIndex >= steps.size()) {
            return null;
        }
        WithdrawSpread.Step step = steps.get(stepIndex++);
        int index = step.index();
        ContainerSnapshot chosen = scan.snapshots().get(index);
        LOGGER.info(
                "[withdraw] {}: контейнер {} — свободно {} из {} слотов, беру '{}' ×{}",
                spirit.displayName(),
                chosen.pos(),
                chosen.freeSlots(),
                chosen.slotCount(),
                params.item(),
                step.count());
        TransferRunner runner = TransferRunner.filtered(
                scan.containers().get(index),
                spirit.getInventory(),
                IdFilter.of(List.of(params.item())),
                Math.min(step.count(), remaining));
        return new AutoMove(scan.containers().get(index), chosen.pos(), index, runner);
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
}
