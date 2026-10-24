package com.theminespirit.core.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IncidentNoteTests {
    @Test
    void fileName_SpiritWithSpaces_StampedSafeName() {
        assertEquals(
                "20261024-103015-250-spirit_2.nbt",
                IncidentNote.fileName(Instant.parse("2026-10-24T10:30:15.250Z"), "spirit 2"),
                "Имя снимка — время UTC до миллисекунд и дух без пробелов: два инцидента не затирают друг друга, "
                        + "файл открывается на любой системе");
    }

    @Test
    void origin_SpiritAtPosition_SnapshotCenteredOnSpirit() {
        IncidentNote note = new IncidentNote("spirit-1", "застрял", new BlockCoord(-165, 64, -489), "-", Map.of());
        assertEquals(
                new BlockCoord(-177, 52, -501),
                note.origin(),
                "Снимок ±12 вокруг духа: угол — позиция минус 12, иначе загруженная сцена сдвинута от места беды");
        assertEquals(
                new BlockCoord(12, 12, 12),
                IncidentNote.spiritOffset(),
                "Дух в снимке — в его центре: тест ставит духа туда же, где он был");
    }

    @Test
    void line_TaskAndInventory_AllForRepeat() {
        IncidentNote note = new IncidentNote(
                "spirit-3",
                "clear:4 провалено",
                new BlockCoord(1, 2, 3),
                "clear_area {x1=0}",
                Map.of("stone_pickaxe", 1, "dirt", 12));
        assertEquals(
                "дух spirit-3 в (1, 2, 3): clear:4 провалено; задача clear_area {x1=0}; инвентарь {dirt=12, "
                        + "stone_pickaxe=1}",
                note.line(),
                "Строка инцидента называет духа, позицию, задачу с параметрами и инвентарь по алфавиту: без них "
                        + "инцидент не повторить в тесте");
    }
}
