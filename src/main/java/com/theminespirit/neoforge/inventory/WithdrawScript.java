package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.script.TransferParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.world.Container;

public final class WithdrawScript extends ContainerTask {
    private final TransferParams params;

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
    protected TransferRunner beginTransfer(Container container) {
        if (transferRemaining() <= 0) {
            return null;
        }
        return TransferRunner.filtered(container, spirit.getInventory(),
                IdFilter.of(List.of(params.item())), transferRemaining());
    }

    @Override
    protected ScriptStatus finish(int moved, TransferRunner.Shortage shortage) {
        return switch (shortage) {
            case NONE -> ScriptStatus.SUCCEEDED;
            case SOURCE_LACK -> failed("в контейнере только " + moved + " из " + params.count() + " '"
                    + params.item() + "': перенесено " + moved + ", остального в контейнере нет");
            case DEST_FULL -> failed("инвентарь духа полон: перенесено " + moved + " из " + params.count() + " '"
                    + params.item() + "', остальное осталось в контейнере");
        };
    }
}
