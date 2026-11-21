package com.theminespirit.neoforge.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.ui.BoardTile;
import com.theminespirit.core.ui.api.BoardTileGroup;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class BoardTileCodecTests {
    @Test
    void writeRead_NonEmptyRequiredItems_KeepsValuesAndDetails() {
        BoardTile repair = new BoardTile(
                "repair:hut",
                "REPAIR_BUILDING",
                1,
                90,
                "Дух-1",
                "PENDING",
                BoardTileGroup.AVAILABLE,
                Map.of("minecraft:cobblestone", 3),
                List.of("board-0"),
                0,
                120,
                0,
                0,
                null,
                0,
                List.of("target=1,2,3", "building=Хижина"),
                Map.of("minecraft:dirt", 5, "minecraft:cobblestone", 8),
                Map.of("minecraft:dirt", 10240, "minecraft:cobblestone", 4));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BoardTileCodec.write(buf, List.of(repair, plain()));
            assertEquals(
                    List.of(repair, plain()),
                    BoardTileCodec.read(buf),
                    "Плитка обязана пережить пакет доски: иначе клиент не увидит, что нужно для постройки"
                            + " При смене writeRead_NonEmptyRequiredItems_KeepsValuesAndDetails проверь это утверждение.");
        } finally {
            buf.release();
        }
    }

    private static BoardTile plain() {
        return new BoardTile(
                "board-2",
                "BUILD_HOUSE",
                2,
                10,
                null,
                "PENDING",
                BoardTileGroup.WAITING,
                Map.of(),
                List.of(),
                0,
                0,
                0,
                0,
                "minecraft:oak_log",
                4);
    }
}
