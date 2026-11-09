package com.theminespirit.neoforge;

import com.theminespirit.core.board.MineDescentBottom;
import com.theminespirit.core.board.MineTunnelRule;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.MineVeinParams;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class MineVeinScript implements ScriptInstance {
    private final HarvestSession session;

    MineVeinScript(SpiritEntity spirit, MineVeinParams params) {
        this.session =
                HarvestSession.mineVein(spirit, params.ore(), params.count(), params.radius(), tunnelAllowed(spirit));
    }

    private static boolean tunnelAllowed(SpiritEntity spirit) {
        if (!PickaxeCheck.has(spirit)) {
            return false;
        }
        if (!(spirit.level() instanceof ServerLevel level)) {
            return true;
        }
        Optional<StageSite> site = SettlementPlotStore.liveSite(level);
        if (site.isEmpty()) {
            return true;
        }
        StageSite stage = site.orElseThrow();
        BlockPos feet = spirit.blockPosition();
        BlockCoord bottom = MineDescentBottom.standOf(stage.mineEntrance()).orElse(null);
        if (bottom == null) {
            return false;
        }
        return MineTunnelRule.allowed(
                new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), stage.minePlot(), stage.mineEntrance(), bottom);
    }

    @Override
    public ScriptStatus tick() {
        return switch (session.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case SUCCEEDED -> ScriptStatus.SUCCEEDED;
            case FAILED -> ScriptStatus.FAILED;
        };
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(session.progress());
    }

    @Override
    public void cancel() {
        session.cancel();
    }

    @Override
    public void suspend() {
        session.suspend();
    }

    @Override
    public String failureReason() {
        return session.failureReason();
    }

    @Override
    public Map<String, Object> saveState() {
        return session.saveState();
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        session.restoreState(state);
    }
}
