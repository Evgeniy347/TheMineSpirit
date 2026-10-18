package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.forestry.NotATree;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeDetection;
import com.theminespirit.core.forestry.TreeShape;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.script.ChopTargetParams;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

public final class ChopTargetScript implements ScriptInstance {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritEntity spirit;
    private final ChopTargetParams params;
    private final Set<Block> logBlocks;
    private final IdFilter logFilter;
    private final LevelTreeView treeView;
    private final TreeChop chop;
    private boolean started;
    private int chopped;
    private int standingLogs;
    private int plantedSaplings;
    private int skippedCells;
    private boolean lackedSaplings;
    private String failureReason;

    public ChopTargetScript(SpiritEntity spirit, ChopTargetParams params) {
        this.spirit = spirit;
        this.params = params;
        this.logBlocks = TreeBlocks.logs(TreeSpecies.ALL);
        this.logFilter = TreeBlocks.filter(TreeSpecies.ALL);
        this.treeView = new LevelTreeView(spirit.level(), logBlocks);
        this.chop = new TreeChop(spirit, logFilter, params.replant(), treeView);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез во время рубки");
        }
        return started ? advance(chop.tick()) : begin();
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(Math.min(chopped, 1), 1));
    }

    @Override
    public void cancel() {
        chop.cancel();
    }

    @Override
    public void suspend() {
        chop.suspend();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.of(new ScriptResult.Text(summary()));
    }

    public String summary() {
        StringBuilder text = new StringBuilder("срублено 1 из 1");
        if (standingLogs > 0) {
            text.append("; оставлено брёвен выше досягаемости: ").append(standingLogs);
        }
        if (chop.pillarFailure() != null) {
            text.append("; столб: ").append(chop.pillarFailure());
        }
        if (params.replant()) {
            text.append("; replant: ").append(replantText());
        }
        return text.toString();
    }

    private ScriptStatus begin() {
        BlockPos target = new BlockPos(params.x(), params.y(), params.z());
        if (!treeView.isLog(new BlockCoord(params.x(), params.y(), params.z()))) {
            return fail("нет дерева по координате " + target.toShortString() + ": там не бревно. Укажите координату "
                    + "ствола настоящего дерева");
        }
        TreeDetection detection = TreeShape.detect(new BlockCoord(params.x(), params.y(), params.z()), treeView);
        if (detection instanceof NotATree notATree) {
            return fail("по координате " + target.toShortString() + " не дерево (" + reasonText(notATree.reason())
                    + "): укажите координату ствола настоящего дерева");
        }
        started = true;
        chop.begin((Tree) detection);
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus advance(TreeChop.Outcome outcome) {
        return switch (outcome.state()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case DONE -> finish(outcome);
            case PROTECTED -> fail("дерево по координате " + targetText()
                    + " защищено политикой: рубка отменена, постройку и защищённые блоки дух не ломает");
            case UNREACHABLE -> fail("дерево по координате " + targetText()
                    + " недостижимо: подойти к стволу не удалось");
            case FAILED -> fail(outcome.reason());
        };
    }

    private ScriptStatus finish(TreeChop.Outcome outcome) {
        chopped = 1;
        standingLogs = outcome.standingLogs();
        plantedSaplings = outcome.plantedSaplings();
        skippedCells = outcome.skippedCells();
        lackedSaplings = outcome.lackedSaplings();
        LOGGER.info("Дух {} завершил chop_target: {}", spirit.getUUID(), summary());
        return ScriptStatus.SUCCEEDED;
    }

    private String replantText() {
        if (lackedSaplings) {
            return "нет саженцев" + (plantedSaplings > 0 ? ", посажено " + plantedSaplings : "");
        }
        String text = "посажено " + plantedSaplings;
        return skippedCells > 0 ? text + ", клеток пропущено " + skippedCells : text;
    }

    private String targetText() {
        return new BlockPos(params.x(), params.y(), params.z()).toShortString();
    }

    private ScriptStatus fail(String reason) {
        cancel();
        failureReason = reason;
        return ScriptStatus.FAILED;
    }

    private static String reasonText(NotATree.Reason reason) {
        return switch (reason) {
            case NO_LEAVES -> "нет листвы";
            case NO_SOIL -> "нет почвы под стволом";
            case TOO_BIG -> "слишком большое";
            case NOT_A_TRUNK -> "это не ствол";
            case BUILT_NEARBY -> "рядом постройка";
        };
    }
}
