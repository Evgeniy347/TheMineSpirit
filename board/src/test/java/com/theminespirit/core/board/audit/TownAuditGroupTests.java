package com.theminespirit.core.board.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class TownAuditGroupTests {

    private static final BlockCoord CELL = new BlockCoord(1, 64, 1);

    @Test
    void group_Key_AlwaysCarriesExactlyOneChunk() {
        Group street = Group.pending(Group.Kind.STREET, "s-7", new ChunkCell(-16, -32), List.of(), 12);
        Group building = Group.pending(Group.Kind.BUILDING, "storage_3", new ChunkCell(0, 0), List.of(), 40);
        Group ground = Group.pending(Group.Kind.GROUND, "", new ChunkCell(-16, -33), List.of(), 6);

        assertEquals(
                "group:street:s-7@-16,-32",
                street.key(),
                "Ключ улицы обязан нести чанк: иначе одна группа растянется на регион и дух уйдёт через всю"
                        + " карту. При смене ключа перепроверь Group.key и ConstructionBatches.at.");
        assertEquals(
                "group:building:storage_3@0,0",
                building.key(),
                "Постройка дробится по чанкам так же, как улица: иначе большой склад нарушит И-1 и локальная"
                        + " перезаливка затронет весь участок. При смене ключа перепроверь GroupKeys.chunk.");
        assertEquals(
                "group:ground@-16,-33",
                ground.key(),
                "У земли нет предмета — только чанк: иначе обратный индекс клетка→группа даст два ключа для"
                        + " одной клетки. При смене ключа перепроверь парсинг в REST-сводке групп.");
        assertThrows(
                IllegalArgumentException.class,
                () -> Group.pending(Group.Kind.BUILDING, "storage_3", null, List.of(), 4),
                "Любая группа без чанка невозможна: иначе узел графа не получит адрес локальной перезаливки"
                        + " и инвариант И-1 будет нарушен. При смене правила перепроверь Group и GroupKeys.");
    }

    @Test
    void group_WorkBeyondPlan_RejectsConstruction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Group(
                        Group.Kind.BUILDING,
                        "storage_3",
                        new ChunkCell(0, 0),
                        List.of(DiffItem.breakAt(CELL, "stone", SourceRef.building("storage_3"), Access.SPIRIT)),
                        0,
                        Group.Status.PENDING,
                        0,
                        0,
                        ""),
                "Предметов больше плана не бывает: иначе total потерял связь со схемой, pendingPercent() даст "
                        + "отрицательную долю, и вес узла графа станет случайным. При смене правила "
                        + "перепроверь формирование total в ExpectIndex (block 3).");
    }

    @Test
    void diffItems_BreakThenFixThenPlace_OrderBottomUp() {
        SourceRef source = SourceRef.street("s-7");
        List<DiffItem> items = List.of(
                DiffItem.place(new BlockCoord(5, 62, 5), "air", Wanted.block("cobblestone"), source, Access.SPIRIT),
                DiffItem.breakAt(new BlockCoord(5, 65, 5), "dirt", source, Access.SPIRIT),
                DiffItem.fixState(
                        new BlockCoord(5, 63, 5),
                        "oak_stairs[half=bottom]",
                        Wanted.of("oak_stairs[half=top]"),
                        source,
                        Access.SPIRIT),
                DiffItem.breakAt(new BlockCoord(5, 64, 5), "log", source, Access.SPIRIT));

        List<DiffItem> ordered = Group.pending(Group.Kind.STREET, "s-7", new ChunkCell(0, 0), items, 4)
                .ordered();

        assertEquals(
                List.of(DiffItem.Action.BREAK, DiffItem.Action.BREAK, DiffItem.Action.FIX_STATE, DiffItem.Action.PLACE),
                ordered.stream().map(DiffItem::action).toList(),
                "Сначала ломать, потом править состояние, потом ставить: обратный порядок ставит блок на "
                        + "занятую клетку, иначе стройка висит на unsupported до конца задания. При смене порядка "
                        + "перепроверь DiffItem.WORK_ORDER и сортировку RepairAudit.");
        assertEquals(
                List.of(64, 65),
                ordered.stream()
                        .filter(item -> item.action() == DiffItem.Action.BREAK)
                        .map(item -> item.cell().y())
                        .toList(),
                "Разборка улицы идёт снизу вверх: иначе верхний блок падает на невынутый нижний, и дух получает "
                        + "завал вместо прохода. При смене порядка перепроверь правило «снизу вверх» для групп "
                        + "земли (block 7).");
    }

    @Test
    void group_RebuiltAndQuarantined_StatusTracksUnknownAndRevision() {
        Group street = Group.pending(
                Group.Kind.STREET,
                "s-7",
                new ChunkCell(0, 0),
                List.of(DiffItem.breakAt(CELL, "log", SourceRef.street("s-7"), Access.SPIRIT)),
                4);

        assertEquals(
                25,
                street.pendingPercent(),
                "Доля невыполненного считается от плана, а не от остатка: по ней GraphEventFacts.weight поднимает "
                        + "улицу выше склада, иначе вся очередь сдвинется. При смене формулы перепроверь "
                        + "GraphEventFacts.weight и веса в graph.d/audit.json.");

        Group closed = street.rebuilt(List.of(), 4, 0);
        Group blocked = street.rebuilt(List.of(), 4, 1);

        assertEquals(
                Group.Status.DONE,
                closed.status(),
                "Группа закрывается только при пустых предметах и известном чанке: иначе узел графа исчезнет с "
                        + "незакрытой клеткой, а дух останется без работы. При смене статусов перепроверь "
                        + "GraphEventFacts.repairTarget и Group.complete().");
        assertEquals(
                Group.Status.PENDING,
                blocked.status(),
                "Неизвестная клетка не даёт закрыть группу: иначе выгруженный чанк объявят готовым, а после "
                        + "загрузки там окажется яма на дороге. При смене статусов перепроверь инвалидацию "
                        + "LevelEvent.Unload (block 4).");
        assertEquals(
                street.revision(),
                closed.revision(),
                "Закрытие не поднимает ревизию: иначе билеты начнут ссылаться на несуществующие снимки группы. "
                        + "При смене ревизий перепроверь GroupRef в TicketTarget (block 9).");

        Group quarantined = street.quarantined();

        assertEquals(
                street.revision() + 1,
                quarantined.revision(),
                "Промах кэша поднимает ревизию: старый билет обязан устареть, иначе дух повторит ту же "
                        + "несуществующую работу. При смене ревизий перепроверь каскад ISSUED в block 5 и снимок "
                        + "группы в реестре.");
        assertTrue(
                quarantined.reason().contains("кэш"),
                "Причина карантина читается в логе: иначе его не отличить от блокировки по достижимости, и "
                        + "живой разбор начнётся с нуля. При смене причин перепроверь текст AUDIT-MISS.");
    }

    @Test
    void auditMiss_Classify_CacheStatesPickDistinctCause() {
        assertEquals(
                AuditMiss.Cause.NOT_FILLED,
                AuditMiss.classify(new AuditMiss.Signs(false, true, true, true)),
                "Незаполненный чанк — всегда NOT_FILLED: прочие признаки бессмысленны, пока кэш пуст, иначе "
                        + "ложная классификация спрячет настоящую причину промаха. При смене классификации "
                        + "перепроверь порядок веток в AuditMiss.classify.");
        assertEquals(
                AuditMiss.Cause.STALE_TICKET,
                AuditMiss.classify(new AuditMiss.Signs(true, false, false, true)),
                "Билет старше группы — STALE_TICKET: иначе промах спишут на потерянное событие, хотя дело в "
                        + "ревизии снимка. При смене классификации перепроверь GroupRef.revision (block 9).");
        assertEquals(
                AuditMiss.Cause.EQUIVALENCE,
                AuditMiss.classify(new AuditMiss.Signs(true, false, true, false)),
                "Клетка совпадает по правилу, но не по id — это EQUIVALENCE: иначе новый предикат молча "
                        + "проглотит расхождение, и мы решим, что кэш врёт. При смене классификации "
                        + "перепроверь BlockEquivalence.check.");
        assertEquals(
                AuditMiss.Cause.EVENT_MISSED,
                AuditMiss.classify(new AuditMiss.Signs(true, true, false, false)),
                "Изменение есть в журнале, а кэш его не отразил — EVENT_MISSED: иначе потерянный хук "
                        + "BlockChangeWatch останется незамеченным. При смене классификации перепроверь "
                        + "ChangeRing.seenSince.");
        assertEquals(
                AuditMiss.Cause.LEASE_OPTIMISTIC,
                AuditMiss.classify(new AuditMiss.Signs(true, false, false, false)),
                "Ни события, ни расхождения по правилу — оптимистичная правка по аренде: иначе дух, поставивший "
                        + "блок вне очереди, останется ненайденным. При смене классификации перепроверь "
                        + "LeaseData.isActive.");
    }

    @Test
    void auditMiss_Text_NamesCellGroupCauseAndWhatToDo() {
        AuditMiss miss = AuditMiss.of(
                AuditMiss.Cause.EVENT_MISSED,
                AuditMiss.FOREIGN_BLOCK,
                new BlockCoord(3, 64, -7),
                "minecraft:air",
                "minecraft:oak_log",
                Wanted.block("dirt"),
                AuditMiss.Context.of(
                        "group:street:s-7@0,0",
                        2,
                        100,
                        240,
                        1,
                        "тик 239: (3,64,-7) air→oak_log",
                        AuditMiss.FOREIGN_BLOCK));
        String text = miss.text();

        assertTrue(
                text.startsWith("AUDIT-MISS/FOREIGN_BLOCK:")
                        && text.contains("(3,64,-7)")
                        && text.contains("группа group:street:s-7@0,0@2")
                        && text.contains("чанк заполнен на тике 100")
                        && text.contains("промах кэша: EVENT_MISSED")
                        && text.contains("перезалить чанк"),
                "Инцидент обязан нести клетку, факт кэша, блок мира, группу с ревизией, тик заполнения, причину "
                        + "и что делать: без любого из этих полей промах кэша не воспроизвести, иначе разбор "
                        + "уйдёт в догадки. При смене формата перепроверь три канала (events.db, журнал доски, "
                        + "warn в чат) и шаблон assertion из программы steady-vent-lynx.");
        AuditMiss moved = miss.withGroupKey("group:ground:pit@0,0");
        assertTrue(
                moved.text().contains("группа group:ground:pit@0,0@2")
                        && miss.text().contains("группа group:street:s-7@0,0@2"),
                "Инцидент сверки получает фактического владельца клетки без изменения исходного снимка:"
                        + " иначе ground-подгруппа останется без карантина. При смене контекста перепроверь"
                        + " AuditMiss.withGroupKey и TownAuditWatch.quarantine.");
    }
}
