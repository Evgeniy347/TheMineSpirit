package com.theminespirit.core.board.audit;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask.Cell;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.StageSchemes;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.WallFragment;
import com.theminespirit.core.town.WallLayout;
import com.theminespirit.core.town.WallPiece;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class FenceEnvelopes {
    private static final String LEGACY_SOURCE = "loop";

    private FenceEnvelopes() {}

    static int collect(TownPlan plan, int levelY, List<Expectation> out, ExpectIndex.Schemes schemes) {
        Objects.requireNonNull(plan, "План не может быть null: ограду брать негде");
        Objects.requireNonNull(out, "Список ожиданий не может быть null: в него пишутся клетки");
        Objects.requireNonNull(schemes, "Источник схем не может быть null: без схемы стена не известна");
        FenceLine fence = plan.fence();
        if (fence.posts().isEmpty()) {
            return 0;
        }
        Map<String, ExpectIndex.Scheme> available = new LinkedHashMap<>();
        int missing = 0;
        for (String id : StageSchemes.wall()) {
            Optional<ExpectIndex.Scheme> found = schemes.scheme(id, 3);
            if (found.isEmpty()) {
                missing++;
            } else {
                available.put(id, found.orElseThrow());
            }
        }
        if (available.isEmpty()) {
            legacy(fence, levelY, out);
            return missing;
        }
        List<WallFragment> fragments = available.entrySet().stream()
                .map(entry -> WallFragment.of(entry.getKey(), entry.getValue().schematic()))
                .toList();
        List<WallPiece> pieces;
        try {
            pieces = new WallLayout(fragments).open(fence.posts(), fence.gates());
        } catch (IllegalStateException unfolded) {
            legacy(fence, levelY, out);
            return missing + 1;
        }
        if (pieces.isEmpty()) {
            legacy(fence, levelY, out);
            return missing + 1;
        }
        for (WallPiece piece : pieces) {
            ExpectIndex.Scheme scheme =
                    Objects.requireNonNull(available.get(piece.scheme()), "Фрагмент стены не взят из загруженных схем");
            String subject = piece.scheme() + "#" + piece.from();
            BlockCoord origin = new BlockCoord(piece.x(), levelY + 1, piece.z());
            Set<BlockCoord> supports = new LinkedHashSet<>();
            for (Schematic.PlacedEntry entry : scheme.schematic().entries(origin, piece.rotation())) {
                if (entry.optional()) {
                    continue;
                }
                out.add(new Expectation(entry.pos(), wanted(entry.block()), SourceRef.wall(subject), Access.SPIRIT));
                if (entry.pos().y() == levelY + 1) {
                    supports.add(
                            new BlockCoord(entry.pos().x(), levelY, entry.pos().z()));
                }
            }
            for (BlockCoord support : supports) {
                out.add(new Expectation(support, Wanted.ground(levelY), SourceRef.wall(subject), Access.SPIRIT));
            }
        }
        return missing;
    }

    private static void legacy(FenceLine fence, int levelY, List<Expectation> out) {
        Set<Integer> gates = Set.copyOf(fence.gates());
        for (int index = 0; index < fence.posts().size(); index++) {
            Cell post = fence.posts().get(index);
            String block = gates.contains(index) ? "minecraft:oak_fence_gate" : "minecraft:oak_fence";
            out.add(new Expectation(
                    new BlockCoord(post.x(), levelY - 1, post.z()),
                    Wanted.ground(levelY),
                    SourceRef.wall(LEGACY_SOURCE),
                    Access.SPIRIT));
            out.add(new Expectation(
                    new BlockCoord(post.x(), levelY, post.z()),
                    Wanted.block(block),
                    SourceRef.wall(LEGACY_SOURCE),
                    Access.SPIRIT));
        }
    }

    private static Wanted wanted(String block) {
        return BlockEquivalence.check(Wanted.air(), block) == BlockEquivalence.Verdict.DONE
                ? Wanted.air()
                : Wanted.of(block);
    }
}
