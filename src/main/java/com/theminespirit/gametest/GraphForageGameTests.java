package com.theminespirit.gametest;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.BoardSources;
import com.theminespirit.neoforge.board.GraphSurroundings;
import com.theminespirit.neoforge.board.JournalService;
import com.theminespirit.neoforge.board.StockReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.neoforged.fml.loading.FMLPaths;

public final class GraphForageGameTests {
    private static final Set<String> EXPECTED = Set.of(
            "flower:blue_orchid",
            "flower:lilac",
            "mushrooms",
            "bamboo_jungle",
            "ocean",
            "biome:jungle",
            "mangrove_swamp",
            "rabbits",
            "glow_squid",
            "armadillo");

    private GraphForageGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void surroundings_ForageBlocksAndMobs_MarkedNearHome(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        for (int x = 4; x <= 11; x++) {
            helper.setBlock(new BlockPos(x, 1, 4), Blocks.DIRT);
        }
        helper.setBlock(new BlockPos(6, 1, 4), Blocks.PODZOL);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.BLUE_ORCHID);
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.LILAC);
        helper.setBlock(new BlockPos(6, 2, 4), Blocks.BROWN_MUSHROOM);
        helper.setBlock(new BlockPos(7, 2, 4), Blocks.BAMBOO);
        helper.setBlock(new BlockPos(8, 2, 4), Blocks.KELP);
        helper.setBlock(new BlockPos(9, 2, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(9, 2, 4), Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true));
        helper.setBlock(new BlockPos(10, 2, 4), Blocks.MUD);
        helper.setBlock(new BlockPos(11, 2, 4), Blocks.MANGROVE_ROOTS);
        helper.spawn(EntityType.RABBIT, new BlockPos(4, 2, 6));
        helper.spawn(EntityType.GLOW_SQUID, new BlockPos(5, 2, 6));
        helper.spawn(EntityType.ARMADILLO, new BlockPos(6, 2, 6));
        BlockPos home = helper.absolutePos(new BlockPos(7, 2, 5));

        GraphSurroundings.Around around = GraphSurroundings.of(
                helper.getLevel(),
                isolatedBoard(helper.getLevel(), "gametest-graph-forage.jsonl"),
                new BlockCoord(home.getX(), home.getY(), home.getZ()));

        helper.assertTrue(
                around.world().containsAll(EXPECTED),
                "Сбор в природе узнаёт округу: цветы, грибы, бамбук, ламинарию, лианы, грязь и корни мангров — по "
                        + "блокам, кроликов, светящихся кальмаров и броненосцев — по сущностям. Нет меток: "
                        + EXPECTED.stream()
                                .filter(tag -> !around.world().contains(tag))
                                .toList()
                        + ", вся округа " + around.world());
        helper.assertTrue(
                around.resource("flower:blue_orchid").isPresent()
                        && around.resource("mangrove_swamp").isPresent(),
                "У метки мира есть ближайшая точка: без неё дух не знает, куда идти за ресурсом");
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void brush_AdultArmadilloWithBrush_ScuteInInventory(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.spawnStill(helper, EntityType.ARMADILLO, new BlockPos(3, 1, 3));
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, new BlockPos(1, 1, 1), new ItemStack(Items.BRUSH));
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("brush", Map.of("radius", 8, "count", 1)), 5);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertSucceeded(helper, spirit, "взрослый броненосец со щёткой обязан отдать щетину");
            int scute = GameTestFixtures.countInInventory(spirit.getInventory(), Items.ARMADILLO_SCUTE);
            helper.assertTrue(scute >= 1, "в инвентаре духа обязана появиться щетина, фактически " + scute);
            helper.assertTrue(
                    HusbandryGameTests.alive(helper, Armadillo.class).size() == 1,
                    "броненосец обязан остаться живым: щётка не убивает");
            GameTestFixtures.finish(helper);
        });
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
