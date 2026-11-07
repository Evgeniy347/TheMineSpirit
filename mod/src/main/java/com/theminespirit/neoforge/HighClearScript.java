package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.ScaffoldClearParams;
import com.theminespirit.neoforge.board.ScaffoldStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

final class HighClearScript implements ScriptInstance {
    private enum Phase {
        ASCEND,
        CLEAR,
        DESCEND,
        DONE
    }

    private final SpiritEntity spirit;
    private final ScaffoldClearParams params;
    private final List<BlockCoord> targets = new ArrayList<>();
    private final List<BlockCoord> own = new ArrayList<>();
    private final BlockBreaker breaker;
    private Phase phase = Phase.ASCEND;
    private @Nullable PillarColumn pillar;
    private @Nullable PillarDismantler dismantler;
    private @Nullable BlockCoord breaking;
    private int cursor;
    private int removed;
    private @Nullable String failure;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private boolean claimed;

    HighClearScript(SpiritEntity spirit, ScaffoldClearParams params) {
        this.spirit = spirit;
        this.params = params;
        this.breaker = new BlockBreaker(spirit);
        this.targets.addAll(params.cells());
        this.own.addAll(params.tower().isEmpty() ? towerCells(params) : params.tower());
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез на подмости: башня остаётся, её защита истечёт");
        }
        claim();
        switch (phase) {
            case ASCEND -> ascend();
            case CLEAR -> clear();
            case DESCEND -> descend();
            case DONE -> finish();
        }
        return outcome;
    }

    private void ascend() {
        if (params.height() < 1) {
            phase = Phase.CLEAR;
            return;
        }
        if (pillar == null) {
            String block = block();
            if (block == null) {
                failure = "нет дешёвых блоков на башню: нужна земля или булыжник";
                phase = Phase.DESCEND;
                return;
            }
            pillar = new PillarColumn(spirit, block, params.height(), new BlockPos(params.x(), params.y(), params.z()));
        }
        PillarColumn.Outcome result = pillar.tick();
        if (result == PillarColumn.Outcome.BUILT) {
            phase = Phase.CLEAR;
        } else if (result == PillarColumn.Outcome.FAILED) {
            failure = "башня не построена: " + pillar.failureReason();
            phase = Phase.DESCEND;
        }
    }

    private void clear() {
        if (breaking != null) {
            BlockBreaker.Progress progress = breaker.tick();
            if (progress != BlockBreaker.Progress.BREAKING) {
                store().removed(breaking);
                removed++;
                breaking = null;
                cursor++;
            }
            return;
        }
        while (cursor < targets.size()) {
            BlockCoord cell = targets.get(cursor);
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (spirit.level().getBlockState(pos).isAir()) {
                store().removed(cell);
                cursor++;
                continue;
            }
            BlockBreaker.Begin begin = breaker.begin(pos);
            if (begin instanceof BlockBreaker.Started) {
                breaking = cell;
                return;
            }
            if (begin instanceof BlockBreaker.Protected protectedBlock) {
                failure = "клетка " + cell + " под защитой: " + protectedBlock.reason();
            } else {
                failure = "клетка " + cell + " не ломается: " + ((BlockBreaker.Refused) begin).message();
            }
            phase = Phase.DESCEND;
            return;
        }
        phase = Phase.DESCEND;
    }

    private void descend() {
        if (own.isEmpty()) {
            phase = Phase.DONE;
            return;
        }
        if (dismantler == null) {
            List<BlockCoord> topDown = new ArrayList<>(own);
            topDown.sort((first, second) -> Integer.compare(second.y(), first.y()));
            dismantler = new PillarDismantler(spirit, topDown);
        }
        PillarDismantler.Outcome result = dismantler.tick();
        for (BlockCoord cell : own) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (spirit.level().getBlockState(pos).isAir()) {
                store().removed(cell);
            }
        }
        if (result == PillarDismantler.Outcome.DONE) {
            phase = Phase.DONE;
        } else if (result == PillarDismantler.Outcome.FAILED) {
            failure = "башня не разобрана: " + dismantler.failureReason();
            phase = Phase.DONE;
        }
    }

    private void finish() {
        if (failure == null) {
            store().release(params.scaffoldId());
            outcome = ScriptStatus.SUCCEEDED;
            return;
        }
        outcome = ScriptStatus.FAILED;
    }

    private void claim() {
        if (claimed) {
            return;
        }
        claimed = true;
        Level level = spirit.level();
        if (level instanceof ServerLevel server) {
            List<BlockCoord> cells = new ArrayList<>(targets);
            cells.addAll(own);
            ScaffoldStore.get(server).claim(params.scaffoldId(), spirit.getUUID(), server.getGameTime(), cells);
        }
    }

    private String block() {
        if (!params.autoBlock()) {
            return params.block().contains(":") ? params.block() : "minecraft:" + params.block();
        }
        return pillarBlock(spirit);
    }

    private static @Nullable String pillarBlock(SpiritEntity spirit) {
        if (PillarColumn.count(spirit, "minecraft:dirt") >= 1) {
            return "minecraft:dirt";
        }
        if (PillarColumn.count(spirit, "minecraft:cobblestone") >= 1) {
            return "minecraft:cobblestone";
        }
        return null;
    }

    private ScaffoldStore store() {
        return ScaffoldStore.get((ServerLevel) spirit.level());
    }

    private static List<BlockCoord> towerCells(ScaffoldClearParams params) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int step = 0; step < params.height(); step++) {
            cells.add(new BlockCoord(params.x(), params.y() + step, params.z()));
        }
        return cells;
    }

    private ScriptStatus fail(String reason) {
        failure = reason;
        outcome = ScriptStatus.FAILED;
        return outcome;
    }

    @Override
    public Optional<Progress> progress() {
        int target = Math.max(1, targets.size() + own.size());
        return Optional.of(new Progress(Math.min(removed, target), target));
    }

    @Override
    public Optional<ScriptResult> result() {
        if (outcome != ScriptStatus.SUCCEEDED) {
            return Optional.empty();
        }
        return Optional.of(new ScriptResult.Text("подмость разобрана, снято столбов: " + removed));
    }

    @Override
    public void cancel() {
        breaker.cancel();
    }

    @Override
    public void suspend() {
        breaker.cancel();
    }

    @Override
    public @Nullable String failureReason() {
        return failure;
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of(
                "phase", phase.name(),
                "cursor", cursor,
                "removed", removed,
                "claimed", claimed,
                "failure", failure == null ? "" : failure);
    }
}
