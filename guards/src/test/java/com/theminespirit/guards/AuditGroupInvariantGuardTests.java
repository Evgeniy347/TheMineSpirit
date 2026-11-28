package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.board.audit.Access;
import com.theminespirit.core.board.audit.AuditBudgets;
import com.theminespirit.core.board.audit.AuditRegistry;
import com.theminespirit.core.board.audit.ExpectIndex;
import com.theminespirit.core.board.audit.Expectation;
import com.theminespirit.core.board.audit.GroupRegistry;
import com.theminespirit.core.board.audit.SourceRef;
import com.theminespirit.core.board.audit.Wanted;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuditGroupInvariantGuardTests {
    @Test
    void auditGroups_EveryExpectation_HasExactlyOneOwner() {
        BlockCoord building = new BlockCoord(0, 64, 0);
        BlockCoord buildingEdge = new BlockCoord(16, 64, 0);
        BlockCoord street = new BlockCoord(1, 64, 0);
        Map<BlockCoord, Expectation> cells = new LinkedHashMap<>();
        cells.put(
                building,
                new Expectation(
                        building, Wanted.block("minecraft:stone"), SourceRef.building("storage_3"), Access.SPIRIT));
        cells.put(
                buildingEdge,
                new Expectation(
                        buildingEdge, Wanted.block("minecraft:stone"), SourceRef.building("storage_3"), Access.SPIRIT));
        cells.put(street, new Expectation(street, Wanted.air(), SourceRef.street("s-7"), Access.SPIRIT));
        ExpectIndex.Index index = new ExpectIndex.Index(cells, 0, 0);
        AuditRegistry facts = new AuditRegistry(AuditBudgets.DEFAULT, 1, 1);
        facts.plan(1, 64);
        facts.retain(Set.of(new ChunkCell(0, 0), new ChunkCell(1, 0)));
        facts.ensure(new ChunkCell(0, 0));
        facts.ensure(new ChunkCell(1, 0));
        facts.fill(4096, cell -> "minecraft:air", 1L);
        GroupRegistry groups = new GroupRegistry();
        groups.rebuild(index, 1L);
        groups.sync(facts, 1L, 10);

        assertEquals(
                index.size(),
                groups.groups().stream().mapToInt(group -> group.total()).sum(),
                "Сумма площадей групп равна числу ожиданий: иначе клетка потеряна или посчитана дважды, и"
                        + " инвалидация не сможет найти единственного владельца. При смене сборки перепроверь"
                        + " GroupRegistry.rebuild и GroupKeys.key.");
        assertEquals(
                "group:building:storage_3@0,0",
                groups.owner(building),
                "Клетка постройки принадлежит только своему чанку: иначе большой склад будет правиться двумя"
                        + " духами сразу. При смене индекса перепроверь owners и GroupKeys.chunk.");
        assertEquals(
                "group:building:storage_3@1,0",
                groups.owner(buildingEdge),
                "Та же постройка в соседнем чанке получает отдельную группу: иначе И-1 и локальная перезаливка"
                        + " сломаются на границе чанка. При смене сборки перепроверь GroupRegistry.rebuild.");
        assertEquals(
                "group:street:s-7@0,0",
                groups.owner(street),
                "Клетка улицы принадлежит только чанковому сегменту: иначе два духа получат один блок."
                        + " При смене индекса перепроверь owners.");
        assertEquals(
                "group:building:storage_3@0,0",
                groups.group(groups.owner(building)).key(),
                "Владелец клетки обязан существовать в реестре: иначе обратный индекс укажет в пустоту,"
                        + " и инвалидация потеряет группу. При смене сборки перепроверь groups и owners.");
        assertEquals(
                new ChunkCell(1, 0),
                groups.group(groups.owner(buildingEdge)).chunk(),
                "Группа края склада обязана нести свой чанк: иначе перезаливка затронет первый участок."
                        + " При смене модели перепроверь Group.chunk.");
    }
}
