package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.mining.MineColumn;
import com.theminespirit.core.mining.MinePlan;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotProtection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class MineBuildRun implements MineBuildCells.Host, MineBuildCycle.Host {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritEntity spirit;
    private final Level level;
    private final LevelSafetyView safety;
    private final BlockBreaker breaker;
    private final BlockPlacer placer;
    private final Approach approach;
    private final MineFinish finish;
    private final List<MineColumn> plan;
    private final Set<MineColumn> open = new HashSet<>();
    private final Set<MineColumn> closed = new HashSet<>();
    private final Set<BlockCoord> openCells = new HashSet<>();
    private final Map<MineColumn, MineColumn> parents = new HashMap<>();
    private final List<Schematic.PlacedEntry> torches;
    private final MineCells cells;
    private final MineOpening entranceOpening;
    private final MineBuildCells ore;
    private final MineBuildCycle cycle;
    private final MineStepWalker stepWalker;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private @Nullable String failureReason;

    MineBuildRun(SpiritEntity spirit, MineLayout layout, BuildRotation rotation) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.safety = new LevelSafetyView(level);
        this.breaker = new BlockBreaker(spirit);
        this.placer = new BlockPlacer(spirit, PlotProtection.Ground.PLAN_BUILD);
        this.approach = new Approach(spirit);
        this.stepWalker = new MineStepWalker(approach, safety);
        this.plan = new ArrayList<>(MinePlan.order(MinePlan.columns(layout.floors(), layout.air()), layout.entrance()));
        MineAimStep aim = new MineAimStep(spirit, level, safety, plan);
        this.torches = new ArrayList<>(layout.optional());
        Set<BlockCoord> walls = new HashSet<>(layout.floors());
        plan.forEach(column -> walls.remove(column.floor()));
        this.cells = new MineCells(level, walls);
        this.finish = new MineFinish(spirit, placer, approach, rotation, torches, parents, open);
        this.entranceOpening = MineOpening.of(safety);
        this.ore =
                new MineBuildCells(spirit, level, placer, breaker, aim, plan, parents, cells, closed, openCells, this);
        this.cycle = new MineBuildCycle(
                spirit,
                level,
                safety,
                breaker,
                finish,
                plan,
                open,
                closed,
                openCells,
                parents,
                cells,
                entranceOpening,
                stepWalker,
                ore,
                this);
        if (plan.isEmpty()) {
            LOGGER.info(
                    "[mine] {}: вход {} уже на глубине цели спуска — 0 ступеней, уровень считается достигнутым",
                    spirit.displayName(),
                    layout.entrance());
        }
    }

    ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время проходки шахты");
        }
        cycle.tick();
        return outcome;
    }

    ScriptInstance.Progress progress() {
        return new ScriptInstance.Progress(open.size(), Math.max(1, plan.size() - closed.size()));
    }

    Optional<ScriptResult> result() {
        if (outcome != ScriptStatus.SUCCEEDED) {
            return Optional.empty();
        }
        return Optional.of(new ScriptResult.Data(Map.of(
                "columns", open.size(),
                "blocks_dug", cycle.dug(),
                "floors_placed", ore.floors(),
                "plugs", ore.plugs(),
                "ores_left", ore.leftOres().size(),
                "detours", ore.detours())));
    }

    void cancel() {
        breaker.cancel();
        approach.cancel();
        ore.resetAim();
        stepWalker.reset();
    }

    void suspend() {
        approach.suspend();
        ore.resetAim();
        stepWalker.reset();
    }

    @Nullable
    String failureReason() {
        return failureReason;
    }

    @Override
    public void failed(String reason) {
        fail(reason);
    }

    @Override
    public void succeeded() {
        outcome = ScriptStatus.SUCCEEDED;
    }

    @Override
    public void aim() {
        cycle.aim();
    }

    @Override
    public void pick() {
        cycle.pick();
    }

    @Override
    public void breaking() {
        cycle.breaking();
    }

    private ScriptStatus fail(String reason) {
        if (outcome == ScriptStatus.RUNNING) {
            failureReason = reason;
            outcome = ScriptStatus.FAILED;
            cancel();
            LOGGER.info("Шахта: дух {} остановил проходку: {}", spirit.getStringUUID(), reason);
        }
        return outcome;
    }
}
