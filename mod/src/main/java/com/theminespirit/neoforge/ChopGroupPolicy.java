package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.forestry.ChopMode;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TreeClearance;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

final class ChopGroupPolicy {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Entity spirit;
    private final ChopSearch search;
    private int plannedRejected;

    ChopGroupPolicy(Entity spirit, ChopSearch search) {
        this.spirit = spirit;
        this.search = search;
    }

    ChopMode mode() {
        return liveSite().isPresent() ? ChopMode.SETTLEMENT : ChopMode.WILD;
    }

    boolean plannedGroup(Tree found) {
        Optional<StageSite> site = liveSite();
        return found.logs().stream().anyMatch(log -> TreeClearance.planned(site, log));
    }

    void skipPlanned(Tree found) {
        plannedRejected++;
        if (plannedRejected <= 5) {
            LOGGER.info(
                    "[chop] дух {}: группа брёвен у {} задевает план поселения — не рублю, ищу дальше",
                    spirit.getName().getString(),
                    found.base());
        }
        search.markKnown(found.logs());
    }

    int plannedRejected() {
        return plannedRejected;
    }

    private Optional<StageSite> liveSite() {
        if (!(spirit.level() instanceof ServerLevel server)) {
            return Optional.empty();
        }
        return SettlementPlotStore.liveSite(server);
    }
}
