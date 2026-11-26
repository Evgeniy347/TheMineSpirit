package com.theminespirit.neoforge.inventory;

import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

abstract class ContainerTask implements ScriptInstance {

    private enum Phase {
        FIND,
        APPROACH,
        TRANSFER,
        SORT
    }

    protected record AutoMove(Container container, BlockCoord pos, int snapshotIndex, TransferRunner runner) {}

    private static final Logger LOGGER = LogUtils.getLogger();
    protected final SpiritEntity spirit;
    protected Container container;
    protected BlockPos containerPos;

    private final ContainerLocation location;
    private final ReachApproach approach;
    private Phase phase = Phase.FIND;
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
        this.location = new ContainerLocation(ContainerScan.of(spirit, target, radius));
        this.approach = ReachApproach.withRoutes(spirit);
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

    protected boolean canTransferRemotely(BlockPos pos) {
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
        return location.maxSlicesPerTick();
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
        return Math.max(0, requestedCount() - movedBase - autoMoved);
    }

    protected TransferRunner.Shortage lastShortage() {
        return lastShortage;
    }

    private boolean isAuto() {
        return location.auto();
    }

    private void find() {
        ContainerLocation.Result found = location.tick(() -> nextAutoMove(location.autoScan()));
        switch (found) {
            case ContainerLocation.Waiting ignored -> {}
            case ContainerLocation.Finished ignored -> finishAutoMoves();
            case ContainerLocation.Failed failed -> fail(failed.reason());
            case ContainerLocation.Ready ready -> accept(ready);
        }
    }

    private void finishAutoMoves() {
        ScriptStatus done = finishAuto();
        if (done == ScriptStatus.FAILED) {
            fail(finishReason != null ? finishReason : "перенос не завершён: перенесено " + movedTotal());
        } else {
            succeed();
        }
    }

    private void accept(ContainerLocation.Ready ready) {
        current = ready.move();
        container = ready.container();
        containerPos = ready.pos();
        if (canTransferRemotely(containerPos)) {
            spirit.showInHand(shownWhileTransferring());
            runner = current == null ? beginTransfer(container) : current.runner();
            phase = Phase.TRANSFER;
        } else {
            approach.begin(containerPos);
            phase = Phase.APPROACH;
        }
    }

    private void approach() {
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> {
                spirit.showInHand(shownWhileTransferring());
                runner = isAuto() ? current.runner() : beginTransfer(container);
                phase = Phase.TRANSFER;
            }
            case STUCK, TIMEOUT -> {
                String reason = "дух не дошёл до контейнера: путь заблокирован или слишком далеко"
                        + (approach.refusal() == null ? "" : " (" + approach.refusal() + ")");
                LOGGER.info(
                        "[container] {} в {}: {}",
                        spirit.displayName(),
                        spirit.blockPosition().toShortString(),
                        reason);
                fail(reason);
            }
        }
    }

    private void transfer() {
        holdOpen();
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
        holdOpen();
        ContainerSort.apply(container, InventoryTables.values());
        if (isAuto()) {
            location.updateSnapshot(current.snapshotIndex());
            current = null;
            runner = null;
            find();
            return;
        }
        ScriptStatus done = finish(movedTotal(), runner == null ? TransferRunner.Shortage.NONE : runner.shortage());
        if (done == ScriptStatus.FAILED) {
            fail(finishReason != null ? finishReason : "перенос не завершён: перенесено " + movedTotal());
        } else {
            succeed();
        }
    }

    private void holdOpen() {
        if (containerPos != null) {
            ContainerOpenService.hold(spirit, containerPos);
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
