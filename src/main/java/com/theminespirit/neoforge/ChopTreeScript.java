package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.NotATree;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeDetection;
import com.theminespirit.core.forestry.TreeShape;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.script.ChopTreeParams;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

public final class ChopTreeScript implements ScriptInstance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CHOPPED_KEY = "chopped";
    private static final String STANDING_KEY = "standingLogs";
    private static final String PROTECTED_KEY = "protectedLogs";
    private static final String PLANTED_KEY = "plantedSaplings";
    private static final String SKIPPED_KEY = "skippedCells";
    private static final String LACKED_KEY = "lackedSaplings";

    private final SpiritEntity spirit;
    private final ChopTreeParams params;
    private final IdFilter logFilter;
    private final LevelTreeView treeView;
    private final TreeChop chop;
    private final ChopSearch search;
    private Tree tree;
    private boolean chopping;
    private int standingLogs;
    private int protectedLogs;
    private int plantedSaplings;
    private int skippedCells;
    private boolean lackedSaplings;
    private int chopped;
    private int rejected;
    private int unreachable;
    private int consecutiveUnreachable;
    private String failureReason;
    private WorkTarget reserved;
    private Tree crowded;

    public ChopTreeScript(SpiritEntity spirit, ChopTreeParams params) {
        this.spirit = spirit;
        this.params = params;
        Set<Block> logBlocks = TreeBlocks.logs(params.species());
        this.logFilter = TreeBlocks.filter(params.species());
        this.treeView = new LevelTreeView(spirit.level(), logBlocks);
        this.search = new ChopSearch(spirit, params, logBlocks);
        this.chop = new TreeChop(spirit, logFilter, params.replant(), false, treeView);
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
        return Optional.of(new Progress(Math.min(chopped, params.count()), params.count()));
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
    public Map<String, Object> saveState() {
        return Map.of(
                CHOPPED_KEY,
                chopped,
                STANDING_KEY,
                standingLogs,
                PROTECTED_KEY,
                protectedLogs,
                PLANTED_KEY,
                plantedSaplings,
                SKIPPED_KEY,
                skippedCells,
                LACKED_KEY,
                lackedSaplings);
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        chopped = readCount(state, CHOPPED_KEY, params.count(), chopped);
        standingLogs = readCount(state, STANDING_KEY, Integer.MAX_VALUE, standingLogs);
        protectedLogs = readCount(state, PROTECTED_KEY, Integer.MAX_VALUE, protectedLogs);
        plantedSaplings = readCount(state, PLANTED_KEY, Integer.MAX_VALUE, plantedSaplings);
        skippedCells = readCount(state, SKIPPED_KEY, Integer.MAX_VALUE, skippedCells);
        Object lacked = state.get(LACKED_KEY);
        if (lacked != null) {
            if (!(lacked instanceof Boolean flag)) {
                throw new IllegalArgumentException("Состояние chop_tree: '" + LACKED_KEY + "' = " + lacked
                        + ", а ожидалось булево значение: снимок задачи повреждён или записан другой версией мода, "
                        + "проверьте его");
            }
            lackedSaplings = flag;
        }
    }

    private static int readCount(Map<String, Object> state, String key, int bound, int previous) {
        Object saved = state.get(key);
        if (saved == null) {
            return previous;
        }
        if (!(saved instanceof Number number) || number.intValue() < 0 || number.intValue() > bound) {
            throw new IllegalArgumentException("Состояние chop_tree: '" + key + "' = " + saved
                    + ", а ожидалось целое число от 0 до " + bound
                    + ": снимок задачи повреждён или записан другой версией мода, проверьте его");
        }
        return number.intValue();
    }

    public String summary() {
        StringBuilder text = new StringBuilder("срублено деревьев ")
                .append(chopped)
                .append(" из ")
                .append(params.count());
        if (standingLogs > 0) {
            text.append("; оставлено брёвен: ").append(standingLogs);
        }
        if (protectedLogs > 0) {
            text.append("; пропущено защищённых: ").append(protectedLogs);
        }
        if (params.replant()) {
            text.append("; replant: ").append(replantText());
        }
        return text.toString();
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.of(new ScriptResult.Text(summary()));
    }

    private ScriptStatus find() {
        if (chopped >= params.count()) {
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
        TreeDetection detection = TreeShape.detect(start.get(), treeView);
        if (detection instanceof NotATree notATree) {
            return reject(notATree);
        }
        Tree found = (Tree) detection;
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
        TreeDetection detection = TreeShape.detect(surviving.get(), treeView);
        if (detection instanceof Tree tree) {
            return Optional.of(tree);
        }
        search.markKnown(found.logs());
        return Optional.empty();
    }

    private ScriptStatus advance(ChopOutcome outcome) {
        return switch (outcome.state()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case DONE -> finishTree(outcome);
            case PROTECTED -> protectedTree();
            case UNREACHABLE -> unreachableTree();
            case FAILED -> fail(outcome.reason());
        };
    }

    private ScriptStatus finishTree(ChopOutcome outcome) {
        releaseReserved();
        chopped++;
        standingLogs += outcome.standingLogs();
        plantedSaplings += outcome.plantedSaplings();
        skippedCells += outcome.skippedCells();
        lackedSaplings |= outcome.lackedSaplings();
        consecutiveUnreachable = 0;
        search.markKnown(tree.logs());
        tree = null;
        chopping = false;
        if (chopped >= params.count()) {
            LOGGER.info("[wood] дух {} завершил chop_tree: {}", spirit.getUUID(), summary());
            return ScriptStatus.SUCCEEDED;
        }
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus protectedTree() {
        releaseReserved();
        protectedLogs++;
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
        consecutiveUnreachable++;
        search.markKnown(logs);
        if (consecutiveUnreachable > ChopTuning.MAX_REJECTED_TREES) {
            return fail("больше " + ChopTuning.MAX_REJECTED_TREES + " недоступных деревьев подряд: срублено деревьев "
                    + chopped + " из " + params.count() + ", недоступных деревьев " + unreachable);
        }
        return ScriptStatus.RUNNING;
    }

    private String replantText() {
        if (lackedSaplings) {
            return "нет саженцев" + (plantedSaplings > 0 ? ", посажено " + plantedSaplings : "");
        }
        String text = "посажено " + plantedSaplings;
        return skippedCells > 0 ? text + ", клеток пропущено " + skippedCells : text;
    }

    private ScriptStatus outOfTrees() {
        int far = search.nearestFarDistance();
        String beyond = far < 0
                ? ""
                : "; ближайшее дерево к поселению " + far + " блоков при пределе " + search.maxDistance()
                        + " — посадите саженцы и дождитесь их роста";
        return fail("срублено деревьев " + chopped + " из " + params.count() + ": деревья породы " + speciesText()
                + " в радиусе " + search.maxDistance() + " от поселения закончились (отвергнуто не-деревьев: "
                + rejected + ", недоступных: " + unreachable + ", пропущено защищённых: " + protectedLogs + ")"
                + beyond);
    }

    private ScriptStatus fail(String reason) {
        cancel();
        failureReason = reason;
        return ScriptStatus.FAILED;
    }

    private String speciesText() {
        return params.species().size() == 1 ? params.species().get(0).id() : TreeSpecies.ANY;
    }
}
