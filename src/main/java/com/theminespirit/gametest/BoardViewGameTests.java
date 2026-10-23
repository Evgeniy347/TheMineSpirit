package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.ui.BoardTile;
import com.theminespirit.core.ui.BoardTileGroup;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.net.BoardViewChannel;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardViewGameTests {
    private static final String TICKET_ID = "board-view-probe";

    private BoardViewGameTests() {}

    @GameTest(template = "empty")
    public static void snapshot_TicketWithoutResources_ReachesClientAsUnavailableTile(GameTestHelper helper) {
        BoardService service = BoardRegistry.get(helper.getLevel());
        service.submit(new BoardTicket(
                TICKET_ID,
                BoardCase.CRAFT_ANVIL,
                Optional.empty(),
                10,
                helper.getLevel().getGameTime(),
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of("minecraft:iron_block", 999)));
        try {
            List<BoardTile> tiles = BoardViewChannel.snapshot(helper.getLevel());
            BoardTile tile = tiles.stream()
                    .filter(candidate -> candidate.id().equals(TICKET_ID))
                    .findFirst()
                    .orElse(null);
            helper.assertTrue(tile != null, "Снимок доски не содержит поставленное задание: экран J его не покажет");
            helper.assertTrue(
                    tile.group() == BoardTileGroup.UNAVAILABLE && tile.spirit() == null,
                    "Задание без ресурсов и исполнителя обязано быть недоступным и без духа, плитка " + tile);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            BoardViewChannel.Reply.STREAM_CODEC.encode(buf, new BoardViewChannel.Reply(tiles));
            BoardViewChannel.Reply decoded = BoardViewChannel.Reply.STREAM_CODEC.decode(buf);
            helper.assertTrue(
                    decoded.tiles().equals(tiles) && buf.readableBytes() == 0,
                    "Пакет снимка доски искажается при передаче: клиент увидит не ту доску, что на сервере");
        } finally {
            service.cancel(helper.getLevel(), TICKET_ID);
        }
        helper.succeed();
    }
}
