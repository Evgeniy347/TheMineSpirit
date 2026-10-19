package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class PillarDownScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final List<BlockCoord> own = new ArrayList<>();
    private PillarDismantler dismantler;
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
                dismantler = new PillarDismantler(spirit, own);
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

    private void scan() {
        if (!(spirit.level() instanceof ServerLevel server)) {
            return;
        }
        Level level = spirit.level();
        PlacedBlocksData data = PlacedBlocksData.get(server);
        BlockPos feet = spirit.blockPosition();
        int floor = Math.max(level.getMinBuildHeight() + 1, feet.getY() - CatalogLimits.COUNT_MAX);
        for (int y = feet.getY() - 1; y >= floor; y--) {
            BlockPos pos = new BlockPos(feet.getX(), y, feet.getZ());
            if (data.isSpiritMarked(pos) && !level.getBlockState(pos).isAir()) {
                own.add(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        return outcome;
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
