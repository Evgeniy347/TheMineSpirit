package com.theminespirit.core.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.nio.file.Path;
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
                        + "файл открывается на любой системе"
                        + " Иначе тест fileName_SpiritWithSpaces_StampedSafeName перестанет ловить эту поломку. При смене fileName_SpiritWithSpaces_StampedSafeName проверь это утверждение.");
    }

    @Test
    void origin_SpiritAtPosition_SnapshotCenteredOnSpirit() {
        IncidentNote note = new IncidentNote("spirit-1", "застрял", new BlockCoord(-165, 64, -489), "-", Map.of());
        assertEquals(
                new BlockCoord(-177, 52, -501),
                note.origin(),
                "Снимок ±12 вокруг духа: угол — позиция минус 12, иначе загруженная сцена сдвинута от места беды"
                        + " При смене origin_SpiritAtPosition_SnapshotCenteredOnSpirit проверь это утверждение.");
        assertEquals(
                new BlockCoord(12, 12, 12),
                IncidentNote.spiritOffset(),
                "Дух в снимке — в его центре: тест ставит духа туда же, где он был"
                        + " Иначе тест origin_SpiritAtPosition_SnapshotCenteredOnSpirit перестанет ловить эту поломку. При смене origin_SpiritAtPosition_SnapshotCenteredOnSpirit проверь это утверждение.");
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
                        + "инцидент не повторить в тесте"
                        + " Иначе тест line_TaskAndInventory_AllForRepeat перестанет ловить эту поломку. При смене line_TaskAndInventory_AllForRepeat проверь это утверждение.");
    }

    @Test
    void brief_WhatAlreadyLoggedOutside_PrintsPlaceTaskInventoryAndSnapshot() {
        IncidentNote note = new IncidentNote(
                "spirit-3", "демо:4 (кейс) провалено", new BlockCoord(1, 2, 3), "clear_area", Map.of("dirt", 12));

        String brief = note.brief(Path.of("a.nbt"));

        assertEquals(
                "дух spirit-3 в (1, 2, 3): задача clear_area; инвентарь {dirt=12}; снимок a.nbt",
                brief,
                "Инцидент не повторяет задание и причину, уже названные строкой лога: дубль раздувал запись "
                        + "до килобайтов, но место, ход задачи и снимок остаются для разбора"
                        + " Иначе тест brief_WhatAlreadyLoggedOutside_PrintsPlaceTaskInventoryAndSnapshot перестанет ловить эту поломку. При смене brief_WhatAlreadyLoggedOutside_PrintsPlaceTaskInventoryAndSnapshot проверь это утверждение.");
    }

    @Test
    void line_SnapshotPath_IsAppended() {
        IncidentNote note = new IncidentNote(
                "spirit-1", "задание стоит 1200 тиков", new BlockCoord(-162, 72, -505), "demolish:1", Map.of());
        assertEquals(
                note.line() + "; снимок a.nbt",
                note.line(Path.of("a.nbt")),
                "Строка [incident] обязана нести путь NBT-снимка: без него инцидент не найти среди файлов"
                        + " Иначе тест line_SnapshotPath_IsAppended перестанет ловить эту поломку. При смене line_SnapshotPath_IsAppended проверь это утверждение.");
    }
}
