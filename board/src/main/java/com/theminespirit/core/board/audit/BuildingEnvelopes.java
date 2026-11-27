package com.theminespirit.core.board.audit;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.TownPlan;
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
            Optional<Schematic> scheme = schemes.scheme(place.id(), levelY);
            if (scheme.isEmpty()) {
                missing++;
                continue;
            }
            for (Schematic.PlacedEntry entry :
                    scheme.get().entries(new BlockCoord(place.x(), levelY, place.z()), BuildRotation.NONE)) {
                if (entry.optional()) {
                    continue;
                }
                out.add(new Expectation(
                        entry.pos(), wanted(entry.block()), source(place.id(), mineIds), Access.SPIRIT));
            }
        }
        return missing;
    }

    static SourceRef source(String planId, Set<String> mineIds) {
        return mineIds.contains(planId) ? SourceRef.mine(planId) : SourceRef.building(planId);
    }

    private static Wanted wanted(String block) {
        return BlockEquivalence.check(Wanted.air(), block) == BlockEquivalence.Verdict.DONE
                ? Wanted.air()
                : Wanted.of(block);
    }
}
