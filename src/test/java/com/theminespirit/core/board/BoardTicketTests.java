package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoardTicketTests {

    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void ticket_Fields_AreRead() {
        BoardTicket ticket = new BoardTicket("fence_1", BoardCase.REPAIR_FENCE, Optional.of(SPIRIT),
                100, 500, 2, "fence:1:64:2", List.of("capture_1"), Map.of("dirt", 9));

        assertEquals("fence_1", ticket.id(), "id задания обязан читаться: по нему доска находит задание");
        assertEquals(BoardCase.REPAIR_FENCE, ticket.boardCase(),
                "Кейс обязан читаться: по нему доска берёт приоритет и план");
        assertEquals(Optional.of(SPIRIT), ticket.assignee(),
                "Владелец-дух обязан читаться: доска проверяет занятость владельца");
        assertEquals(100, ticket.priority(), "Приоритет обязан читаться: им доска сортирует очередь");
        assertEquals(500, ticket.createdAt(), "Момент постановки обязан читаться: им доска сортирует очередь");
        assertEquals(2, ticket.level(), "Уровень обязан читаться: по нему доска отличает "
                + "расширение шахты от её входа");
        assertEquals("fence:1:64:2", ticket.cause(),
                "Причина обязана читаться: по ней пересборка проверяет актуальность");
        assertEquals(List.of("capture_1"), ticket.dependsOn(),
                "Зависимости обязаны читаться: без них доска не разблокирует задание");
        assertEquals(Map.of("dirt", 9), ticket.requiredItems(),
                "Требуемые ресурсы обязаны читаться: ими гейт держит задание в очереди");
    }

    @Test
    void ticket_EmptyOwner_AnyFreeSpirit() {
        BoardTicket ticket = new BoardTicket("build_1", BoardCase.BUILD_HOUSE, Optional.empty(),
                50, 0, 1, BoardTicket.ALWAYS_CAUSE, List.of(), Map.of());

        assertFalse(ticket.assignee().isPresent(),
                "Пустой владелец означает «любой свободный дух»: доска сама назначает "
                        + "исполнителя при выдаче, адресный билет не нужен");
    }

    @Test
    void ticket_EmptyId_Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardTicket("", BoardCase.BUILD_HOUSE, Optional.empty(), 50, 0, 1,
                        BoardTicket.ALWAYS_CAUSE, List.of(), Map.of()),
                "Пустой id обязан отвергаться: по id доска находит задание и связывает "
                        + "зависимости, пустое имя слило бы разные задания");
        assertThrows(IllegalArgumentException.class,
                () -> new BoardTicket("  ", BoardCase.BUILD_HOUSE, Optional.empty(), 50, 0, 1,
                        BoardTicket.ALWAYS_CAUSE, List.of(), Map.of()),
                "id из пробелов обязан отвергаться: он выглядит пустым для наблюдателя, "
                        + "но проходит мимо проверки на пустую строку");
    }

    @Test
    void ticket_NullCase_Rejected() {
        assertThrows(NullPointerException.class,
                () -> new BoardTicket("build_1", null, Optional.empty(), 50, 0, 1,
                        BoardTicket.ALWAYS_CAUSE, List.of(), Map.of()),
                "Кейс обязан быть задан: без него доска не знает приоритет, гейт и план");
    }

    @Test
    void ticket_NegativeCreatedAt_Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardTicket("build_1", BoardCase.BUILD_HOUSE, Optional.empty(), 50, -1, 1,
                        BoardTicket.ALWAYS_CAUSE, List.of(), Map.of()),
                "Отрицательный момент постановки обязан отвергаться: время до начала отсчёта "
                        + "сломает сортировку по createdAt");
    }

    @Test
    void ticket_ZeroLevel_Rejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new BoardTicket("build_1", BoardCase.BUILD_HOUSE, Optional.empty(), 50, 0, 0,
                        BoardTicket.ALWAYS_CAUSE, List.of(), Map.of()),
                "Нулевой уровень обязан отвергаться: первая постройка ставится уровнем 1, "
                        + "нулевой уровень снял бы условие «уровень 1 готов» с доски");
        assertTrue(error.getMessage().contains("1"),
                "Сообщение обязано называть минимальный уровень: " + error.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> new BoardTicket("build_1", BoardCase.BUILD_HOUSE, Optional.empty(), 50, 0, -2,
                        BoardTicket.ALWAYS_CAUSE, List.of(), Map.of()),
                "Отрицательный уровень обязан отвергаться: уровень ниже первого ломает "
                        + "цепочку уровней постройки");
    }

    @Test
    void ticket_BlankCause_Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardTicket("build_1", BoardCase.BUILD_HOUSE, Optional.empty(), 50, 0, 1,
                        null, List.of(), Map.of()),
                "Причина null обязана отвергаться: без причины пересборка не проверит "
                        + "актуальность, и задание повиснет после отпадения причины");
        assertThrows(IllegalArgumentException.class,
                () -> new BoardTicket("build_1", BoardCase.BUILD_HOUSE, Optional.empty(), 50, 0, 1,
                        "  ", List.of(), Map.of()),
                "Причина из пробелов обязана отвергаться: она не совпадёт ни с одной живой "
                        + "причиной, и задание снимут на первой же пересборке");
    }

    @Test
    void ticket_AlwaysCause_MarksEvergreen() {
        BoardTicket ticket = new BoardTicket("eat_1", BoardCase.EAT_TO_FULL, Optional.empty(),
                80, 0, 1, BoardTicket.ALWAYS_CAUSE, List.of(), Map.of());

        assertEquals("always", ticket.cause(),
                "Постоянное задание задают причиной always: такая причина переживает снятие "
                        + "по актуальности, иначе вечное задание умрёт на первой пересборке");
    }

    @Test
    void ticket_PriorityOutOfRange_Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardTicket("build_1", BoardCase.BUILD_HOUSE, Optional.empty(), -1, 0, 1,
                        BoardTicket.ALWAYS_CAUSE, List.of(), Map.of()),
                "Приоритет ниже нуля обязан отвергаться: доска сравнивает задания с ремонтом, "
                        + "отрицательный приоритет утащил бы задание под все остальные");
        assertThrows(IllegalArgumentException.class,
                () -> new BoardTicket("build_1", BoardCase.BUILD_HOUSE, Optional.empty(),
                        BoardTuning.MAX_PRIORITY + 1, 0, 1, BoardTicket.ALWAYS_CAUSE, List.of(), Map.of()),
                "Приоритет выше максимума обязан отвергаться: выход за границу означает, "
                        + "что доска не сможет сравнить задание с ремонтом ограждений");
    }

    @Test
    void ticket_SelfDependency_Rejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new BoardTicket("build_1", BoardCase.BUILD_HOUSE, Optional.empty(), 50, 0, 1,
                        BoardTicket.ALWAYS_CAUSE, List.of("build_1"), Map.of()),
                "Самозависимость обязана отвергаться: задание навсегда ждало бы самого себя "
                        + "и никогда не попало бы в выдачу");
        assertTrue(error.getMessage().contains("build_1"),
                "Сообщение обязано называть id задания: фактическое " + error.getMessage());
    }

    @Test
    void ticket_NullCollections_Rejected() {
        assertThrows(NullPointerException.class,
                () -> new BoardTicket("build_1", BoardCase.BUILD_HOUSE, Optional.empty(), 50, 0, 1,
                        BoardTicket.ALWAYS_CAUSE, null, Map.of()),
                "dependsOn=null обязан отвергаться: задание без зависимостей задают пустым списком");
        assertThrows(NullPointerException.class,
                () -> new BoardTicket("build_1", BoardCase.BUILD_HOUSE, Optional.empty(), 50, 0, 1,
                        BoardTicket.ALWAYS_CAUSE, List.of(), null),
                "requiredItems=null обязан отвергаться: задание без ресурсов задают пустой картой");
        assertThrows(NullPointerException.class,
                () -> new BoardTicket("build_1", BoardCase.BUILD_HOUSE, null, 50, 0, 1,
                        BoardTicket.ALWAYS_CAUSE, List.of(), Map.of()),
                "assignee=null обязан отвергаться: «любой свободный дух» — это пустой Optional");
    }

    @Test
    void ticket_DefensiveCopies_Immutable() {
        List<String> dependsOn = new ArrayList<>(List.of("capture_1"));
        Map<String, Integer> requiredItems = new HashMap<>(Map.of("dirt", 9));
        BoardTicket ticket = new BoardTicket("fence_1", BoardCase.REPAIR_FENCE, Optional.empty(),
                100, 500, 1, "fence:1:64:2", dependsOn, requiredItems);
        dependsOn.add("other_1");
        requiredItems.put("stone", 5);

        assertEquals(List.of("capture_1"), ticket.dependsOn(),
                "Билет обязан копировать зависимости: подмена списка после создания сломала "
                        + "бы блокировку выдачи по зависимостям");
        assertEquals(Map.of("dirt", 9), ticket.requiredItems(),
                "Билет обязан копировать требуемые ресурсы: подмена карты после создания "
                        + "сломала бы гейт в момент выдачи");
        assertThrows(UnsupportedOperationException.class, () -> ticket.dependsOn().add("late_1"),
                "Зависимости обязаны быть неизменяемыми: доска хранит билет в очереди, "
                        + "менять его может только перевыдача");
        assertThrows(UnsupportedOperationException.class, () -> ticket.requiredItems().put("stone", 5),
                "Требуемые ресурсы обязаны быть неизменяемыми: гейт читает карту при выдаче");
    }

    @Property
    void ticket_EmptyCollections_Normalized(@ForAll("ticketIds") String id,
            @ForAll("boardCases") BoardCase boardCase) {
        BoardTicket ticket = new BoardTicket(id, boardCase, Optional.empty(), 0, 0, 1,
                BoardTicket.ALWAYS_CAUSE, List.of(), Map.of());

        assertTrue(ticket.dependsOn().isEmpty(),
                "Задание без зависимостей обязано читаться пустым списком: гейт и выдача "
                        + "проверяют «зависимостей нет» через isEmpty");
        assertTrue(ticket.requiredItems().isEmpty(),
                "Задание без ресурсов обязано читаться пустой картой: такое задание не "
                        + "ждёт гейта и выдаётся сразу при выполненных зависимостях");
    }

    @Property
    void ticket_Fields_Deterministic(@ForAll("ticketIds") String id,
            @ForAll("boardCases") BoardCase boardCase, @ForAll long createdAt, @ForAll int priority,
            @ForAll("levels") int level, @ForAll("causes") String cause) {
        long safeCreatedAt = Math.floorMod(createdAt, 1_000_000_000L);
        int safePriority = Math.floorMod(priority, BoardTuning.MAX_PRIORITY + 1);
        BoardTicket first = new BoardTicket(id, boardCase, Optional.empty(), safePriority,
                safeCreatedAt, level, cause, List.of(), Map.of());
        BoardTicket second = new BoardTicket(id, boardCase, Optional.empty(), safePriority,
                safeCreatedAt, level, cause, List.of(), Map.of());

        assertEquals(first, second,
                "Два билета с одними полями обязаны быть равны: доска ищет дубликаты по id, "
                        + "а наблюдетель сравнивает задания между пересборками");
    }

    @Provide
    Arbitrary<String> ticketIds() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(12);
    }

    @Provide
    Arbitrary<BoardCase> boardCases() {
        return Arbitraries.of(BoardCase.values());
    }

    @Provide
    Arbitrary<Integer> levels() {
        return Arbitraries.integers().between(1, 4);
    }

    @Provide
    Arbitrary<String> causes() {
        return Arbitraries.of("always", "crater:1:64:2", "fence:3:64:1", "animal:capture_1");
    }
}
