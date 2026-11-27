package com.theminespirit.core.board.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TownAuditGroundGroupsTests {
    @Test
    void groupRegistry_GroundColumn_SplitsPitAndStray() {
        BlockCoord ground = new BlockCoord(0, 63, 0);
        BlockCoord air = new BlockCoord(0, 64, 0);
        Map<BlockCoord, Expectation> cells = new LinkedHashMap<>();
        cells.put(ground, new Expectation(ground, Wanted.ground(63), SourceRef.GROUND, Access.SPIRIT));
        cells.put(air, new Expectation(air, Wanted.air(), SourceRef.GROUND, Access.SPIRIT));
        AuditRegistry facts = new AuditRegistry(AuditBudgets.DEFAULT, 1, 1);
        facts.plan(1, 64);
        facts.retain(Set.of(new ChunkCell(0, 0)));
        facts.ensure(new ChunkCell(0, 0));
        facts.fill(2048, cell -> cell.equals(air) ? "minecraft:oak_fence" : "minecraft:air", 1L);
        GroupRegistry groups = new GroupRegistry();
        groups.rebuild(new ExpectIndex.Index(cells, 0, 0), 1L);

        groups.sync(facts, 1L, 10);

        assertEquals(
                "group:ground:pit@0,0",
                groups.owner(ground),
                "Слой земли становится отдельной группой"
                        + " pit: иначе яма потеряет приоритет перед декоративной расчисткой. При смене дробления перепроверь ground.");
        assertEquals(
                "group:ground:stray@0,0",
                groups.owner(air),
                "Препятствие в коридоре становится"
                        + " stray: иначе граф не отличит его от общей земли. При смене дробления перепроверь ground.");
        assertEquals(
                DiffItem.Action.PLACE,
                groups.group(groups.owner(ground)).items().get(0).action(),
                "Яма требует установки грунта: иначе группа pit останется пустой, а дух будет падать"
                        + " в яму под ногами. При смене правил перепроверь BlockEquivalence.GROUND.");
        assertEquals(
                DiffItem.Action.BREAK,
                groups.group(groups.owner(air)).items().get(0).action(),
                "Лишний блок в коридоре требует слома: иначе stray не освободит проход, и дух застрянет"
                        + " на заборе. При смене правил перепроверь Wanted.AIR.");
        assertEquals(
                cells.size(),
                groups.groups().stream().mapToInt(Group::total).sum(),
                "Сумма подгрупп равна числу клеток: иначе pit и stray потеряют часть колонки. При смене"
                        + " дробления перепроверь owners и Group.total.");
    }
}
