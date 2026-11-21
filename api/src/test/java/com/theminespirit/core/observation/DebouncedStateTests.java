package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DebouncedStateTests {
    @Test
    void update_FirstState_IsReturnedImmediately() {
        DebouncedState state = new DebouncedState(40);

        assertEquals(
                "вода",
                state.update("вода", 10).orElseThrow(),
                "Первое состояние печатается сразу: иначе вход духа в воду теряется за выдержкой"
                        + " При смене update_FirstState_IsReturnedImmediately проверь это утверждение.");
    }

    @Test
    void update_TransientFlip_IsIgnored() {
        DebouncedState state = new DebouncedState(40);
        state.update("вода", 10);

        assertTrue(
                state.update("берег", 11).isEmpty(),
                "Короткое всплытие на берег — ещё не смена состояния"
                        + " Иначе тест update_TransientFlip_IsIgnored перестанет ловить эту поломку. При смене update_TransientFlip_IsIgnored проверь это утверждение.");
        assertTrue(
                state.update("вода", 12).isEmpty(),
                "Возврат в воду до выдержки не печатается второй раз"
                        + " Иначе тест update_TransientFlip_IsIgnored перестанет ловить эту поломку. При смене update_TransientFlip_IsIgnored проверь это утверждение.");
        assertEquals(
                "вода",
                state.current(),
                "Текущее состояние остаётся прежним: лог не дрожит на каждом всплытии"
                        + " Иначе тест update_TransientFlip_IsIgnored перестанет ловить эту поломку. При смене update_TransientFlip_IsIgnored проверь это утверждение.");
    }

    @Test
    void update_HeldFlip_IsReturned() {
        DebouncedState state = new DebouncedState(40);
        state.update("вода", 10);
        state.update("берег", 20);

        assertEquals(
                "берег",
                state.update("берег", 60).orElseThrow(),
                "Состояние, продержавшееся выдержку, печатается: выход из воды виден в логе"
                        + " Иначе тест update_HeldFlip_IsReturned перестанет ловить эту поломку. При смене update_HeldFlip_IsReturned проверь это утверждение.");
    }

    @Test
    void update_SameState_IsSilent() {
        DebouncedState state = new DebouncedState(40);
        state.update("вода", 10);

        assertTrue(
                state.update("вода", 100).isEmpty(),
                "То же состояние не повторяется: сводка идёт по своему сроку"
                        + " Иначе тест update_SameState_IsSilent перестанет ловить эту поломку. При смене update_SameState_IsSilent проверь это утверждение.");
    }

    @Test
    void update_FlappingAtBoundary_ReportsOnlyFirstState() {
        DebouncedState state = new DebouncedState(40);
        int reported = 0;
        for (int tick = 0; tick < 200; tick++) {
            if (state.update(tick % 2 == 0 ? "вода" : "воздух", tick).isPresent()) {
                reported++;
            }
        }

        assertEquals(
                1,
                reported,
                "Дух на границе воды дрожит каждый тик: [pos] не должен печатать это как смену "
                        + "состояния сотни раз (observe18 B10, живая проверка)"
                        + " Иначе тест update_FlappingAtBoundary_ReportsOnlyFirstState перестанет ловить эту поломку. При смене update_FlappingAtBoundary_ReportsOnlyFirstState проверь это утверждение.");
    }
}
