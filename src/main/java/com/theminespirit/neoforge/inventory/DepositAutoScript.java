package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.inventory.ContainerChoice;
import com.theminespirit.core.inventory.ContainerPicker;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.script.TransferParams;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class DepositAutoScript implements ScriptInstance {
    private static final double ARRIVAL = 2.5;
    private static final int FALLBACK_MAX_STACK = 64;

    private enum Phase {
        FIND,
        APPROACH,
        TRANSFER
    }

    private final SpiritEntity spirit;
    private final TransferParams params;
    private final AutoContainerScan scan;
    private final Approach approach;
    private Phase phase = Phase.FIND;
    private ScriptStatus status = ScriptStatus.RUNNING;
    private String reason;
    private int chosen = -1;
    private TransferRunner runner;

    public DepositAutoScript(SpiritEntity spirit, TransferParams params) {
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
            return fail("дух исчез во время работы с контейнером");
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

    private void find() {
        if (!scan.tick()) {
            return;
        }
        ContainerChoice choice = ContainerPicker.choose(scan.snapshots(), blockCoord(), kind());
        if (choice instanceof ContainerChoice.Refused refused) {
            fail(refused.reason());
            return;
        }
        chosen = ((ContainerChoice.Chosen) choice).index();
        ContainerSnapshot target = scan.snapshots().get(chosen);
        approach.begin(new Vec3(target.pos().x() + 0.5, target.pos().y() + 0.5, target.pos().z() + 0.5), ARRIVAL);
        phase = Phase.APPROACH;
    }

    private void approach() {
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> {
                runner = TransferRunner.filtered(spirit.getInventory(), scan.containers().get(chosen),
                        IdFilter.of(List.of(params.item())), params.count());
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
                ContainerSort.apply(scan.containers().get(chosen), InventoryTables.values());
                scan.updateSnapshot(chosen);
                finish(runner.moved(), runner.shortage());
            }
            case FAILED -> fail(runner.failureReason());
            case CANCELLED -> fail("перенос прерван");
        }
    }

    private void finish(int moved, TransferRunner.Shortage shortage) {
        switch (shortage) {
            case NONE -> succeed();
            case SOURCE_LACK -> fail("в инвентаре только " + moved + " из " + params.count() + " '"
                    + params.item() + "': перенесено " + moved + ", остального у духа нет");
            case DEST_FULL -> fail("контейнер полон: перенесено " + moved + " из " + params.count() + " '"
                    + params.item() + "', остальное осталось у духа");
        }
    }

    private Stack kind() {
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
}