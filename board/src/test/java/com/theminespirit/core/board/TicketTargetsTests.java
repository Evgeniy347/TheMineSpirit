package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.audit.GroupRef;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TicketTargetsTests {
    private static final String GOOD = "repair:hut";
    private static final String BROKEN = "world:backfill:1,64,2";

    @Test
    void decode_UnreadableTarget_IsReportedAndOthersSurvive() {
        Map<String, Map<String, Object>> raw = new LinkedHashMap<>();
        raw.put(GOOD, Map.of("Target", List.of(1, 64, 2)));
        raw.put(BROKEN, Map.of("Target", List.of(1, 64)));

        TicketTargets decoded = TicketTargets.decode(raw);

        assertEquals(
                Map.of(GOOD, TicketTarget.at(new BlockCoord(1, 64, 2))),
                decoded.targets(),
                "Одна битая цель не отменяет загрузку доски: остальные задания сохраняют свои точки"
                        + " Иначе тест decode_UnreadableTarget_IsReportedAndOthersSurvive перестанет ловить эту поломку. При смене decode_UnreadableTarget_IsReportedAndOthersSurvive проверь это утверждение.");
        assertEquals(
                List.of(BROKEN),
                decoded.broken().stream().map(TicketTargets.Broken::id).toList(),
                "Битая цель названа по id: иначе задание молча исполнилось бы у дома"
                        + " При смене decode_UnreadableTarget_IsReportedAndOthersSurvive проверь это утверждение.");
    }

    @Test
    void decode_BrokenTargetReason_NamesTheField() {
        TicketTargets decoded = TicketTargets.decode(Map.of(BROKEN, Map.of("Target", List.of(1, 64))));

        assertTrue(
                decoded.broken().get(0).message().contains("Target"),
                "Причина отказа называет поле NBT, а не только id: "
                        + decoded.broken().get(0).message()
                        + " Иначе тест decode_BrokenTargetReason_NamesTheField перестанет ловить эту поломку. При смене decode_BrokenTargetReason_NamesTheField проверь это утверждение.");
    }

    @Test
    void decode_ReadableTargets_AreRestoredWithoutBroken() {
        TicketTargets decoded = TicketTargets.decode(Map.of(GOOD, Map.of("Target", List.of(-3, 70, 5))));

        assertEquals(
                Map.of(GOOD, TicketTarget.at(new BlockCoord(-3, 70, 5))),
                decoded.targets(),
                "Читаемая цель восстанавливается целиком"
                        + " Иначе тест decode_ReadableTargets_AreRestoredWithoutBroken перестанет ловить эту поломку. При смене decode_ReadableTargets_AreRestoredWithoutBroken проверь это утверждение.");
        assertTrue(
                decoded.broken().isEmpty(),
                "Читаемая доска не даёт битых целей"
                        + " Иначе тест decode_ReadableTargets_AreRestoredWithoutBroken перестанет ловить эту поломку. При смене decode_ReadableTargets_AreRestoredWithoutBroken проверь это утверждение.");
    }

    @Test
    void dropBroken_BrokenTicket_IsCancelledSoGraphRepostsIt() {
        Board board = new Board(() -> 0L);
        board.submit(new BoardTicket(
                BROKEN,
                BoardCase.FILL_CRATER,
                Optional.empty(),
                1,
                0L,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of()));

        List<String> cancelled = TicketTargets.dropBroken(board, List.of(BROKEN, "world:backfill:нет-такого"));

        assertEquals(
                List.of(BROKEN),
                cancelled,
                "Снимается только найденное битое задание: чужой id не считается отменённым"
                        + " Иначе тест dropBroken_BrokenTicket_IsCancelledSoGraphRepostsIt перестанет ловить эту поломку. При смене dropBroken_BrokenTicket_IsCancelledSoGraphRepostsIt проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf(BROKEN),
                "Задание без читаемой цели снимается: иначе оно исполнялось бы у дома или висело навсегда"
                        + " При смене dropBroken_BrokenTicket_IsCancelledSoGraphRepostsIt проверь это утверждение.");
    }

    @Test
    void fillsBuilding_KnownTargetWithoutBuilding_IsRefreshed() {
        TicketTarget known = TicketTarget.at(new BlockCoord(1, 2, 3));
        TicketTarget incoming = known.withBuilding("Хижина");

        assertTrue(
                TicketTarget.fillsBuilding(known, incoming),
                "Открытая задача без подписи объекта обязана получить её при повторной постановке: "
                        + "иначе старая цель висит до переоткрытия"
                        + " При смене fillsBuilding_KnownTargetWithoutBuilding_IsRefreshed проверь это утверждение.");
    }

    @Test
    void fillsBuilding_KnownTargetWithBuilding_IsNotReplaced() {
        TicketTarget known = TicketTarget.at(new BlockCoord(1, 2, 3)).withBuilding("Хижина");

        assertFalse(
                TicketTarget.fillsBuilding(known, known.withBuilding("Сад")),
                "Цель с подписью объекта не перезаписывается: после переименования задачу переоткрывают, а не подменяют"
                        + " Иначе тест fillsBuilding_KnownTargetWithBuilding_IsNotReplaced перестанет ловить эту поломку. При смене fillsBuilding_KnownTargetWithBuilding_IsNotReplaced проверь это утверждение.");
    }

    @Test
    void fillBuilding_KnownPlanAndGroup_SurviveIncomingLabel() {
        ScriptCall plan = new ScriptCall("audit_group", Map.of("group", "group:building:hut"));
        GroupRef ref = new GroupRef("group:building:hut", 2);
        TicketTarget known =
                TicketTarget.at(new BlockCoord(1, 64, 2)).withPlan(plan).withGroup(ref);
        TicketTarget incoming =
                TicketTarget.at(new BlockCoord(9, 64, 9)).withBuilding("hut").withScheme("hut_v1");

        TicketTarget merged = TicketTargets.fillBuilding(known, incoming);

        assertEquals(
                plan,
                merged.plan(),
                "Повторная постановка с подписью постройки сохраняет явный план:"
                        + " иначе тактика снова пересчитает мир вместо выданной работы. При смене merge перепроверь plan.");
        assertEquals(
                ref,
                merged.group(),
                "GroupRef переживает заполнение подписи: иначе билет потеряет"
                        + " снимок группы и промах кэша станет невидимым. При смене merge перепроверь group.");
        assertEquals(
                "hut",
                merged.building(),
                "Подпись постройки из новой цели применяется: иначе граф"
                        + " не сможет связать задание с участком. При смене merge перепроверь withBuilding.");
        assertEquals(
                "hut_v1",
                merged.schemeFile(),
                "Пустая схема известной цели дополняется новой:"
                        + " иначе дух не узнает породу и уровень постройки. При смене merge перепроверь withScheme.");
    }
}
