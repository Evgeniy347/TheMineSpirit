package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import org.junit.jupiter.api.Test;

class TicketPlansUpgradeTests {
    private static final BlockCoord AT = new BlockCoord(0, 64, 0);

    @Test
    void plan_PenUpgrade_CarriesPreviousScheme() {
        assertNull(
                schematic(BoardCase.BUILD_PEN, 1).params().get("previous"),
                "Первая постройка на чистом месте не имеет старой схемы: сравнение с ней ничего не снимает"
                        + " Иначе тест plan_PenUpgrade_CarriesPreviousScheme перестанет ловить эту поломку. При смене plan_PenUpgrade_CarriesPreviousScheme проверь это утверждение.");
        assertEquals(
                "pen_4x5",
                schematic(BoardCase.BUILD_PEN, 2).params().get("previous"),
                "Повышение загона знает старую схему: лишние блоки уровня снимаются по позициям, а не по меткам"
                        + " Иначе тест plan_PenUpgrade_CarriesPreviousScheme перестанет ловить эту поломку. При смене plan_PenUpgrade_CarriesPreviousScheme проверь это утверждение.");
        assertEquals(
                "pen_7x7",
                schematic(BoardCase.BUILD_PEN, 3).params().get("previous"),
                "Третий уровень загона сравнивается со вторым"
                        + " Иначе тест plan_PenUpgrade_CarriesPreviousScheme перестанет ловить эту поломку. При смене plan_PenUpgrade_CarriesPreviousScheme проверь это утверждение.");
    }

    @Test
    void plan_EnchantUpgrade_CarriesPreviousScheme() {
        assertEquals(
                "enchant_room_v1",
                schematic(BoardCase.ENCHANT_ROOM, 2).params().get("previous"),
                "Чаровальня уровня 2 снимает старую схему уровня 1 точно"
                        + " Иначе тест plan_EnchantUpgrade_CarriesPreviousScheme перестанет ловить эту поломку. При смене plan_EnchantUpgrade_CarriesPreviousScheme проверь это утверждение.");
        assertEquals(
                "enchant_room_v2",
                schematic(BoardCase.ENCHANT_ROOM, 3).params().get("previous"),
                "Чаровальня уровня 3 сравнивается с уровнем 2"
                        + " Иначе тест plan_EnchantUpgrade_CarriesPreviousScheme перестанет ловить эту поломку. При смене plan_EnchantUpgrade_CarriesPreviousScheme проверь это утверждение.");
    }

    @Test
    void plan_WarehouseUpgrade_CarriesPreviousScheme() {
        assertNull(
                schematic(BoardCase.BUILD_WAREHOUSE, 1).params().get("previous"),
                "Первый склад строится без старой схемы"
                        + " Иначе тест plan_WarehouseUpgrade_CarriesPreviousScheme перестанет ловить эту поломку. При смене plan_WarehouseUpgrade_CarriesPreviousScheme проверь это утверждение.");
        assertEquals(
                WarehouseRules.scheme(1),
                schematic(BoardCase.BUILD_WAREHOUSE, 2).params().get("previous"),
                "Склад уровня 2 знает схему уровня 1: иначе старые полки остаются висеть"
                        + " При смене plan_WarehouseUpgrade_CarriesPreviousScheme проверь это утверждение.");
    }

    @Test
    void plan_HouseUpgrade_CarriesPreviousScheme() {
        assertEquals(
                BuildingLevels.scheme(BoardCase.BUILD_HOUSE, 1).orElseThrow(),
                schematic(BoardCase.BUILD_HOUSE, 2).params().get("previous"),
                "Дом уровня 2 сравнивается со схемой уровня 1"
                        + " Иначе тест plan_HouseUpgrade_CarriesPreviousScheme перестанет ловить эту поломку. При смене plan_HouseUpgrade_CarriesPreviousScheme проверь это утверждение.");
    }

    private static ScriptCall schematic(BoardCase boardCase, int level) {
        return find(TicketPlans.plan(boardCase, level, TicketTarget.at(AT)));
    }

    private static ScriptCall find(TaskNode node) {
        if (node instanceof ScriptCall call && "build_schematic".equals(call.script())) {
            return call;
        }
        if (node instanceof SequenceNode sequence) {
            for (TaskNode step : sequence.steps()) {
                ScriptCall found = findOrNull(step);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof FallbackNode fallback) {
            for (TaskNode step : fallback.steps()) {
                ScriptCall found = findOrNull(step);
                if (found != null) {
                    return found;
                }
            }
        }
        throw new IllegalStateException("В плане нет build_schematic: " + node);
    }

    private static ScriptCall findOrNull(TaskNode node) {
        try {
            return find(node);
        } catch (IllegalStateException none) {
            return null;
        }
    }
}
