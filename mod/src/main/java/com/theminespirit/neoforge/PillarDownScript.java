package com.theminespirit.neoforge;

import com.theminespirit.core.board.lease.LeaseCell;
import com.theminespirit.core.board.lease.LeaseKind;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class PillarDownScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final List<BlockCoord> own = new ArrayList<>();
    private PillarDismantler dismantler;
    private @Nullable LeaseRun lease;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;

    public PillarDownScript(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время разбора столба");
        }
        if (dismantler == null) {
            scan();
            if (!own.isEmpty()) {
                dismantler = new PillarDismantler(spirit, own, lease);
            }
            return dismantler == null ? fail("под духом нет своего столба: разбирать нечего") : ScriptStatus.RUNNING;
        }
        return switch (dismantler.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case DONE -> {
                outcome = ScriptStatus.SUCCEEDED;
                yield outcome;
            }
            case FAILED -> fail(dismantler.failureReason());
        };
    }

    record OwnPillar(List<BlockCoord> cells, @Nullable LeaseRun lease) {}

    private void scan() {
        ownPillarBelow(spirit)
                .ifPresentOrElse(
                        found -> {
                            own.addAll(found.cells());
                            lease = found.lease();
                        },
                        () -> scanLeaseCells());
    }

    private void scanLeaseCells() {
        if (!(spirit.level() instanceof ServerLevel)) {
            return;
        }
        LeaseRun found = LeaseRun.ownedAnywhereAt(spirit, spirit.blockPosition().below());
        if (found == null) {
            return;
        }
        lease = found;
        List<LeaseCell> cells = found.cells();
        for (int index = cells.size() - 1; index >= 0; index--) {
            LeaseCell cell = cells.get(index);
            if (cell.kind() == LeaseKind.PLACED) {
                own.add(cell.pos());
            }
        }
    }

    static @Nullable PillarDismantler leftoverDismantler(SpiritEntity spirit) {
        return ownPillarBelow(spirit)
                .map(found -> new PillarDismantler(spirit, found.cells(), found.lease(), false))
                .orElse(null);
    }

    static Optional<OwnPillar> ownPillarBelow(SpiritEntity spirit) {
        if (!(spirit.level() instanceof ServerLevel)) {
            return Optional.empty();
        }
        Level level = spirit.level();
        BlockPos feet = spirit.blockPosition();
        int floor = Math.max(level.getMinBuildHeight() + 1, feet.getY() - CatalogLimits.COUNT_MAX);
        List<BlockCoord> cells = columnBelow(
                feet.getX(),
                feet.getY(),
                feet.getZ(),
                floor,
                cell -> ownCellAt(spirit, new BlockPos(cell.x(), cell.y(), cell.z())));
        if (cells.isEmpty()) {
            return Optional.empty();
        }
        BlockCoord top = cells.get(0);
        return Optional.of(
                new OwnPillar(cells, LeaseRun.ownedAnywhereAt(spirit, new BlockPos(top.x(), top.y(), top.z()))));
    }

    static List<BlockCoord> columnBelow(int x, int feetY, int z, int floor, Predicate<BlockCoord> own) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int y = feetY - 1; y >= floor; y--) {
            BlockCoord cell = new BlockCoord(x, y, z);
            if (own.test(cell)) {
                cells.add(cell);
            }
        }
        return cells;
    }

    public static boolean ownBelow(SpiritEntity spirit) {
        return ownCellAt(spirit, spirit.blockPosition().below());
    }

    static boolean ownCellAt(SpiritEntity spirit, BlockPos pos) {
        return LeaseRun.ownedAnywhereAt(spirit, pos) != null
                && !spirit.level().getBlockState(pos).isAir();
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        if (lease != null) {
            lease.orphan();
        }
        return outcome;
    }

    @Override
    public void cancel() {
        if (lease != null) {
            lease.orphan();
        }
    }

    @Override
    public Optional<Progress> progress() {
        int target = Math.max(own.size(), 1);
        int done = dismantler == null ? 0 : dismantler.returned();
        return Optional.of(new Progress(Math.min(done, target), target));
    }

    @Override
    public Optional<ScriptResult> result() {
        if (outcome != ScriptStatus.SUCCEEDED || dismantler == null) {
            return Optional.empty();
        }
        String text =
                "разобрано блоков: " + dismantler.returned() + ", возвращено в инвентарь: " + dismantler.returned();
        if (dismantler.dropped() > 0) {
            text += ", выпало из-за полного инвентаря: " + dismantler.dropped();
        }
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
