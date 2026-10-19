package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.ContainerChoice;
import com.theminespirit.core.inventory.ContainerPicker;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.script.TransferParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.world.Container;

public final class DepositScript extends ContainerTask {
    private static final int FALLBACK_MAX_STACK = 64;

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
        return TransferRunner.filtered(
                spirit.getInventory(), container, IdFilter.of(List.of(params.item())), transferRemaining());
    }

    @Override
    protected ScriptStatus finish(int moved, TransferRunner.Shortage shortage) {
        return switch (shortage) {
            case NONE -> ScriptStatus.SUCCEEDED;
            case SOURCE_LACK ->
                failed("в инвентаре только " + moved + " из " + params.count() + " '" + params.item() + "': перенесено "
                        + moved + ", остального у духа нет");
            case DEST_FULL ->
                failed("контейнер полон: перенесено " + moved + " из " + params.count() + " '" + params.item()
                        + "', остальное осталось у духа");
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
            return null;
        }
        int index = ((ContainerChoice.Chosen) choice).index();
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

    private String exactItem() {
        return params.item().indexOf(':') < 0 ? "minecraft:" + params.item() : params.item();
    }

    private BlockCoord blockCoord() {
        return new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
    }
}
