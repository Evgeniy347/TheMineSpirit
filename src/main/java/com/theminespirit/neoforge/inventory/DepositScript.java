package com.theminespirit.neoforge.inventory;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.ContainerChoice;
import com.theminespirit.core.inventory.ContainerPicker;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.script.TransferParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.world.Container;
import org.slf4j.Logger;

public final class DepositScript extends ContainerTask {
    private static final int FALLBACK_MAX_STACK = 64;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final TransferParams params;
    private boolean planned;
    private String autoRefusal;

    public DepositScript(SpiritEntity spirit, TransferParams params) {
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
    protected TransferRunner beginTransfer(Container container) {
        if (transferRemaining() <= 0) {
            return null;
        }
        LOGGER.info(
                "[deposit] {}: контейнер{} — свободно {} из {} слотов, кладу '{}' ×{}, осталось {}",
                spirit.displayName(),
                at(),
                freeSlots(container),
                container.getContainerSize(),
                params.item(),
                params.count(),
                transferRemaining());
        return TransferRunner.filtered(
                spirit.getInventory(), container, IdFilter.of(List.of(params.item())), transferRemaining());
    }

    @Override
    protected ScriptStatus finish(int moved, TransferRunner.Shortage shortage) {
        Container current = container;
        LOGGER.info(
                "[deposit] {}: контейнер{} — свободно {} из {} слотов, перенесено {}, решение: {}",
                spirit.displayName(),
                at(),
                current == null ? -1 : freeSlots(current),
                current == null ? -1 : current.getContainerSize(),
                moved,
                shortage == TransferRunner.Shortage.NONE ? "предметы приняты" : "нужен второй контейнер или сундук");
        return switch (shortage) {
            case NONE -> ScriptStatus.SUCCEEDED;
            case SOURCE_LACK ->
                failed("в инвентаре только " + moved + " из " + params.count() + " '" + params.item() + "': перенесено "
                        + moved + ", остального у духа нет");
            case DEST_FULL ->
                failed("контейнер полон" + at() + ": перенесено " + moved + " из " + params.count() + " '"
                        + params.item() + "', остальное осталось у духа; нужен второй контейнер или сундук");
        };
    }

    @Override
    protected AutoMove nextAutoMove(AutoContainerScan scan) {
        if (planned) {
            return null;
        }
        planned = true;
        ContainerChoice choice = ContainerPicker.choose(scan.snapshots(), blockCoord(), kind(scan));
        if (choice instanceof ContainerChoice.Refused refused) {
            autoRefusal = refused.reason();
            LOGGER.info(
                    "[deposit] {}: {} — решение: заказать сундук или освободить место",
                    spirit.displayName(),
                    refused.reason());
            return null;
        }
        int index = ((ContainerChoice.Chosen) choice).index();
        ContainerSnapshot chosen = scan.snapshots().get(index);
        LOGGER.info(
                "[deposit] {}: контейнер {} — свободно {} из {} слотов, кладу '{}' ×{}",
                spirit.displayName(),
                chosen.pos(),
                chosen.freeSlots(),
                chosen.slotCount(),
                params.item(),
                params.count());
        TransferRunner runner = TransferRunner.filtered(
                spirit.getInventory(),
                scan.containers().get(index),
                IdFilter.of(List.of(params.item())),
                params.count());
        return new AutoMove(
                scan.containers().get(index), scan.snapshots().get(index).pos(), index, runner);
    }

    @Override
    protected ScriptStatus finishAuto() {
        if (autoRefusal != null) {
            return failed(autoRefusal);
        }
        return finish(movedTotal(), lastShortage());
    }

    private Stack kind(AutoContainerScan scan) {
        IdFilter filter = IdFilter.of(List.of(params.item()));
        for (Stack stack : scan.mapper().map(spirit.getInventory())) {
            if (stack != null && filter.matches(stack.item())) {
                return stack;
            }
        }
        return new Stack(exactItem(), 0, 1, FALLBACK_MAX_STACK);
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

    private String exactItem() {
        return params.item().indexOf(':') < 0 ? "minecraft:" + params.item() : params.item();
    }

    private BlockCoord blockCoord() {
        return new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
    }
}
