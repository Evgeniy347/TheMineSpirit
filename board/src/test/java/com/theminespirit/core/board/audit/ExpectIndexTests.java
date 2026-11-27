package com.theminespirit.core.board.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExpectIndexTests {
    private static final int LEVEL = 63;

    private static final TownStreets STREETS = new TownStreets(
            ColumnMask.rect(0, 0, 5, 1),
            ColumnMask.rect(0, 3, 2, 3),
            List.of(
                    new TownStreets.Street("s-1", "main", ColumnMask.rect(0, 0, 5, 1)),
                    new TownStreets.Street("s-2", "side", ColumnMask.rect(0, 3, 2, 3))));

    private static TownPlan plan(List<Placement> placements) {
        return new TownPlan(
                new BlockCoord(0, LEVEL, 0),
                ColumnMask.rect(0, 0, 5, 3),
                STREETS,
                placements,
                new FenceLine(List.of(), List.of()),
                List.of());
    }

    private static ExpectIndex.Index indexOf(TownPlan plan, Map<String, Schematic> schemes, Set<String> mines) {
        return ExpectIndex.of(plan, 1, LEVEL, mines, (planId, levelY) -> Optional.ofNullable(schemes.get(planId)));
    }

    @Test
    void expectIndex_PlacementOnStreet_BuildingWinsAndCollisionIsCounted() {
        TownPlan town = plan(List.of(new Placement("hut", "Хижина", 1, 2, 0, 2, 2, Placement.Facing.SOUTH)));
        ExpectIndex.Index index = indexOf(
                town,
                Map.of("hut", new Schematic(2, 1, 2, List.of(new Schematic.Entry(0, 0, 0, "minecraft:cobblestone")))),
                Set.of());

        assertEquals(
                SourceRef.building("hut"),
                index.at(new BlockCoord(2, LEVEL, 0)).orElseThrow().source(),
                "Клетка схемы важнее плитки улицы: иначе дух сломает пол хижины ради коридора дороги, и "
                        + "постройка никогда не закроется. При смене приоритета источников перепроверь порядок "
                        + "продюсеров в ExpectIndex.of.");
        assertTrue(
                index.collisions() > 0,
                "Пересечение улицы и участка видно числом: без счётчика И-1 («клетка ровно в одной группе») "
                        + "ломается молча, и две группы начнут спорить за одну клетку. При смене геометрии "
                        + "плана перепроверь TownMapCheck и overlaps.");
    }

    @Test
    void expectIndex_FreeColumn_ExpectsGroundBelowAndThreeAirCells() {
        ExpectIndex.Index index = indexOf(plan(List.of()), Map.of(), Set.of());

        assertEquals(
                Wanted.ground(LEVEL - 1),
                index.at(new BlockCoord(4, LEVEL - 1, 3)).orElseThrow().wanted(),
                "Столб вне построек и улиц обязан иметь грунт на слое уровня−1: иначе яма 20.11 уйдёт в "
                        + "расчистку как «нет земли», и дух будет сыпать грунт на готовую площадку. "
                        + "При смене слоя перепроверь PlotCells.expect и канон уровня из SettlementPlot.anchor().");
        assertEquals(
                4,
                List.of(LEVEL - 1, LEVEL, LEVEL + 1, LEVEL + 2).stream()
                        .filter(y -> index.at(new BlockCoord(4, y, 3)).isPresent())
                        .count(),
                "Окно колонки = грунт + три свободных блока (PlotCells.FREE_HEIGHT): без запасного уровня "
                        + "дух не сможет поставить ступеньку на дороге, и застревания 14.11 вернутся. "
                        + "При смене высоты перепроверь StreetEnvelopes и FREE_HEIGHT в PlotCells.");
        assertTrue(
                index.collisions() == 0,
                "Земля не спорит с землёй: коллизии в пустом плане означают, что продюсеры пишут одну клетку "
                        + "дважды, иначе группа получит дубликаты предметов и дух сломает её дважды. При смене "
                        + "обхода контура перепроверь GroundEnvelopes.lotColumns.");
    }

    @Test
    void expectIndex_SchemeBlockAndMineAndReplaceable_MapToWantedKinds() {
        TownPlan town = plan(List.of(
                new Placement("hut", "Хижина", 1, 0, 2, 1, 1, Placement.Facing.SOUTH),
                new Placement("mine_1", "Шахта", 1, 5, 3, 1, 1, Placement.Facing.NORTH)));
        ExpectIndex.Index index = indexOf(
                town,
                Map.of(
                        "hut",
                        new Schematic(1, 1, 1, List.of(new Schematic.Entry(0, 0, 0, "minecraft:oak_stairs[half=top]"))),
                        "mine_1",
                        new Schematic(1, 1, 1, List.of(new Schematic.Entry(0, 0, 0, "minecraft:air")))),
                Set.of("mine_1"));

        assertEquals(
                Wanted.of("minecraft:oak_stairs[half=top]"),
                index.at(new BlockCoord(0, LEVEL, 2)).orElseThrow().wanted(),
                "Схема несёт и block id, и значущий state: без свойств аудит не отличит `half=top` от "
                        + "`half=bottom`, иначе ступени чинятся поломкой вместо правки состояния. При смене "
                        + "разбора схемы перепроверь Schematic.PlacedEntry.block и Wanted.of.");
        assertEquals(
                SourceRef.mine("mine_1"),
                index.at(new BlockCoord(5, LEVEL, 3)).orElseThrow().source(),
                "Ствол шахты помнит источник mine: иначе спуск попадёт в группу постройки, и ремонт шахты "
                        + "перемешается с ремонтом склада. При смене классификации перепроверь mineIds в "
                        + "ExpectIndex.of и карты StageSite.");
    }

    @Test
    void expectIndex_FuturePlotAndMissingScheme_GroundCoversAndGapReported() {
        TownPlan town = plan(List.of(
                new Placement("field_1", "Грядка", 2, 4, 2, 1, 1, Placement.Facing.SOUTH),
                new Placement("chest_1", "Сундук", 1, 3, 2, 1, 1, Placement.Facing.SOUTH)));
        ExpectIndex.Index index = indexOf(town, Map.of(), Set.of());

        assertEquals(
                1,
                index.missingSchemes(),
                "Отсутствующая схема текущего этапа — пропуск, а не тишина: иначе участок «починен», хотя "
                        + "клетки никогда не проверялись, и стройка встанет без задачи. При смене каталога "
                        + "этапов перепроверь StageCatalog.scheme и StageCatalog.entry.");
        assertEquals(
                SourceRef.GROUND,
                index.at(new BlockCoord(4, LEVEL - 1, 2)).orElseThrow().source(),
                "Участок будущего этапа остаётся землёй: иначе расчистка грядки пропадёт из плана, и дух не "
                        + "подготовит поле к строительству. При смене этапа перепроверь TownPlan.upToStage.");
        assertEquals(
                SourceRef.GROUND,
                index.at(new BlockCoord(3, LEVEL - 1, 2)).orElseThrow().source(),
                "Без схемы клетка не повисает: иначе сундук этапа 1 выпадет из всех групп, и провал в крыше "
                        + "останется невидимым для аудита. При смене обработчика пропуска перепроверь "
                        + "BuildingEnvelopes.collect.");
    }
}
