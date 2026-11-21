package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoardTicketPart3Tests {
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

    @Test
    void ticket_PriorityOutOfRange_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoardTicket(
                        "build_1",
                        BoardCase.BUILD_HOUSE,
                        Optional.empty(),
                        -1,
                        0,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                "Приоритет ниже нуля обязан отвергаться: доска сравнивает задания с ремонтом, "
                        + "отрицательный приоритет утащил бы задание под все остальные Иначе тест ticket_PriorityOutOfRange_Rejected перестанет ловить эту поломку. При смене ticket_PriorityOutOfRange_Rejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoardTicket(
                        "build_1",
                        BoardCase.BUILD_HOUSE,
                        Optional.empty(),
                        BoardTuning.MAX_PRIORITY + 1,
                        0,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                "Приоритет выше максимума обязан отвергаться: выход за границу означает, "
                        + "что доска не сможет сравнить задание с ремонтом ограждений При смене ticket_PriorityOutOfRange_Rejected проверь это утверждение.");
    }

    @Test
    void ticket_SelfDependency_Rejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new BoardTicket(
                        "build_1",
                        BoardCase.BUILD_HOUSE,
                        Optional.empty(),
                        50,
                        0,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of("build_1"),
                        Map.of()),
                "Самозависимость обязана отвергаться: задание навсегда ждало бы самого себя "
                        + "и никогда не попало бы в выдачу Иначе тест ticket_SelfDependency_Rejected перестанет ловить эту поломку. При смене ticket_SelfDependency_Rejected проверь это утверждение.");
        assertTrue(
                error.getMessage().contains("build_1"),
                "Сообщение обязано называть id задания: фактическое  Иначе тест ticket_SelfDependency_Rejected перестанет ловить эту поломку. При смене ticket_SelfDependency_Rejected проверь это утверждение."
                        + error.getMessage());
    }

    @Test
    void ticket_NullCollections_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> new BoardTicket(
                        "build_1",
                        BoardCase.BUILD_HOUSE,
                        Optional.empty(),
                        50,
                        0,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        null,
                        Map.of()),
                "dependsOn=null обязан отвергаться: задание без зависимостей задают пустым списком Иначе тест ticket_NullCollections_Rejected перестанет ловить эту поломку. При смене ticket_NullCollections_Rejected проверь это утверждение.");
        assertThrows(
                NullPointerException.class,
                () -> new BoardTicket(
                        "build_1",
                        BoardCase.BUILD_HOUSE,
                        Optional.empty(),
                        50,
                        0,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        null),
                "requiredItems=null обязан отвергаться: задание без ресурсов задают пустой картой Иначе тест ticket_NullCollections_Rejected перестанет ловить эту поломку. При смене ticket_NullCollections_Rejected проверь это утверждение.");
        assertThrows(
                NullPointerException.class,
                () -> new BoardTicket(
                        "build_1",
                        BoardCase.BUILD_HOUSE,
                        null,
                        50,
                        0,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                "assignee=null обязан отвергаться: «любой свободный дух» — это пустой Optional Иначе тест ticket_NullCollections_Rejected перестанет ловить эту поломку. При смене ticket_NullCollections_Rejected проверь это утверждение.");
    }

    @Test
    void ticket_DefensiveCopies_Immutable() {
        List<String> dependsOn = new ArrayList<>(List.of("capture_1"));
        Map<String, Integer> requiredItems = new HashMap<>(Map.of("dirt", 9));
        BoardTicket ticket = new BoardTicket(
                "fence_1",
                BoardCase.REPAIR_FENCE,
                Optional.empty(),
                100,
                500,
                1,
                "fence:1:64:2",
                dependsOn,
                requiredItems);
        dependsOn.add("other_1");
        requiredItems.put("stone", 5);
        assertEquals(
                List.of("capture_1"),
                ticket.dependsOn(),
                "Билет обязан копировать зависимости: подмена списка после создания сломала "
                        + "бы блокировку выдачи по зависимостям Иначе тест ticket_DefensiveCopies_Immutable перестанет ловить эту поломку. При смене ticket_DefensiveCopies_Immutable проверь это утверждение.");
        assertEquals(
                Map.of("dirt", 9),
                ticket.requiredItems(),
                "Билет обязан копировать требуемые ресурсы: подмена карты после создания "
                        + "сломала бы гейт в момент выдачи Иначе тест ticket_DefensiveCopies_Immutable перестанет ловить эту поломку. При смене ticket_DefensiveCopies_Immutable проверь это утверждение.");
        assertThrows(
                UnsupportedOperationException.class,
                () -> ticket.dependsOn().add("late_1"),
                "Зависимости обязаны быть неизменяемыми: доска хранит билет в очереди, "
                        + "менять его может только перевыдача Иначе тест ticket_DefensiveCopies_Immutable перестанет ловить эту поломку. При смене ticket_DefensiveCopies_Immutable проверь это утверждение.");
        assertThrows(
                UnsupportedOperationException.class,
                () -> ticket.requiredItems().put("stone", 5),
                "Требуемые ресурсы обязаны быть неизменяемыми: гейт читает карту при выдаче Иначе тест ticket_DefensiveCopies_Immutable перестанет ловить эту поломку. При смене ticket_DefensiveCopies_Immutable проверь это утверждение.");
    }

    @Property
    void ticket_EmptyCollections_Normalized(@ForAll("ticketIds") String id, @ForAll("boardCases") BoardCase boardCase) {
        BoardTicket ticket = new BoardTicket(
                id, boardCase, Optional.empty(), 0, 0, 1, BoardTicket.ALWAYS_CAUSE, List.of(), Map.of());
        assertTrue(
                ticket.dependsOn().isEmpty(),
                "Задание без зависимостей обязано читаться пустым списком: гейт и выдача "
                        + "проверяют «зависимостей нет» через isEmpty Иначе тест ticket_EmptyCollections_Normalized перестанет ловить эту поломку. При смене ticket_EmptyCollections_Normalized проверь это утверждение.");
        assertTrue(
                ticket.requiredItems().isEmpty(),
                "Задание без ресурсов обязано читаться пустой картой: такое задание не "
                        + "ждёт гейта и выдаётся сразу при выполненных зависимостях Иначе тест ticket_EmptyCollections_Normalized перестанет ловить эту поломку. При смене ticket_EmptyCollections_Normalized проверь это утверждение.");
    }

    @Property
    void ticket_Fields_Deterministic(
            @ForAll("ticketIds") String id,
            @ForAll("boardCases") BoardCase boardCase,
            @ForAll long createdAt,
            @ForAll int priority,
            @ForAll("levels") int level,
            @ForAll("causes") String cause) {
        long safeCreatedAt = Math.floorMod(createdAt, 1_000_000_000L);
        int safePriority = Math.floorMod(priority, BoardTuning.MAX_PRIORITY + 1);
        BoardTicket first = new BoardTicket(
                id, boardCase, Optional.empty(), safePriority, safeCreatedAt, level, cause, List.of(), Map.of());
        BoardTicket second = new BoardTicket(
                id, boardCase, Optional.empty(), safePriority, safeCreatedAt, level, cause, List.of(), Map.of());
        assertEquals(
                first,
                second,
                "Два билета с одними полями обязаны быть равны: доска ищет дубликаты по id, "
                        + "а наблюдетель сравнивает задания между пересборками Иначе тест ticket_Fields_Deterministic перестанет ловить эту поломку. При смене ticket_Fields_Deterministic проверь это утверждение.");
    }
}
