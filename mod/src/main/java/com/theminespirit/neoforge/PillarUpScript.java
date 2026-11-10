package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.PillarUpParams;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PillarUpScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final int height;
    private final String requestedBlock;
    private final boolean down;

    private PillarColumn column;
    private PillarDismantler dismantler;
    private int returned;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;

    public PillarUpScript(SpiritEntity spirit, PillarUpParams params) {
        this.spirit = spirit;
        this.height = params.height();
        this.requestedBlock = params.block();
        this.down = params.down();
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время стройки столба");
        }
        if (dismantler != null) {
            return switch (dismantler.tick()) {
                case RUNNING -> ScriptStatus.RUNNING;
                case DONE -> {
                    returned = dismantler.returned();
                    outcome = ScriptStatus.SUCCEEDED;
                    yield outcome;
                }
                case FAILED -> fail(dismantler.failureReason());
            };
        }
        if (column == null && !startColumn()) {
            return outcome;
        }
        return switch (column.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case BUILT -> {
                if (!down) {
                    outcome = ScriptStatus.SUCCEEDED;
                    yield outcome;
                }
                dismantler = new PillarDismantler(spirit, topDown(column.cells()), column.lease());
                yield ScriptStatus.RUNNING;
            }
            case FAILED -> fail(column.failureReason());
        };
    }

    private boolean startColumn() {
        String blockId = requestedBlock != null ? requestedBlock : PillarColumn.cheapBlock(spirit);
        if (blockId == null) {
            fail("нет материала для столба: нужны земля или булыжник, есть 0");
            return false;
        }
        int have = PillarColumn.count(spirit, blockId);
        if (have < height) {
            fail("нет материала '" + blockId + "': нужно " + height + ", есть " + have);
            return false;
        }
        column = new PillarColumn(spirit, blockId, height);
        return true;
    }

    private static List<BlockCoord> topDown(List<BlockCoord> cells) {
        List<BlockCoord> order = new ArrayList<>(cells);
        Collections.reverse(order);
        return order;
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        if (column != null && column.lease() != null) {
            column.lease().orphan();
        }
        return outcome;
    }

    @Override
    public void cancel() {
        if (column != null && column.lease() != null) {
            column.lease().orphan();
        }
    }

    @Override
    public Optional<Progress> progress() {
        int done = (column == null ? 0 : column.built()) + returned;
        int target = down ? height * 2 : height;
        return Optional.of(new Progress(Math.min(done, target), target));
    }

    @Override
    public Optional<ScriptResult> result() {
        if (outcome != ScriptStatus.SUCCEEDED) {
            return Optional.empty();
        }
        int built = column == null ? 0 : column.built();
        String text =
                "поднято на " + built + (down ? "; столб разобран, блоков возвращено " + returned : "; столб оставлен");
        return Optional.of(new ScriptResult.Text(text));
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of();
    }

    @Override
    public void restoreState(Map<String, Object> state) {}
}
