package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.ContainerTarget;
import com.theminespirit.neoforge.ReachApproach;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

abstract class ContainerTask implements ScriptInstance {

    private enum Phase {
        FIND,
        APPROACH,
        TRANSFER,
        SORT
    }

    protected record AutoMove(Container container, BlockCoord pos, int snapshotIndex, TransferRunner runner) {}

    protected final SpiritEntity spirit;

    private final ContainerFinder finder;
    private final AutoContainerScan scan;
    private final ReachApproach approach;
    private Phase phase = Phase.FIND;
    private Container container;
    private AutoMove current;
    private TransferRunner runner;
    private TransferRunner.Shortage lastShortage = TransferRunner.Shortage.NONE;
    private ScriptStatus status = ScriptStatus.RUNNING;
    private String reason;
    private String finishReason;
    private int movedBase;
    private int autoMoved;

    protected ContainerTask(SpiritEntity spirit, ContainerTarget target, int radius) {
        this.spirit = spirit;
        if (target instanceof ContainerTarget.Auto) {
            this.finder = null;
            this.scan = new AutoContainerScan(spirit.level(), spirit.blockPosition(), radius);
        } else {
            this.finder = new ContainerFinder(spirit.level(), spirit.blockPosition(), target, radius);
            this.scan = null;
        }
        this.approach = new ReachApproach(spirit);
    }

    protected abstract TransferRunner beginTransfer(Container container);

    protected ItemStack shownWhileTransferring() {
        return ItemStack.EMPTY;
    }

    protected abstract ScriptStatus finish(int moved, TransferRunner.Shortage shortage);

    protected int requestedCount() {
        return 0;
    }

    protected boolean persistsProgress() {
        return false;
    }

    protected boolean skipAsDone() {
        return false;
    }

    protected AutoMove nextAutoMove(AutoContainerScan scan) {
        return null;
    }

    protected ScriptStatus finishAuto() {
        return finish(movedTotal(), lastShortage);
    }

    @Override
    public final ScriptStatus tick() {
        if (status != ScriptStatus.RUNNING) {
            return status;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время работы с контейнером");
        }
        if (phase == Phase.FIND && skipAsDone()) {
            return succeed();
        }
        switch (phase) {
            case FIND -> find();
            case APPROACH -> approach();
            case TRANSFER -> transfer();
            case SORT -> sort();
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
    public Map<String, Object> saveState() {
        return persistsProgress() ? Map.of("moved", movedTotal()) : Map.of();
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (!persistsProgress()) {
            return;
        }
        Object value = state.get("moved");
        if (value instanceof Number number) {
            restoredMoved(Math.max(0, number.intValue()));
        }
        if (isAuto()) {
            return;
        }
        if (container != null && phase == Phase.TRANSFER) {
            if (runner != null) {
                runner.cancel();
            }
            runner = transferRemaining() > 0 ? beginTransfer(container) : null;
        }
    }

    @Override
    public String failureReason() {
        return reason;
    }

    public int movedTotal() {
        return movedBase + autoMoved + (runner == null ? 0 : runner.moved());
    }

    public int maxSlicesPerTick() {
        return isAuto() ? scan.maxSlicesPerTick() : finder.maxSlicesPerTick();
    }

    public int maxMovesPerTick() {
        return runner == null ? 0 : runner.maxMovesPerTick();
    }

    protected void restoredMoved(int moved) {
        movedBase = moved;
    }

    protected ScriptStatus failed(String message) {
        finishReason = message;
        return ScriptStatus.FAILED;
    }

    protected int transferRemaining() {
        return Math.max(0, requestedCount() - movedBase);
    }

    protected TransferRunner.Shortage lastShortage() {
        return lastShortage;
    }

    private boolean isAuto() {
        return scan != null;
    }

    private void find() {
        if (isAuto()) {
            if (!scan.tick()) {
                return;
            }
            advanceAuto();
            return;
        }
        if (!finder.tick()) {
            return;
        }
        if (finder.found() == null) {
            fail(finder.refusal());
            return;
        }
        BlockPos pos = finder.found();
        switch (ContainerAccess.open(finder.level(), pos)) {
            case ContainerAccess.Opened opened -> {
                container = opened.container();
                approach.begin(pos);
                phase = Phase.APPROACH;
            }
            case ContainerAccess.Refused refused -> fail(refused.reason());
        }
    }

    private void advanceAuto() {
        AutoMove move = nextAutoMove(scan);
        if (move == null) {
            ScriptStatus done = finishAuto();
            if (done == ScriptStatus.FAILED) {
                fail(finishReason != null ? finishReason : "перенос не завершён: перенесено " + movedTotal());
            } else {
                succeed();
            }
            return;
        }
        current = move;
        container = move.container();
        approach.begin(new BlockPos(move.pos().x(), move.pos().y(), move.pos().z()));
        phase = Phase.APPROACH;
    }

    private void approach() {
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> {
                spirit.showInHand(shownWhileTransferring());
                runner = isAuto() ? current.runner() : beginTransfer(container);
                phase = Phase.TRANSFER;
            }
            case STUCK, TIMEOUT ->
                fail("дух не дошёл до контейнера: путь заблокирован или слишком далеко"
                        + (approach.refusal() == null ? "" : " (" + approach.refusal() + ")"));
        }
    }

    private void transfer() {
        if (runner == null) {
            phase = Phase.SORT;
            return;
        }
        switch (runner.tick()) {
            case RUNNING -> {}
            case DONE -> {
                lastShortage = runner.shortage();
                if (isAuto()) {
                    autoMoved += runner.moved();
                    runner = null;
                }
                phase = Phase.SORT;
            }
            case FAILED -> fail(runner.failureReason());
            case CANCELLED -> fail("перенос прерван");
        }
    }

    private void sort() {
        ContainerSort.apply(container, InventoryTables.values());
        if (isAuto()) {
            scan.updateSnapshot(current.snapshotIndex());
            current = null;
            runner = null;
            advanceAuto();
            return;
        }
        ScriptStatus done = finish(movedTotal(), runner == null ? TransferRunner.Shortage.NONE : runner.shortage());
        if (done == ScriptStatus.FAILED) {
            fail(finishReason != null ? finishReason : "перенос не завершён: перенесено " + movedTotal());
        } else {
            succeed();
        }
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
