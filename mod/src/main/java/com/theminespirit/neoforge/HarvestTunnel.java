package com.theminespirit.neoforge;

import com.theminespirit.core.mining.MineTunnel;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

final class HarvestTunnel {
    private final SpiritEntity spirit;
    private final HarvestSpec spec;
    private final LevelSafetyView safety;
    private final HarvestCounters counters;
    private final Set<BlockCoord> tunnelTried = new HashSet<>();

    private VeinTunnelSession tunnel;
    private String tunnelRefusal;

    HarvestTunnel(SpiritEntity spirit, HarvestSpec spec, LevelSafetyView safety, HarvestCounters counters) {
        this.spirit = spirit;
        this.spec = spec;
        this.safety = safety;
        this.counters = counters;
    }

    String refusal() {
        return tunnelRefusal;
    }

    boolean begin(BlockCoord origin, List<BlockCoord> candidates) {
        if (tunnel != null) {
            return true;
        }
        if (!spec.allowTunnel()) {
            tunnelRefusal = "проход к руде запрещён: дух вне спуска и хода шахты";
            return false;
        }
        String refusal = tunnelRefusal;
        for (BlockCoord candidate : candidates) {
            if (!tunnelTried.add(candidate)) {
                continue;
            }
            MineTunnel.Plan plan = MineTunnel.plan(candidate, origin, safety, spec.radius(), this::protectedCell);
            tunnel = VeinTunnelSession.start(spirit, candidate, plan, safety);
            if (tunnel != null) {
                return true;
            }
            refusal = plan.refusal();
        }
        tunnelRefusal = refusal;
        return false;
    }

    HarvestSession.State tick() {
        counters.progress();
        VeinTunnelSession.Outcome progress = tunnel.tick();
        if (progress == VeinTunnelSession.Outcome.RUNNING) {
            return HarvestSession.State.TUNNEL;
        }
        if (progress == VeinTunnelSession.Outcome.FAILED) {
            tunnelRefusal = tunnel.failureReason();
        }
        tunnel = null;
        return HarvestSession.State.PICK;
    }

    void cancel() {
        if (tunnel != null) {
            tunnel.cancel();
        }
    }

    void suspend() {
        if (tunnel != null) {
            tunnel.suspend();
        }
    }

    private boolean protectedCell(BlockCoord cell) {
        return spirit.level() instanceof ServerLevel server
                && SettlementPlotStore.get(server)
                        .site()
                        .map(site -> PlotProtection.check(
                                        site,
                                        cell,
                                        PlotProtection.Action.DIG,
                                        PlotProtection.Ground.NONE,
                                        PlanBuilding.NONE)
                                instanceof PlotProtection.Refused)
                        .orElse(false);
    }
}
