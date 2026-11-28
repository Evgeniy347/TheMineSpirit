package com.theminespirit.core.board.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TownAuditPavementTests {

    private static final ChunkCell CHUNK = new ChunkCell(0, 0);
    private static final BlockCoord SURFACE = new BlockCoord(3, 63, 0);

    private static DiffItem item(Wanted wanted, String standing) {
        AuditRegistry facts = new AuditRegistry(AuditBudgets.DEFAULT, 1, 1);
        facts.plan(1, 64);
        facts.retain(Set.of(CHUNK));
        facts.ensure(CHUNK);
        facts.fill(2048, cell -> cell.equals(SURFACE) ? standing : null, 1L);
        Expectation expectation = new Expectation(SURFACE, wanted, SourceRef.street("s-7"), Access.SPIRIT);
        Map<BlockCoord, Expectation> cells = new LinkedHashMap<>();
        cells.put(SURFACE, expectation);
        GroupRegistry groups = new GroupRegistry();
        groups.rebuild(new ExpectIndex.Index(cells, 0, 0), 1L);
        groups.sync(facts, 1L, 10);
        return groups.group("group:street:s-7@0,0").items().get(0);
    }

    @Test
    void groupRegistry_PavedStreetOnBareEarth_AsksToTramplePath() {
        DiffItem work = item(Wanted.pavement("minecraft:dirt_path", 63), "minecraft:dirt");

        assertEquals(
                "minecraft:dirt_path",
                work.wanted().id(),
                "Голая земля на мощёной улице чинится тропой: иначе граф выдаст задачу «насыпь земли» там,"
                        + " где грунта достаточно, и духи будут возить землю вместо лопаты. При смене правила"
                        + " перепроверь GroupRegistry.build и BlockEquivalence.Rules.paved.");
        assertEquals(
                DiffItem.Action.PLACE,
                work.action(),
                "Мощение — установка блока слоем улицы: иначе дефект уйдёт как «сломать», и улица останется"
                        + " без покрытия. При смене действия перепроверь DiffItem.place.");
    }

    @Test
    void groupRegistry_PavedStreetOverHole_AsksEarthFirst() {
        DiffItem work = item(Wanted.pavement("minecraft:dirt_path", 63), "minecraft:air");

        assertEquals(
                Wanted.ground(63),
                work.wanted(),
                "Над ямой тропе не на чём вырасти: иначе без грунта дух уронит лопату в пустоту, и группа"
                        + " улиц зависнет в невыполнимой задаче. При смене правила перепроверь"
                        + " GroupRegistry.build и BlockEquivalence.ground.");
    }
}
