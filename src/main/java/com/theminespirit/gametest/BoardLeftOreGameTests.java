package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.mining.LeftOreRules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.MineOreLedger;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.BoardSources;
import com.theminespirit.neoforge.board.JournalService;
import com.theminespirit.neoforge.board.LeftOreWatch;
import com.theminespirit.neoforge.board.StockReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardLeftOreGameTests {
    private BoardLeftOreGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void leftOre_DiamondWaitsForFortunePick_ThenGoesToItsHolder(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        BlockPos ore = helper.absolutePos(new BlockPos(6, 1, 6));
        helper.setBlock(new BlockPos(6, 1, 6), Blocks.DIAMOND_ORE);
        BlockCoord orePos = new BlockCoord(ore.getX(), ore.getY(), ore.getZ());
        BlockPos stand = helper.absolutePos(new BlockPos(5, 1, 6));
        MineOreLedger ledger = MineOreLedger.get(level);
        ledger.remember(orePos, "minecraft:diamond_ore", new BlockCoord(stand.getX(), stand.getY(), stand.getZ()));
        BoardService board = isolatedBoard(level);
        String id = LeftOreWatch.id(orePos);

        LeftOreWatch.tick(level, board, List.of(spirit), level.getGameTime());
        boolean waiting = board.board().statusOf(id).isPresent()
                && board.board().claimable(new StockView(Map.of("iron_pickaxe", 1)), level.getGameTime()).stream()
                        .noneMatch(ticket -> ticket.id().equals(id));

        ItemStack lucky = new ItemStack(Items.IRON_PICKAXE);
        lucky.enchant(
                level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FORTUNE),
                3);
        spirit.getInventory().setItem(1, lucky);
        board.board()
                .retain(LeftOreWatch.tick(level, board, List.of(spirit), level.getGameTime()), level.getGameTime());
        String held = id + "@" + spirit.getUUID();
        Optional<BoardTicket> ticket =
                board
                        .board()
                        .claimable(new StockView(Map.of(LeftOreRules.LUCK_PICKAXE, 1)), level.getGameTime())
                        .stream()
                        .filter(found -> found.id().equals(held))
                        .findFirst();
        boolean open = board.board().statusOf(id).orElse(null) == BoardStatus.CANCELLED;
        ledger.forget(orePos);

        helper.assertTrue(
                waiting,
                "без кирки с Удачей задание оставленного алмаза висит на доске, но не выдаётся (владелец 23.10)");
        helper.assertTrue(
                ticket.isPresent() && ticket.get().assignee().equals(Optional.of(spirit.getUUID())) && open,
                "кирка с Удачей появилась у духа — задание открывается и уходит ему, ничьё снимается, а не достаётся "
                        + "духу без Удачи; задание " + ticket + ", ничьё снято " + open);
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static BoardService isolatedBoard(ServerLevel level) {
        Board board = new Board(level::getGameTime);
        Path journalPath = FMLPaths.CONFIGDIR.get().resolve("theminespirit/gametest-left-ore.jsonl");
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
