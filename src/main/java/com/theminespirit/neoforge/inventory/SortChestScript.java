package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.SortChestParams;
import com.theminespirit.neoforge.SpiritEntity;

import net.minecraft.world.Container;

public final class SortChestScript extends ContainerTask {
    public SortChestScript(SpiritEntity spirit, SortChestParams params) {
        super(spirit, params.container(), params.radius());
    }

    @Override
    protected TransferRunner beginTransfer(Container container) {
        return null;
    }

    @Override
    protected ScriptStatus finish(int moved, TransferRunner.Shortage shortage) {
        return ScriptStatus.SUCCEEDED;
    }
}
