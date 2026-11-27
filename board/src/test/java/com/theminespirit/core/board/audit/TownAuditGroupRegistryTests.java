package com.theminespirit.core.board.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TownAuditGroupRegistryTests {

    private static final ChunkCell CHUNK = new ChunkCell(0, 0);
    private static final BlockCoord BUILDING = new BlockCoord(0, 64, 0);
    private static final BlockCoord STREET = new BlockCoord(1, 64, 0);
    private static final BlockCoord GROUND = new BlockCoord(2, 63, 0);

    private static ExpectIndex.Index index(Expectation... expectations) {
        Map<BlockCoord, Expectation> cells = new LinkedHashMap<>();
        for (Expectation expectation : expectations) {
            cells.put(expectation.cell(), expectation);
        }
        return new ExpectIndex.Index(cells, 0, 0);
    }

    private static Expectation expectation(BlockCoord cell, Wanted wanted, SourceRef source) {
        return new Expectation(cell, wanted, source, Access.SPIRIT);
    }

    private static AuditRegistry facts(Map<BlockCoord, String> cells) {
        AuditRegistry registry = new AuditRegistry(AuditBudgets.DEFAULT, 1, 1);
        registry.plan(1, 64);
        registry.retain(Set.of(CHUNK));
        registry.ensure(CHUNK);
        registry.fill(2048, cell -> cells.get(cell), 1L);
        return registry;
    }

    @Test
    void groupRegistry_Sources_FormBuildingStreetAndGroundGroups() {
        Map<BlockCoord, String> world = new LinkedHashMap<>();
        world.put(BUILDING, "minecraft:oak_planks");
        world.put(STREET, "minecraft:oak_fence");
        world.put(GROUND, "minecraft:grass_block");
        AuditRegistry facts = facts(world);
        GroupRegistry groups = new GroupRegistry();
        groups.rebuild(
                index(
                        expectation(BUILDING, Wanted.block("minecraft:stone"), SourceRef.building("storage_3")),
                        expectation(STREET, Wanted.air(), SourceRef.street("s-7")),
                        expectation(GROUND, Wanted.ground(64), SourceRef.GROUND)),
                1L);

        assertEquals(
                3,
                groups.sync(facts, 1L, 10),
                "Все источники собираются в свои группы: иначе улица"
                        + " и склад смешаются в одну работу с разным приоритетом. При смене группировки перепроверь GroupKeys.");
        Group building = groups.group("group:building:storage_3");
        Group street = groups.group("group:street:s-7@0,0");
        Group ground = groups.group("group:ground@0,0");
        assertEquals(
                Group.Status.PENDING,
                building.status(),
                "Группа постройки существует по id участка"
                        + " и ждёт работы: иначе склад не попадёт в граф. При смене ключа перепроверь GroupKeys.");
        assertEquals(
                Group.Status.PENDING,
                street.status(),
                "Группа улицы существует по id и чанку:"
                        + " иначе препятствие над плиткой останется вне работы. При смене ключа перепроверь GroupKeys.");
        assertEquals(
                Group.Status.DONE,
                ground.status(),
                "Группа земли существует по чанку и уже чиста:"
                        + " иначе обратный индекс потеряет базовый слой. При смене ключа перепроверь GroupKeys.");
        assertEquals(
                DiffItem.Action.PLACE,
                building.items().get(0).action(),
                "Чужой блок в схеме требует установки ожидаемого: иначе дух оставит доски вместо камня,"
                        + " и постройка не будет соответствовать плану. При смене правила перепроверь build.");
        assertEquals(
                DiffItem.Action.BREAK,
                street.items().get(0).action(),
                "Препятствие над улицой требует слома: иначе схема будет чистой на бумаге, но дух не"
                        + " сможет пройти по дороге к складу. При смене правила перепроверь Wanted.AIR и GroupRegistry.build.");
        assertEquals(
                Group.Status.DONE,
                ground.status(),
                "Земляной класс не требует работы: иначе каждая"
                        + " тропа снова попадёт в расчистку. При смене правила перепроверь BlockEquivalence.GROUND.");
        assertEquals(
                "group:building:storage_3",
                groups.owner(BUILDING),
                "Обратный индекс ведёт клетку в"
                        + " единственную группу: иначе инвалидация не найдёт владельца блока. При смене индекса перепроверь owners.");
    }

    @Test
    void groupRegistry_UnknownFact_BlocksGroupWithoutWork() {
        AuditRegistry facts = facts(Map.of());
        GroupRegistry groups = new GroupRegistry();
        groups.rebuild(
                index(expectation(BUILDING, Wanted.block("minecraft:stone"), SourceRef.building("storage_3"))), 1L);

        groups.sync(facts, 1L, 10);

        Group group = groups.group("group:building:storage_3");
        assertEquals(
                1,
                group.total(),
                "Группа существует даже пока чанк unknown: иначе клетка исчезнет"
                        + " из инварианта И-1. При смене сборки перепроверь GroupRegistry.build.");
        assertEquals(
                Group.Status.BLOCKED,
                group.status(),
                "Неизвестный факт блокирует группу: иначе дух"
                        + " получит работу по несуществующему снимку. При смене правила перепроверь status в build.");
        assertEquals(
                1,
                group.unknown(),
                "Unknown считается отдельно от работы: иначе REST покажет пустую"
                        + " группу без причины блокировки. При смене модели перепроверь Group.unknown.");
        assertTrue(
                group.items().isEmpty(),
                "Unknown не рождает ложный BREAK или PLACE: иначе аудит"
                        + " выдаст задание на блок, которого не видел. При смене правила перепроверь found == null.");
    }

    @Test
    void groupRegistry_WrongState_CreatesFixStateItem() {
        Map<BlockCoord, String> world = new LinkedHashMap<>();
        world.put(BUILDING, "minecraft:oak_stairs[facing=north,half=bottom]");
        AuditRegistry facts = facts(world);
        GroupRegistry groups = new GroupRegistry();
        groups.rebuild(
                index(expectation(
                        BUILDING,
                        Wanted.of("minecraft:oak_stairs[facing=north,half=top]"),
                        SourceRef.building("storage_3"))),
                1L);

        groups.sync(facts, 1L, 10);

        Group group = groups.group("group:building:storage_3");
        assertEquals(
                1,
                group.items().size(),
                "Группа ступеньки существует и несёт одну правку: иначе"
                        + " тактика не получит координату. При смене сборки перепроверь GroupRegistry.build.");
        assertEquals(
                DiffItem.Action.FIX_STATE,
                group.items().get(0).action(),
                "Неверная половина лестницы"
                        + " правит состояние, а не ломает блок: иначе дух гоняет ступеньку по кругу. При смене правила"
                        + " перепроверь BlockEquivalence.STATE и DiffItem.fixState.");
    }

    @Test
    void groupRegistry_IssueThenQuarantine_ReleasesTicketAndStopsOptimism() {
        Map<BlockCoord, String> world = new LinkedHashMap<>();
        world.put(BUILDING, "minecraft:oak_planks");
        AuditRegistry facts = facts(world);
        GroupRegistry groups = new GroupRegistry();
        groups.rebuild(
                index(expectation(BUILDING, Wanted.block("minecraft:stone"), SourceRef.building("storage_3"))), 1L);
        groups.sync(facts, 1L, 10);
        String key = "group:building:storage_3";

        groups.issue("ticket-1", key);
        assertEquals(
                Group.Status.ISSUED,
                groups.group(key).status(),
                "Выдача меняет статус группы на ISSUED: иначе каскад не отличит свой билет от чужой руки"
                        + " и снова откроет работу духа. При смене правила перепроверь GroupRegistry.issue.");
        assertFalse(
                facts.record(new ChangeRing.Change(
                        5L,
                        BUILDING,
                        "minecraft:cobblestone",
                        "minecraft:stone",
                        ChangeRing.Actor.SPIRIT,
                        "своя работа",
                        "ticket-1",
                        "")),
                "Пока билет выдан, своё изменение оптимистично: иначе дух тут же переоткроет свою группу."
                        + " При смене правила перепроверь AuditRegistry.issued.");

        groups.quarantine(key);
        assertEquals(
                Group.Status.QUARANTINED,
                groups.group(key).status(),
                "Промах кэша ставит группу в"
                        + " карантин: иначе следующая выдача снова получит устаревший снимок. При смене правила перепроверь"
                        + " Group.quarantined.");
        assertTrue(
                facts.record(new ChangeRing.Change(
                        6L,
                        BUILDING,
                        "minecraft:stone",
                        "minecraft:air",
                        ChangeRing.Actor.SPIRIT,
                        "после карантина",
                        "ticket-1",
                        "")),
                "Карантин освобождает билет: иначе продолжение старого духа снова будет считаться своим и"
                        + " спрячет реальное изменение. При смене правила перепроверь quarantine и released.");
    }

    @Test
    void groupRegistry_FillCompletion_RebuildsBlockedGroup() {
        AuditRegistry facts = new AuditRegistry(AuditBudgets.DEFAULT, 1, 1);
        facts.plan(1, 64);
        facts.retain(Set.of(CHUNK));
        facts.ensure(CHUNK);
        GroupRegistry groups = new GroupRegistry();
        groups.rebuild(
                index(expectation(BUILDING, Wanted.block("minecraft:stone"), SourceRef.building("storage_3"))), 1L);
        groups.sync(facts, 1L, 10);
        assertEquals(
                Group.Status.BLOCKED,
                groups.group("group:building:storage_3").status(),
                "До наполнения"
                        + " группа заблокирована: иначе клиент покажет готовность на пустом кэше. При смене правила"
                        + " перепроверь sync и build.");

        facts.fill(2048, cell -> cell.equals(BUILDING) ? "minecraft:oak_planks" : "minecraft:air", 2L);
        groups.sync(facts, 1L, 10);

        Group group = groups.group("group:building:storage_3");
        assertEquals(
                Group.Status.PENDING,
                group.status(),
                "После заполнения группа возвращается в работу:"
                        + " иначе BLOCKED повиснет навсегда. При смене очереди перепроверь fillVersion и waiting.");
        assertEquals(
                1,
                group.items().size(),
                "После заполнения виден конкретный дефект: иначе граф не"
                        + " сможет выдать билет с координатой. При смене правила перепроверь build.");
        assertNull(
                groups.owner(new BlockCoord(9, 64, 9)),
                "Клетка вне ожиданий не имеет владельца: иначе"
                        + " инвалидация припишет её случайной группе. При смене индекса перепроверь owners.");
    }
}
