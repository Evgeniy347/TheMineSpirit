package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrafterMemoryTest {
    private static final UUID GIVER = UUID.fromString("a7d7052e-eb7f-48cb-9458-bd5ab1aed762");
    private static final UUID CRAFTER = UUID.fromString("a9b9f24c-6b8d-477f-bf5d-ee928dac3a1b");
    private static final String FURNACE = "first:craft_furnace";

    @Test
    void choose_GiverNearerAfterTransfer_KeepsCrafter() {
        CrafterMemory memory = new CrafterMemory();
        memory.remember(FURNACE, CRAFTER);
        UUID chosen = memory.choose(FURNACE, List.of(GIVER, CRAFTER), id -> id, id -> true, () -> GIVER);
        assertEquals(
                CRAFTER,
                chosen,
                "получивший булыжник крафтер остаётся крафтером, иначе передача идёт обратно по кругу (24.10)");
    }

    @Test
    void choose_CrafterBusyOrGone_FallsBack() {
        CrafterMemory memory = new CrafterMemory();
        memory.remember(FURNACE, CRAFTER);
        UUID busy = memory.choose(FURNACE, List.of(GIVER, CRAFTER), id -> id, GIVER::equals, () -> GIVER);
        assertEquals(GIVER, busy, "занятый крафтер не держит крафт: выбор по умолчанию");
        UUID gone = memory.choose(FURNACE, List.of(GIVER), id -> id, id -> true, () -> GIVER);
        assertEquals(GIVER, gone, "ушедший крафтер не держит крафт: выбор по умолчанию");
    }

    @Test
    void retain_ClosedTicket_Forgotten() {
        CrafterMemory memory = new CrafterMemory();
        memory.remember(FURNACE, CRAFTER);
        memory.retain(id -> false);
        assertEquals(0, memory.size(), "закрытое задание не держит память крафтера: карта не течёт");
    }
}
