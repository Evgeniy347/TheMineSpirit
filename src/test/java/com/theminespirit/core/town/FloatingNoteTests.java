package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class FloatingNoteTests {
    @Test
    void line_FindingsWithTypes_ListsCountTypeAndFirstCells() {
        List<FloatingBlocks.Finding> findings = List.of(
                new FloatingBlocks.Finding(
                        new BlockCoord(-183, 70, -486),
                        FloatingBlocks.Kind.TREE,
                        "minecraft:oak_log",
                        0,
                        "часть висящего дерева (ствол или листва), вне плана",
                        List.of(new BlockCoord(-183, 70, -486), new BlockCoord(-183, 71, -486))),
                new FloatingBlocks.Finding(
                        new BlockCoord(-181, 70, -486),
                        FloatingBlocks.Kind.OTHER,
                        "minecraft:oak_leaves",
                        0,
                        "висит на высоте 0 над землёй, вне плана"));

        String line = FloatingNote.line(findings);

        assertTrue(line.startsWith("2 ("), "Строка начинается с числа находок: " + line);
        assertTrue(
                line.contains("minecraft:oak_log=2"),
                "Тип и число блоков видны: листва не путается с бревном: " + line);
        assertTrue(line.contains("minecraft:oak_leaves=1"), "Каждый тип в строке: " + line);
        assertTrue(line.contains("BlockCoord[x=-183"), "Первые клетки названы: по ним идут убирать: " + line);
    }

    @Test
    void line_NoFindings_IsZero() {
        assertEquals("0", FloatingNote.line(List.of()), "Пустой обход не притворяется работой: ноль без скобок");
    }
}
