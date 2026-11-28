package com.theminespirit.core.board.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExpectIndexStreetTests {
    private static final int LEVEL = 63;

    private static TownPlan plan() {
        return new TownPlan(
                new BlockCoord(0, LEVEL, 0),
                ColumnMask.rect(0, 0, 5, 3),
                new TownStreets(
                        ColumnMask.rect(0, 0, 5, 1),
                        ColumnMask.rect(0, 3, 2, 3),
                        List.of(
                                new TownStreets.Street("s-1", "main", ColumnMask.rect(0, 0, 5, 1)),
                                new TownStreets.Street("s-2", "side", ColumnMask.rect(0, 3, 2, 3)))),
                List.of(),
                new FenceLine(List.of(), List.of()),
                List.of());
    }

    private static ExpectIndex.Index indexOf(int stage, Schematic tile) {
        return ExpectIndex.of(
                plan(),
                stage,
                LEVEL,
                Set.of(),
                (planId, level) -> planId.startsWith("street")
                        ? Optional.of(new ExpectIndex.Scheme(tile, new BlockCoord(0, 0, 0)))
                        : Optional.empty());
    }

    private static Schematic tile(int sizeY, String surface) {
        return new Schematic(5, sizeY, 5, List.of(new Schematic.Entry(0, 0, 0, surface)));
    }

    @Test
    void expectIndex_StreetAtPavedStage_PavementIsNotBareEarth() {
        Wanted surface = indexOf(2, tile(4, "minecraft:dirt_path"))
                .at(new BlockCoord(0, LEVEL - 1, 0))
                .orElseThrow()
                .wanted();

        assertEquals(
                "minecraft:dirt_path",
                surface.id(),
                "Плитка этапа 2 несёт тропу, и аудит ждёт именно её: иначе голая земля и мощение"
                        + " неразличимы, непоставленная плитка выглядит готовой, и задача на починку улицы не"
                        + " родится никогда. При смене слоя перепроверь StreetEnvelopes и"
                        + " SchematicGround.walkwayPlaced.");
        assertEquals(
                BlockEquivalence.Verdict.DIFFER,
                BlockEquivalence.check(surface, "minecraft:dirt"),
                "Голая земля на мощёной улице — дефект: иначе этап 2 закрывается без мощения, и духи не"
                        + " получают задания починить улицу. При смене правила перепроверь"
                        + " BlockEquivalence.Rules.paved.");
        assertEquals(
                BlockEquivalence.Verdict.DONE,
                BlockEquivalence.check(surface, "minecraft:gravel"),
                "Гравий с краёв плитки — тоже покрытие: иначе аудит разберёт готовую улицу как дефектную, и"
                        + " группа улиц будет висеть в задачах бесконечно. При смене материалов перепроверь"
                        + " street5_straight.txt и street7_straight.txt.");
    }

    @Test
    void expectIndex_StreetAtDirtStage_BareEarthIsEnough() {
        Wanted surface = indexOf(1, tile(4, "minecraft:dirt"))
                .at(new BlockCoord(0, LEVEL - 1, 0))
                .orElseThrow()
                .wanted();

        assertEquals(
                BlockEquivalence.Verdict.DONE,
                BlockEquivalence.check(surface, "minecraft:grass_block"),
                "Этап 1 улицы — земля, и дёрн под ногами годится: иначе свежая улица сразу станет дефектом,"
                        + " и граф зальёт поселение заданиями мощения раньше этапа 2. При смене уровня перепроверь"
                        + " StageCatalog.streetLevel и street5_dirt.txt.");
    }

    @Test
    void expectIndex_StreetFreeHeight_ComesFromTileScheme() {
        ExpectIndex.Index tall = indexOf(1, tile(4, "minecraft:dirt"));
        ExpectIndex.Index shortTile = indexOf(1, tile(3, "minecraft:dirt"));

        assertTrue(
                tall.at(new BlockCoord(0, LEVEL + 1, 0)).isPresent(),
                "Свободные уровни улицы берутся из высоты схемы: без второго запаса над тропой дух не"
                        + " сможет пройти и поставить ступеньку, и застревания вернутся. При смене высоты"
                        + " перепроверь size в схемах street5_* и street7_*.");
        assertTrue(
                shortTile.at(new BlockCoord(0, LEVEL + 2, 0)).isEmpty(),
                "Коридор плитки высотой в три уровня кончается на sizeY−1, а не на PlotCells.FREE_HEIGHT:"
                        + " иначе аудит требовал бы воздух, которого схема не обещала, и улица никогда не стала бы"
                        + " «готовой». При смене коридора перепроверь StreetEnvelopes.lay.");
    }
}
