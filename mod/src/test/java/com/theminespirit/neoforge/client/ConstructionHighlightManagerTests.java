package com.theminespirit.neoforge.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConstructionHighlightManagerTests {
    private final ConstructionHighlightManager manager = ConstructionHighlightManager.instance();

    @BeforeEach
    void reset() {
        if (manager.isEnabled()) {
            manager.toggle();
        }
        manager.clear();
    }

    @Test
    void toggle_FirstPress_EnablesMode() {
        manager.toggle();

        assertTrue(
                manager.isEnabled(),
                "Первое Ctrl+J обязано включать режим: иначе контуры не появятся и режим нельзя проверить в игре; "
                        + "при смене подсветки конфликтов строительства проверь, что toggle включает флаг");
    }

    @Test
    void toggle_SecondPress_DisablesAndClearsAudit() {
        manager.toggle();
        manager.updateData(Set.of(new BlockPos(0, 64, 0)), Set.of(new BlockPos(1, 64, 1)));

        manager.toggle();

        assertFalse(
                manager.isEnabled(),
                "Второе Ctrl+J обязано выключать режим: иначе контуры не убрать, не выходя из мира, и они останутся "
                        + "на экране; при смене подсветки конфликтов строительства проверь, что toggle выключает флаг");
        assertEquals(
                Set.of(),
                manager.wrongBlocks(),
                "Выключение обязано сбрасывать блоки на снос: иначе при повторном включении рисуется устаревший "
                        + "аудит, которого уже нет в мире; при смене очистки при выключении проверь, что clear зовётся из toggle");
        assertEquals(
                Set.of(),
                manager.missingBlocks(),
                "Выключение обязано сбрасывать отсутствующие блоки: иначе при повторном включении рисуется устаревший "
                        + "аудит, которого уже нет в мире; при смене очистки при выключении проверь, что clear зовётся из toggle");
    }

    @Test
    void updateData_CalledSets_StoresCopies() {
        Set<BlockPos> wrong = new HashSet<>(Set.of(new BlockPos(1, 64, 2)));
        Set<BlockPos> missing = new HashSet<>(Set.of(new BlockPos(3, 64, 4)));

        manager.updateData(wrong, missing);
        wrong.add(new BlockPos(5, 64, 6));
        missing.clear();

        assertEquals(
                Set.of(new BlockPos(1, 64, 2)),
                manager.wrongBlocks(),
                "Менеджер обязан хранить копию набора на снос: иначе мутация исходного множества изменит контуры "
                        + "на экране без нового аудита; при смене updateData проверь, что множества копируются через Set.copyOf");
        assertEquals(
                Set.of(new BlockPos(3, 64, 4)),
                manager.missingBlocks(),
                "Менеджер обязан хранить копию отсутствующих блоков: иначе очистка исходного множества сотрёт "
                        + "подсветку постановки без нового аудита; при смене updateData проверь, что множества копируются через Set.copyOf");
    }

    @Test
    void applyConstructionAudit_Coordinates_ReachManagerState() {
        TheMineSpiritClient.applyConstructionAudit(Set.of(new BlockPos(7, 64, 8)), Set.of(new BlockPos(9, 64, 10)));

        assertEquals(
                Set.of(new BlockPos(7, 64, 8)),
                manager.wrongBlocks(),
                "Заглушка пакета аудита обязана передавать блоки на снос в состояние подсветки: иначе серверный "
                        + "аудит не покажется в мире и подсветка будет пустой; при смене приёма пакета аудита проверь, что applyConstructionAudit зовёт updateData");
        assertEquals(
                Set.of(new BlockPos(9, 64, 10)),
                manager.missingBlocks(),
                "Заглушка пакета аудита обязана передавать отсутствующие блоки в состояние подсветки: иначе план "
                        + "постановки не покажется в мире и дух поставит блоки вслепую; при смене приёма пакета аудита проверь, что applyConstructionAudit зовёт updateData");
    }
}
