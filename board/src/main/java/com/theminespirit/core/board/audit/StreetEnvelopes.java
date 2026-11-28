package com.theminespirit.core.board.audit;

import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SchematicGround;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.StreetLayout;
import com.theminespirit.core.town.TownMapRules;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownRules;
import com.theminespirit.core.town.TownStreets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class StreetEnvelopes {
    private StreetEnvelopes() {}

    static int collect(TownPlan plan, int stage, int levelY, List<Expectation> out, ExpectIndex.Schemes schemes) {
        Objects.requireNonNull(plan, "План не может быть null: улицы берутся из него");
        Objects.requireNonNull(out, "Список ожиданий не может быть null: в него пишутся клетки");
        Objects.requireNonNull(schemes, "Источник схем не может быть null: без плитки улицы неизвестен уровень");
        Optional<Integer> level = StageCatalog.streetLevel(stage);
        if (level.isEmpty()) {
            return 1;
        }
        TownStreets streets = plan.streets();
        TownRules.Streets widths = TownRules.current().streets();
        return lay(
                        streets.main(),
                        StreetLayout.tileId(widths.main(), "_straight"),
                        level.orElseThrow(),
                        TownMapRules.MAIN,
                        streets,
                        levelY,
                        out,
                        schemes)
                + lay(
                        streets.side(),
                        StreetLayout.tileId(widths.side(), "_straight"),
                        level.orElseThrow(),
                        TownMapRules.SIDE,
                        streets,
                        levelY,
                        out,
                        schemes);
    }

    private static int lay(
            ColumnMask cells,
            String tileId,
            int level,
            String fallbackId,
            TownStreets streets,
            int levelY,
            List<Expectation> out,
            ExpectIndex.Schemes schemes) {
        Optional<Schematic> found = schemes.scheme(tileId, level).map(ExpectIndex.Scheme::schematic);
        if (found.isEmpty()) {
            return 1;
        }
        Schematic tile = found.orElseThrow();
        boolean grounded = SchematicGround.groundLayer(tile);
        int surface = grounded ? levelY - 1 : levelY;
        int free = grounded ? tile.sizeY() - 1 : tile.sizeY();
        Optional<String> walkway = walkway(tile);
        for (ColumnMask.Cell cell : cells.cells()) {
            SourceRef source = SourceRef.street(streets.idAt(cell.x(), cell.z()).orElse(fallbackId));
            out.add(new Expectation(
                    new BlockCoord(cell.x(), surface, cell.z()),
                    walkway.isPresent() ? Wanted.pavement(walkway.orElseThrow(), surface) : Wanted.ground(surface),
                    source,
                    Access.SPIRIT));
            for (int dy = 0; dy < free; dy++) {
                out.add(new Expectation(
                        new BlockCoord(cell.x(), levelY + dy, cell.z()), Wanted.air(), source, Access.SPIRIT));
            }
        }
        return 0;
    }

    private static Optional<String> walkway(Schematic tile) {
        return tile.blocks().stream()
                .filter(entry -> !entry.optional() && SchematicActions.isWalkway(entry.block()))
                .map(Schematic.Entry::block)
                .findFirst();
    }
}
