package com.theminespirit.core.board.audit;

import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.TownPlan;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class BuildingEnvelopes {
    private BuildingEnvelopes() {}

    static int collect(
            TownPlan plan,
            int stage,
            int levelY,
            Set<String> mineIds,
            List<Expectation> out,
            ExpectIndex.Schemes schemes) {
        Objects.requireNonNull(plan, "План не может быть null: постройки берутся из него");
        Objects.requireNonNull(mineIds, "Список шахтных планов не может быть null: иначе ствол станет постройкой");
        Objects.requireNonNull(out, "Список ожиданий не может быть null: в него пишутся клетки");
        Objects.requireNonNull(schemes, "Источник схем не может быть null: без схемы клетка не известна");
        int missing = 0;
        for (Placement place : plan.upToStage(stage)) {
            Optional<ExpectIndex.Scheme> found = schemes.scheme(place.id(), place.stage());
            if (found.isEmpty()) {
                missing++;
                continue;
            }
            ExpectIndex.Scheme scheme = found.orElseThrow();
            BlockCoord origin = new BlockCoord(place.x(), levelY, place.z());
            if (mineIds.contains(place.id())) {
                mine(scheme, origin, place.id(), out);
            } else {
                for (Schematic.PlacedEntry entry : scheme.schematic().entries(origin, BuildRotation.NONE)) {
                    if (entry.optional()) {
                        continue;
                    }
                    out.add(new Expectation(
                            entry.pos(), wanted(entry.block()), SourceRef.building(place.id()), Access.SPIRIT));
                }
            }
        }
        return missing;
    }

    private static void mine(ExpectIndex.Scheme scheme, BlockCoord origin, String planId, List<Expectation> out) {
        MineLayout layout = MineLayout.place(
                scheme.schematic(), scheme.entry(), origin, BuildRotation.NONE, MineLayout.Extent.NONE);
        Set<BlockCoord> optional = new HashSet<>();
        layout.optional().forEach(entry -> optional.add(entry.pos()));
        for (BlockCoord cell : layout.floors()) {
            out.add(new Expectation(cell, Wanted.block(SchematicBlocks.FLOOR), SourceRef.mine(planId), Access.SPIRIT));
        }
        for (BlockCoord cell : layout.air()) {
            if (!optional.contains(cell)) {
                out.add(new Expectation(cell, Wanted.air(), SourceRef.mine(planId), Access.SPIRIT));
            }
        }
    }

    private static Wanted wanted(String block) {
        return BlockEquivalence.check(Wanted.air(), block) == BlockEquivalence.Verdict.DONE
                ? Wanted.air()
                : Wanted.of(block);
    }
}
