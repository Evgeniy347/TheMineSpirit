package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.BoardSources;
import com.theminespirit.neoforge.board.JournalService;
import com.theminespirit.neoforge.board.StockReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Блокеры живого прогона 2: выдача доски при паузе своего задания (INC-23).
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true)
public final class BoardDispatchGameTests {
    private static final String OWN = "dispatch:own";
    private static final String FREE = "dispatch:free";
    private static final BoardCase PAUSED_CASE = BoardCase.STOCK_CHESTS;

    private BoardDispatchGameTests() {}

    @GameTest(template = "arena", batch = "board_dispatch_paused_holder", timeoutTicks = 100)
    public static void dispatch_OwnTicketPaused_SpiritTakesFreeTicket(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-dispatch-paused.jsonl");
        long now = board.board().now();
        String failed = "dispatch:failed";
        board.submit(ticket(failed, PAUSED_CASE, Optional.empty(), now), at(helper));
        board.board().claim(failed, spirit.getUUID(), now);
        board.board().fail(failed);
        helper.assertTrue(
                !board.board().availableTo(spirit.getUUID(), PAUSED_CASE, now),
                "Провал ставит паузу кейса духу: без неё сцена не воспроизводит INC-23");
        board.submit(ticket(OWN, PAUSED_CASE, Optional.of(spirit.getUUID()), now), at(helper));
        board.submit(
                ticket(FREE, BoardCase.GATHER_MATERIALS, Optional.empty(), now),
                at(helper).withSupply("dirt", 1));

        board.rebuild(level, level.getGameTime());

        helper.assertTrue(
                board.board().holderOf(FREE).filter(spirit.getUUID()::equals).isPresent(),
                "Дух на паузе по своему заданию " + OWN + " берёт свободное " + FREE + ": держатель "
                        + board.board().holderOf(FREE)
                        + " — иначе все духи простаивают, пока их тикеты ремонта висят PENDING (INC-23)");
        spirit.discard();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static TicketTarget at(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        return TicketTarget.at(new BlockCoord(at.getX(), at.getY(), at.getZ()));
    }

    private static BoardTicket ticket(String id, BoardCase boardCase, Optional<UUID> assignee, long now) {
        return new BoardTicket(
                id,
                boardCase,
                assignee,
                BoardRules.priorityOf(boardCase),
                now,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    private static BoardService isolatedBoard(ServerLevel level, String journalFile) {
        Board board = new Board(level::getGameTime);
        Path journalPath = FMLPaths.CONFIGDIR.get().resolve("theminespirit/" + journalFile);
        try {
            Files.deleteIfExists(journalPath);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return new BoardService(
                board,
                new HerdCycles(board),
                new StockReader(BoardTuning.stockReadBudget(), BoardTuning.stockBlockBudget()),
                BoardSources.create(),
                new JournalService(journalPath));
    }
}
