package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpiritEntityBoundaryGuardTests {
    private static final int MAX_FILES = 411;

    @Test
    void spiritEntity_ReferencesGrow_AreReported() {
        assertEquals(
                List.of("SpiritEntity: " + (MAX_FILES + 2) + " файлов при пределе " + MAX_FILES),
                growthProblems(MAX_FILES + 2),
                "Сторож ослеп: рост числа ссылок на SpiritEntity обязан попасть в нарушители");
    }

    @Test
    void spiritEntity_AtLimit_IsAccepted() {
        assertEquals(List.of(), growthProblems(MAX_FILES), "Сторож ложно срабатывает: предел — не нарушение");
    }

    @Test
    void spiritEntity_RealCount_DoesNotGrow() {
        int files = (int) SourceFiles.javaUnder(SourceFiles.MAIN).values().stream()
                .filter(text -> text.contains("SpiritEntity"))
                .count();
        assertEquals(
                List.of(),
                growthProblems(files),
                "Ссылки на SpiritEntity снова растут (" + files + " файлов при пределе " + MAX_FILES + "): "
                        + "это универсальный контекст адаптера, юнит-тесты механик без Minecraft невозможны, "
                        + "правка сущности задевает сотни файлов (ревью 19.11, P1-1). Как надо: новый код "
                        + "механики принимает узкий интерфейс (WorldView/InventoryAccess/MovementHandle), "
                        + "а не SpiritEntity; стало меньше ссылок — снизь предел MAX_FILES в этом стороже.");
    }

    private static List<String> growthProblems(int files) {
        if (files <= MAX_FILES) {
            return List.of();
        }
        return List.of("SpiritEntity: " + files + " файлов при пределе " + MAX_FILES);
    }
}
