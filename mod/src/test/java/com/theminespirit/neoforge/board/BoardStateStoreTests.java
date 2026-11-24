package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class BoardStateStoreTests {
    private static CompoundTag ticket(String id, String boardCase) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Case", boardCase);
        tag.putInt("Priority", 50);
        tag.putLong("CreatedAt", 0);
        tag.putInt("Level", 1);
        tag.putString("Cause", "always");
        tag.putString("Status", "PENDING");
        tag.putInt("Failures", 0);
        return tag;
    }

    private static CompoundTag pause(String boardCase) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Spirit", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        tag.putString("Case", boardCase);
        tag.putInt("Consecutive", 1);
        tag.putLong("PausedUntil", 0);
        tag.putBoolean("Detached", false);
        return tag;
    }

    @Test
    void load_RetiredCaseInTicketAndPause_SkipsBothAndKeepsLiveTicket() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 4);
        ListTag entries = new ListTag();
        entries.add(ticket("undo:old", "ACTIVITY_UNDO"));
        entries.add(ticket("first:stock_chests", "STOCK_CHESTS"));
        tag.put("Entries", entries);
        ListTag pauses = new ListTag();
        pauses.add(pause("ACTIVITY_UNDO"));
        tag.put("Pauses", pauses);

        BoardStateStore store = BoardStateStore.load(tag, RegistryAccess.EMPTY);

        assertEquals(
                List.of("first:stock_chests"),
                store.state().entries().stream()
                        .map(entry -> entry.ticket().id())
                        .toList(),
                "Билет удалённого кейса не читается, а живой билет рядом переживает загрузку: иначе старое "
                        + "состояние доски роняет сервер и доски нет вовсе"
                        + " Иначе тест load_RetiredCaseInTicketAndPause_SkipsBothAndKeepsLiveTicket перестанет ловить эту поломку. "
                        + "При смене load_RetiredCaseInTicketAndPause_SkipsBothAndKeepsLiveTicket проверь это утверждение.");
        assertTrue(
                store.state().pauses().isEmpty(),
                "Пауза удалённого кейса пропускается: иначе BoardCase.valueOf роняет чтение состояния"
                        + " Иначе тест load_RetiredCaseInTicketAndPause_SkipsBothAndKeepsLiveTicket перестанет ловить эту поломку. "
                        + "При смене load_RetiredCaseInTicketAndPause_SkipsBothAndKeepsLiveTicket проверь это утверждение.");
    }
}
