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
import com.theminespirit.core.forestry.TreeCandidates;
import com.theminespirit.core.forestry.TreeDetection;
import com.theminespirit.core.forestry.TreeShape;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.script.ChopTreeParams;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
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
    private final Set<Block> logBlocks;
    private final IdFilter logFilter;
    private final BlockLocator.Search search;
    private final LevelTreeView treeView;
    private final TreeChop chop;
    private final TreeCandidates candidates = new TreeCandidates(ChopTuning.SEARCH_MAX_LOGS);
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
        this.logBlocks = TreeBlocks.logs(params.species());
        this.logFilter = TreeBlocks.filter(params.species());
        BlockPos center = spirit.blockPosition();
        this.search = BlockLocator.search(spirit.level(), center, params.radius(), Set.copyOf(logBlocks));
        this.treeView = new LevelTreeView(spirit.level(), logBlocks);
        this.chop = new TreeChop(spirit, logFilter, params.replant());
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
        return Map.of(CHOPPED_KEY, chopped,
                STANDING_KEY, standingLogs,
                PROTECTED_KEY, protectedLogs,
                PLANTED_KEY, plantedSaplings,
                SKIPPED_KEY, skippedCells,
                LACKED_KEY, lackedSaplings);
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
        StringBuilder text = new StringBuilder("срублено деревьев ").append(chopped).append(" из ")
                .append(params.count());
        if (standingLogs > 0) {
            text.append("; оставлено брёвен выше досягаемости: ").append(standingLogs);
        }
        if (protectedLogs > 0) {
            text.append("; пропущено защищённых: ").append(protectedLogs);
        }
        if (chop.pillarFailure() != null) {
            text.append("; столб: ").append(chop.pillarFailure());
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

    public long treeBlockReads() {
        return treeView.blockReads();
    }

    public int searchExaminedPositions() {
        return search.examinedPositions();
    }

    public int searchMaxExaminedPositionsPerTick() {
        return search.maxExaminedPerStep();
    }

    private ScriptStatus find() {
        if (chopped >= params.count()) {
            return ScriptStatus.SUCCEEDED;
        }
        locate();
        Optional<BlockCoord> start = candidates.nearest(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
        if (start.isEmpty()) {
            if (!search.done()) {
                return ScriptStatus.RUNNING;
            }
            return crowdOrOutOfTrees();
        }
        if (!treeView.isLog(start.get())) {
            candidates.markKnown(List.of(start.get()));
            return ScriptStatus.RUNNING;
        }
        TreeDetection detection = TreeShape.detect(start.get(), treeView);
        if (detection instanceof NotATree notATree) {
            return reject(notATree);
        }
        Tree found = (Tree) detection;
        WorkTarget target = new WorkTarget(WorkKind.TREE, found.base());
        if (!target.equals(reserved)) {
            releaseReserved();
            if (!WorkReserveHub.reserve(spirit, target)) {
                crowded = found;
                candidates.markKnown(found.logs());
                return ScriptStatus.RUNNING;
            }
            reserved = target;
        }
        return startChop(found);
    }

    private ScriptStatus crowdOrOutOfTrees() {
        if (crowded != null) {
            Tree deferred = crowded;
            crowded = null;
            return startChop(deferred);
        }
        return outOfTrees();
    }

    private void releaseReserved() {
        if (reserved != null) {
            WorkReserveHub.release(spirit, reserved);
            reserved = null;
        }
    }

    private void locate() {
        if (candidates.isFull()) {
            return;
        }
        for (BlockPos pos : search.step(ScanTuning.SLICES_PER_TICK)) {
            candidates.offer(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    private ScriptStatus reject(NotATree notATree) {
        rejected++;
        candidates.markKnown(notATree.logs());
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus startChop(Tree found) {
        tree = found;
        chopping = true;
        chop.begin(found);
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus advance(TreeChop.Outcome outcome) {
        return switch (outcome.state()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case DONE -> finishTree(outcome);
            case PROTECTED -> protectedTree();
            case UNREACHABLE -> unreachableTree();
            case FAILED -> fail(outcome.reason());
        };
    }

    private ScriptStatus finishTree(TreeChop.Outcome outcome) {
        releaseReserved();
        chopped++;
        standingLogs += outcome.standingLogs();
        plantedSaplings += outcome.plantedSaplings();
        skippedCells += outcome.skippedCells();
        lackedSaplings |= outcome.lackedSaplings();
        consecutiveUnreachable = 0;
        candidates.markKnown(tree.logs());
        tree = null;
        chopping = false;
        if (chopped >= params.count()) {
            LOGGER.info("Дух {} завершил chop_tree: {}", spirit.getUUID(), summary());
            return ScriptStatus.SUCCEEDED;
        }
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus protectedTree() {
        releaseReserved();
        protectedLogs++;
        candidates.markKnown(tree.logs());
        tree = null;
        chopping = false;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus unreachableTree() {
        releaseReserved();
        unreachable++;
        consecutiveUnreachable++;
        candidates.markKnown(tree.logs());
        tree = null;
        chopping = false;
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
        return fail("срублено деревьев " + chopped + " из " + params.count() + ": деревья породы " + speciesText()
                + " в радиусе " + params.radius() + " закончились (отвергнуто не-деревьев: " + rejected
                + ", недоступных: " + unreachable + ", пропущено защищённых: " + protectedLogs + ")");
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
