package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class BuildPitTests {
    @Test
    void needsEscape_SpiritBelowBaseNotTrapped_IsFalse() {
        assertFalse(
                BuildPit.needsEscape(70, 80, () -> false),
                "Дух на земле под крышей склада не в яме: низ схемы на y=80 — ошибка основания забора, а не повод "
                        + "копать вверх. Иначе DigOut ищет путь на крышу полным перебором и падает каждые 3,4 с"
                        + " При смене needsEscape_SpiritBelowBaseNotTrapped_IsFalse проверь это утверждение.");
    }

    @Test
    void needsEscape_SpiritBelowBaseInSealedPocket_IsTrue() {
        assertTrue(
                BuildPit.needsEscape(64, 68, () -> true),
                "Запертый дух ниже низа схемы выходит к постройке: это настоящая яма, DigOut здесь по делу"
                        + " Иначе тест needsEscape_SpiritBelowBaseInSealedPocket_IsTrue перестанет ловить эту поломку. При смене needsEscape_SpiritBelowBaseInSealedPocket_IsTrue проверь это утверждение.");
    }

    @Test
    void needsEscape_SpiritAtOrAboveBase_IsFalseWithoutTrappedCheck() {
        AtomicBoolean asked = new AtomicBoolean();

        assertFalse(
                BuildPit.needsEscape(70, 70, () -> {
                    asked.set(true);
                    return true;
                }),
                "Дух на уровне основания или выше не копает: выхода вверх нет, работа идёт обычным подходом"
                        + " Иначе тест needsEscape_SpiritAtOrAboveBase_IsFalseWithoutTrappedCheck перестанет ловить эту поломку. При смене needsEscape_SpiritAtOrAboveBase_IsFalseWithoutTrappedCheck проверь это утверждение.");
        assertFalse(
                asked.get(),
                "Запертость не считается, когда дух не ниже основания: проверка запертости — обход мира, и звать "
                        + "его на каждой стоянке стройки нельзя"
                        + " Иначе тест needsEscape_SpiritAtOrAboveBase_IsFalseWithoutTrappedCheck перестанет ловить эту поломку. При смене needsEscape_SpiritAtOrAboveBase_IsFalseWithoutTrappedCheck проверь это утверждение.");
    }

    @Test
    void needsEscape_SpiritOneBelowBase_IsFalse() {
        assertFalse(
                BuildPit.needsEscape(69, 70, () -> true),
                "Один блок ниже низа схемы — не яма: дух поднимается шагом, DigOut не запускается"
                        + " Иначе тест needsEscape_SpiritOneBelowBase_IsFalse перестанет ловить эту поломку. При смене needsEscape_SpiritOneBelowBase_IsFalse проверь это утверждение.");
    }

    @Test
    void trapped_SealedOrEmbedded_IsTrue() {
        assertTrue(
                BuildPit.trapped(true, false),
                "Замурованность (sealed) — запертость: дух в замкнутой клетке без выхода"
                        + " Иначе тест trapped_SealedOrEmbedded_IsTrue перестанет ловить эту поломку. При смене trapped_SealedOrEmbedded_IsTrue проверь это утверждение.");
        assertTrue(
                BuildPit.trapped(false, true),
                "Застрял в блоке (embedded) — тоже запертость: DigOut обязан считать оба признака одинаково"
                        + " Иначе тест trapped_SealedOrEmbedded_IsTrue перестанет ловить эту поломку. При смене trapped_SealedOrEmbedded_IsTrue проверь это утверждение.");
        assertFalse(
                BuildPit.trapped(false, false),
                "Открытая яма не запертость: её оставляют общему спасению, иначе любая низина снова копает вверх"
                        + " При смене trapped_SealedOrEmbedded_IsTrue проверь это утверждение.");
    }

    @Test
    void needsEscape_TrappedSpiritBelowBase_IsTrue() {
        assertTrue(
                BuildPit.needsEscape(55, 70, () -> true),
                "Запертый дух на 15 блоков ниже основания выходит: без DigOut он не доберётся до низа схемы"
                        + " Иначе тест needsEscape_TrappedSpiritBelowBase_IsTrue перестанет ловить эту поломку. При смене needsEscape_TrappedSpiritBelowBase_IsTrue проверь это утверждение.");
    }
}
