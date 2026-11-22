package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.coordinator.work.WorkKind;
import com.theminespirit.core.coordinator.work.WorkTarget;
import com.theminespirit.core.engine.FailureFact;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.forestry.ChopMode;
import com.theminespirit.core.forestry.NotATree;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeDetection;
import com.theminespirit.core.forestry.TreeShape;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.ChopTreeParams;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

public final class ChopTreeScript implements ScriptInstance {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritEntity spirit;
    private final ChopTreeParams params;
    private final LevelTreeView treeView;
    private final ChopMode chopMode;
    private final TreeChop chop;
    private final ChopSearch search;
    private final ChopGroupPolicy groups;
    private final ChopTally tally = new ChopTally();
    private Tree tree;
    private boolean chopping;
    private int rejected;
    private int unreachable;
    private String failureReason;
    private FailureFact failureFact;
    private WorkTarget reserved;
    private Tree crowded;

    public ChopTreeScript(SpiritEntity spirit, ChopTreeParams params) {
        this.spirit = spirit;
        this.params = params;
        Set<Block> logBlocks = TreeBlocks.logs(params.species());
        this.treeView = new LevelTreeView(spirit.level(), logBlocks);
        this.search = new ChopSearch(spirit, params, logBlocks);
        this.groups = new ChopGroupPolicy(spirit, search);
        this.chopMode = groups.mode();
        this.chop = new TreeChop(spirit, params.replant(), chopMode, treeView);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез во время рубки");
        }
        return chopping ? advance(chop.tick()) : find();
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(Math.min(tally.chopped(), params.count()), params.count()));
    }

    @Override
    public void cancel() {
        chop.cancel();
        releaseReserved();
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
    public Optional<FailureFact> failureFact() {
        return Optional.ofNullable(failureFact);
    }

    @Override
    public Map<String, Object> saveState() {
        return tally.save();
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        tally.restore(state, params.count());
    }

    public String summary() {
        return tally.summary(params);
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.of(new ScriptResult.Text(summary()));
    }

    private ScriptStatus find() {
        if (tally.chopped() >= params.count()) {
            return ScriptStatus.SUCCEEDED;
        }
        Optional<BlockCoord> start = search.next();
        if (start.isEmpty()) {
            if (!search.done()) {
                return ScriptStatus.RUNNING;
            }
            if (search.restart()) {
                return ScriptStatus.RUNNING;
            }
            return crowdOrOutOfTrees();
        }
        if (!treeView.isLog(start.get())) {
            search.markKnown(List.of(start.get()));
            return ScriptStatus.RUNNING;
        }
        TreeDetection detection = TreeShape.detect(start.get(), treeView, true, chopMode);
        if (detection instanceof NotATree notATree) {
            return reject(notATree);
        }
        Tree found = (Tree) detection;
        if (groups.plannedGroup(found)) {
            groups.skipPlanned(found);
            return ScriptStatus.RUNNING;
        }
        return startChop(found, false);
    }

    private boolean reserve(Tree found) {
        WorkTarget target = new WorkTarget(WorkKind.TREE, found.base());
        if (target.equals(reserved)) {
            return true;
        }
        releaseReserved();
        if (!WorkReserveHub.reserve(spirit, target)) {
            crowded = found;
            search.markKnown(found.logs());
            return false;
        }
        reserved = target;
        return true;
    }

    private ScriptStatus crowdOrOutOfTrees() {
        if (crowded != null) {
            Tree deferred = crowded;
            crowded = null;
            return startChop(deferred, true);
        }
        return outOfTrees();
    }

    private void releaseReserved() {
        if (reserved != null) {
            WorkReserveHub.release(spirit, reserved);
            reserved = null;
        }
    }

    private ScriptStatus reject(NotATree notATree) {
        rejected++;
        if (rejected <= 5) {
            LOGGER.warn(
                    "[chop] дух {}: бревно {} не признано деревом — {}; брёвен {}, порода {}, список {}",
                    spirit.getName().getString(),
                    notATree.logs().stream().min(Comparator.naturalOrder()).orElse(null),
                    notATree.reason(),
                    notATree.logs().size(),
                    ChopTreeText.speciesText(params),
                    notATree.logs().stream().sorted().limit(6).toList());
        }
        search.markKnown(notATree.logs());
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus startChop(Tree found, boolean verify) {
        Optional<Tree> fresh = verify ? refresh(found) : Optional.of(found);
        if (fresh.isEmpty()) {
            return ScriptStatus.RUNNING;
        }
        if (!reserve(fresh.get())) {
            return ScriptStatus.RUNNING;
        }
        tree = fresh.get();
        chopping = true;
        search.logChosen(tree);
        chop.begin(tree);
        return ScriptStatus.RUNNING;
    }

    private Optional<Tree> refresh(Tree found) {
        Optional<BlockCoord> surviving =
                found.logs().stream().filter(treeView::isLog).findFirst();
        if (surviving.isEmpty()) {
            search.markKnown(found.logs());
            return Optional.empty();
        }
        TreeDetection detection = TreeShape.detect(surviving.get(), treeView, true, chopMode);
        if (detection instanceof Tree tree) {
            if (groups.plannedGroup(tree)) {
                search.markKnown(found.logs());
                return Optional.empty();
            }
            return Optional.of(tree);
        }
        search.markKnown(found.logs());
        return Optional.empty();
    }

    private ScriptStatus advance(ChopOutcome outcome) {
        return switch (outcome.state()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case DONE -> outcome.standingLogs() > 0 ? chopFailed(outcome) : finishTree(outcome);
            case PROTECTED -> protectedTree();
            case UNREACHABLE -> unreachableTree();
            case FAILED -> chopFailed(outcome);
        };
    }

    private ScriptStatus finishTree(ChopOutcome outcome) {
        releaseReserved();
        tally.choppedTree(outcome);
        search.markKnown(tree.logs());
        tree = null;
        chopping = false;
        if (tally.chopped() >= params.count()) {
            LOGGER.info("[wood] дух {} завершил chop_tree: {}", spirit.getUUID(), summary());
            return ScriptStatus.SUCCEEDED;
        }
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus chopFailed(ChopOutcome outcome) {
        if (tally.failed(outcome)) {
            failureFact = FailureFact.NO_PATH;
        }
        search.markKnown(tree.logs());
        releaseReserved();
        tree = null;
        chopping = false;
        return fail(outcome.reason());
    }

    private ScriptStatus protectedTree() {
        releaseReserved();
        tally.protectedTree();
        search.markKnown(tree.logs());
        tree = null;
        chopping = false;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus unreachableTree() {
        releaseReserved();
        List<BlockCoord> logs = tree.logs();
        tree = null;
        chopping = false;
        return unreachableLogs(logs);
    }

    private ScriptStatus unreachableLogs(List<BlockCoord> logs) {
        unreachable++;
        search.markKnown(logs);
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus outOfTrees() {
        int far = search.nearestFarDistance();
        String beyond = far < 0
                ? ""
                : "; ближайшее дерево к поселению " + far + " блоков при пределе " + search.maxDistance()
                        + " — посадите саженцы и дождитесь их роста";
        failureFact = spirit.level().isNight() ? FailureFact.NIGHT : unreachable == 0 ? FailureFact.NO_TREES : null;
        String area = chopMode == ChopMode.SETTLEMENT
                ? "в зоне поселения"
                : "в радиусе " + search.maxDistance() + " от поселения";
        return fail("срублено деревьев " + tally.chopped() + " из " + params.count() + ": деревья породы "
                + ChopTreeText.speciesText(params) + " " + area + " закончились (отвергнуто не-деревьев: "
                + rejected + ", задевают план: " + groups.plannedRejected() + ", недоступных: " + unreachable
                + ", пропущено защищённых: " + tally.protectedLogs() + ")" + beyond);
    }

    private ScriptStatus fail(String reason) {
        cancel();
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}
