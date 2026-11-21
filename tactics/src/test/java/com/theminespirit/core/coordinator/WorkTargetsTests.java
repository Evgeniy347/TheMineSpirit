package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.work.WorkKind;
import com.theminespirit.core.coordinator.work.WorkTarget;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkTargetsTests {
    private static final WorkTarget CELL = new WorkTarget(WorkKind.CELL, new BlockCoord(9, 64, 9));
    private static final WorkTarget TREE = new WorkTarget(WorkKind.TREE, new BlockCoord(4, 64, 2));
    private static final WorkTarget VEIN = new WorkTarget(WorkKind.VEIN, new BlockCoord(1, 40, 1));

    @Test
    void current_NoOwnedTargets_ReturnsEmpty() {
        assertEquals(
                Optional.empty(),
                WorkTargets.current(List.of()),
                "Дух без броней не занят: снимок несёт пустую цель, и координатор снова даёт ему работу"
                        + " Иначе тест current_NoOwnedTargets_ReturnsEmpty перестанет ловить эту поломку. При смене current_NoOwnedTargets_ReturnsEmpty проверь это утверждение.");
    }

    @Test
    void current_SingleOwnedTarget_ReturnsIt() {
        assertEquals(
                Optional.of(TREE),
                WorkTargets.current(List.of(TREE)),
                "Единственная бронь духа — его текущая цель: снимок обязан её назвать, иначе WorkAllocator слеп"
                        + " При смене current_SingleOwnedTarget_ReturnsIt проверь это утверждение.");
    }

    @Test
    void current_TwoTargets_PicksSmallestKindThenAnchor() {
        List<WorkTarget> owned = new ArrayList<>(List.of(VEIN, CELL, TREE));

        assertEquals(
                Optional.of(CELL),
                WorkTargets.current(owned),
                "Из нескольких броней берётся меньшая по виду и якорю: выбор не зависит от порядка в реестре, "
                        + "поэтому два замера одного набора совпадут"
                        + " Иначе тест current_TwoTargets_PicksSmallestKindThenAnchor перестанет ловить эту поломку. При смене current_TwoTargets_PicksSmallestKindThenAnchor проверь это утверждение.");
        assertEquals(
                Optional.of(CELL),
                WorkTargets.current(List.of(CELL, VEIN, TREE)),
                "Другой порядок даёт ту же цель: иначе снимок мигал бы между целями и координатор видел то одно, то другое"
                        + " При смене current_TwoTargets_PicksSmallestKindThenAnchor проверь это утверждение.");
    }

    @Test
    void current_NullCollection_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WorkTargets.current(null),
                "null вместо набора броней не говорит, занят дух или свободен: передавайте пустой набор"
                        + " Иначе тест current_NullCollection_Throws перестанет ловить эту поломку. При смене current_NullCollection_Throws проверь это утверждение.");
    }

    @Test
    void current_NullElement_Throws() {
        List<WorkTarget> owned = new ArrayList<>();
        owned.add(null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> WorkTargets.current(owned),
                "null-цель в наборе броней не опознаёт занятие духа: реестр держит только настоящие цели"
                        + " Иначе тест current_NullElement_Throws перестанет ловить эту поломку. При смене current_NullElement_Throws проверь это утверждение.");
        assertTrue(
                error.getMessage().contains("null"),
                "Сообщение называет причину: без этого при падении не видно, какая бронь повреждена"
                        + " При смене current_NullElement_Throws проверь это утверждение.");
    }
}
