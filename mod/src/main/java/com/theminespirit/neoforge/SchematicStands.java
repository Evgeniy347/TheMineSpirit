package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildPit;
import com.theminespirit.core.building.BuildStandLevel;
import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

final class SchematicStands {
    private SchematicStands() {}

    static ScriptStatus moveTowards(
            BuildSchematicScript host,
            List<BlockCoord> cells,
            ReachPlanner.World base,
            boolean outsideOnly,
            BiPredicate<BlockCoord, BlockCoord> reaches) {
        if (BuildPit.needsEscape(host.feet().y(), host.site.origin().y(), host.pit::trapped)) {
            host.pit.escape(host.feet());
            return ScriptStatus.RUNNING;
        }
        ReachPlanner.World stands = BuildStandLevel.atOrAbove(base, host.site.standBase());
        Set<BlockCoord> occupied = SchematicWalkways.free(host.site.primaries(), host.site.schemeCells());
        Optional<Set<BlockCoord>> reachable = host.site.walkable(host.feet(), BuildSchematicScript.WALK_MARGIN);
        boolean walkKnown =
                host.site.networkBox(BuildSchematicScript.WALK_MARGIN).contains(host.feet()) && reachable.isPresent();
        ReachPlanner.World world = walkKnown ? BuildStandPath.walkableOnly(stands, reachable.orElseThrow()) : stands;
        Set<BlockCoord> excluded = new HashSet<>(host.excludedStands);
        Optional<BlockCoord> stand = choose(host, cells, world, excluded, occupied, outsideOnly, reaches);
        if (stand.isEmpty()) {
            Set<BlockCoord> blockers = SchematicWalkways.free(host.site.primaries(), host.site.standBlockers());
            stand = choose(host, cells, world, excluded, blockers, outsideOnly, reaches);
        }
        if (stand.isEmpty() && !host.standsRetried && !host.excludedStands.isEmpty()) {
            host.standsRetried = true;
            excluded.clear();
            stand = choose(host, cells, world, excluded, occupied, outsideOnly, reaches);
        }
        if (stand.isEmpty()) {
            for (BlockCoord cell : cells) {
                host.blocked.putIfAbsent(
                        cell,
                        "ни с одной стоянки не дотянуться" + (outsideOnly ? " снаружи постройки" : "")
                                + (walkKnown ? " пешком от " + host.feet() : "")
                                + (host.lastRefusal.isBlank() ? "" : "; " + host.lastRefusal));
            }
            return ScriptStatus.RUNNING;
        }
        return host.placement.beginApproach(stand.orElseThrow());
    }

    private static Optional<BlockCoord> choose(
            BuildSchematicScript host,
            List<BlockCoord> cells,
            ReachPlanner.World world,
            Set<BlockCoord> excluded,
            Set<BlockCoord> blockers,
            boolean outsideOnly,
            BiPredicate<BlockCoord, BlockCoord> reaches) {
        Optional<BlockCoord> stand = BuildStandPath.choose(cells, world, excluded, blockers, outsideOnly, host.feet());
        while (stand.isPresent()) {
            BlockCoord candidate = stand.orElseThrow();
            if (cells.stream().anyMatch(cell -> reaches.test(candidate, cell))) {
                return stand;
            }
            excluded.add(candidate);
            stand = BuildStandPath.choose(cells, world, excluded, blockers, outsideOnly, host.feet());
        }
        return Optional.empty();
    }
}
