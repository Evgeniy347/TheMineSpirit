package com.theminespirit.core.board.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SchematicGround;
import com.theminespirit.core.town.StreetLayout;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StreetTileDataTests {
    private static final List<String> SHAPES = List.of("_straight", "_turn", "_t", "_cross");
    private static final List<Integer> WIDTHS = List.of(7, 5);
    private static final int FREE_LEVELS = 3;

    record Tile(String id, Schematic schematic) {}

    private static Tile tile(int width, String shape, int level) {
        String planId = StreetLayout.tileId(width, shape);
        String file = StageCatalog.scheme(planId, level)
                .orElseThrow(() -> new AssertionError("buildings.json: у плитки '" + planId + "' нет уровня " + level));
        Schematic schematic = PresetLibrary.current()
                .byId(file)
                .orElseThrow(() -> new AssertionError("presets.json: нет схемы '" + file + "'"))
                .schematic();
        return new Tile(file, schematic);
    }

    private static List<String> surface(Tile tile) {
        return tile.schematic().blocks().stream()
                .filter(entry -> !entry.optional() && entry.dy() == 0)
                .map(Schematic.Entry::block)
                .distinct()
                .toList();
    }

    @Test
    void streetTile_PavedLevel_AuditAcceptsEveryItsSurfaceBlock() {
        for (int width : WIDTHS) {
            for (String shape : SHAPES) {
                Tile tile = tile(width, shape, 2);
                Optional<String> walkway = surface(tile).stream()
                        .filter(SchematicActions::isWalkway)
                        .findFirst();
                assertTrue(
                        SchematicGround.groundLayer(tile.schematic()) && walkway.isPresent(),
                        tile.id() + ": этап 2 обязан ложиться слоем земли с тропой — иначе аудит улицы"
                                + " не отличит мощение от голой земли, и задача на починку улицы не родится."
                                + " При смене плитки перепроверь StreetEnvelopes и street" + width + shape + ".txt");
                for (String block : surface(tile)) {
                    assertEquals(
                            BlockEquivalence.Verdict.DONE,
                            BlockEquivalence.check(Wanted.pavement(walkway.orElseThrow(), 63), block),
                            tile.id() + ": блок " + block + " слоя плитки не входит в класс мощения аудита — иначе"
                                    + " готовая улица будет вечно числиться дефектной, и граф зальёт её задачами."
                                    + " При смене материалов перепроверь BlockEquivalence.Rules.paved и"
                                    + " street" + width + shape + ".txt");
                }
            }
        }
    }

    @Test
    void streetTile_DirtLevel_AuditStillTreatsItAsBareGround() {
        for (int width : WIDTHS) {
            Tile tile = tile(width, "_straight", 1);
            for (String block : surface(tile)) {
                assertEquals(
                        BlockEquivalence.Verdict.DONE,
                        BlockEquivalence.check(Wanted.ground(63), block),
                        tile.id() + ": грунт этапа 1 (" + block + ") не проходит как земля — иначе свежая улица"
                                + " сразу станет дефектом, и мощение потребуют раньше второго этапа. При смене"
                                + " класса земли перепроверь BlockEquivalence.Rules.ground и street" + width
                                + "_dirt.txt");
                assertEquals(
                        BlockEquivalence.Verdict.DIFFER,
                        BlockEquivalence.check(Wanted.pavement("minecraft:dirt_path", 63), block),
                        tile.id() + ": материал голой улицы (" + block + ") засчитывается мощением — иначе"
                                + " аудит этапа 2 снова не отличит тропу от земли. При смене класса мощения"
                                + " перепроверь BlockEquivalence.Rules.paved.");
            }
        }
    }

    @Test
    void streetTile_EveryLevel_DeclaresFreeCorridorAboveGround() {
        for (int width : WIDTHS) {
            for (int level : List.of(1, 2)) {
                Tile tile = tile(width, "_straight", level);
                assertTrue(
                        SchematicGround.groundLayer(tile.schematic()),
                        tile.id() + ": плитка улицы не является слоем земли — иначе аудит сдвинет коридор на уровень"
                                + " вверх, и улица будет требовать воздух там, где стоит покрытие. При смене"
                                + " legend перепроверь SchematicGround.groundLayer и " + tile.id() + ".txt");
                assertEquals(
                        FREE_LEVELS,
                        tile.schematic().sizeY() - 1,
                        tile.id() + ": над слоем земли объявлено "
                                + (tile.schematic().sizeY() - 1)
                                + " свободных уровней вместо трёх — StreetEnvelopes берёт высоту коридора из"
                                + " схемы, и дух не сможет пройти или поставить ступеньку. При смене высоты"
                                + " перепроверь size в " + tile.id() + ".txt и PlotCells.FREE_HEIGHT.");
            }
        }
    }
}
