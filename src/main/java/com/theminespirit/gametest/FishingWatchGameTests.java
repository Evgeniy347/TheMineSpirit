package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.observation.BlockCoord;
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
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Постоянные задания рыболовства: коптильня встаёт на доску при улове не меньше порога,
 * рыбалка — при нехватке еды и найденной рабочей точке у воды. Доска своя, изолированная:
 * общая доска уровня приносит чужие билеты соседних сцен.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true)
public final class FishingWatchGameTests {
    private static final String BATCH = "fishing_watch";

    private FishingWatchGameTests() {}

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 100)
    public static void fishingWatch_FishAtThreshold_PostsSmokerCraft(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));
        spirit.setHome(coord(helper, new BlockPos(4, 1, 4)));
        ServerLevel level = helper.getLevel();
        BoardSources sources = BoardSources.create();
        BoardService service = isolatedBoard(level, sources, "gametest-fishing-watch.jsonl");
        long now = level.getGameTime();

        sources.tick(
                level, service, List.of(spirit), new StockView(Map.of("cod", 16, "furnace", 1, "oak_planks", 4)), now);
        helper.assertTrue(
                service.board().statusOf("fish:smoker").isPresent(),
                "С 16 трески на складе доска обязана заказать коптильню, статус "
                        + service.board().statusOf("fish:smoker"));

        sources.tick(
                level,
                service,
                List.of(spirit),
                new StockView(Map.of("cod", 4, "furnace", 1, "oak_planks", 4)),
                now + 1);
        helper.assertTrue(
                service.board().statusOf("fish:smoker").isEmpty()
                        || service.board().statusOf("fish:smoker").orElseThrow() == BoardStatus.CANCELLED,
                "Ниже порога коптильня с доски снимается, статус "
                        + service.board().statusOf("fish:smoker"));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 100)
    public static void fishingWatch_NoFoodNearWater_PostsWorkPoint(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        deepPond(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(0, 1, 0));
        spirit.setHome(coord(helper, new BlockPos(0, 1, 0)));
        ServerLevel level = helper.getLevel();
        BoardSources sources = BoardSources.create();
        BoardService service = isolatedBoard(level, sources, "gametest-fishing-work.jsonl");

        sources.tick(level, service, List.of(spirit), new StockView(Map.of("furnace", 1)), level.getGameTime());
        helper.assertTrue(
                service.board().statusOf("fish:work").isPresent(),
                "Без еды у открытой воды доска обязана заказать рыбалку, статус "
                        + service.board().statusOf("fish:work"));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static void deepPond(GameTestHelper helper) {
        for (int x = 2; x <= 8; x++) {
            for (int z = 2; z <= 8; z++) {
                helper.setBlock(x, 0, z, Blocks.WATER);
                helper.setBlock(x, 1, z, Blocks.WATER);
            }
        }
    }

    private static BlockCoord coord(GameTestHelper helper, BlockPos local) {
        BlockPos absolute = helper.absolutePos(local);
        return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
    }

    private static BoardService isolatedBoard(ServerLevel level, BoardSources sources, String journalFile) {
        Board board = new Board(level::getGameTime);
        Path journalPath = FMLPaths.CONFIGDIR.get().resolve("theminespirit/" + journalFile);
        try {
            Files.deleteIfExists(journalPath);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        JournalService journal = new JournalService(journalPath);
        return new BoardService(
                board,
                new HerdCycles(board),
                new StockReader(BoardTuning.stockReadBudget(), BoardTuning.stockBlockBudget()),
                sources,
                journal);
    }
}
