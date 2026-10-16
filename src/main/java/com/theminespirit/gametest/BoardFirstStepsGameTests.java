package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.FirstStepsRunner;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Первые шаги на доске: новое поселение → CLEAR_GROUNDS; дальше задания идут строго по цепочке,
 * каждое следующее — после выполнения предыдущего; выполненная цепочка новых заданий не плодит.
 * Всё синхронно: генератор — чистая функция от фактов мира, тело теста атомарно.
 * Доска общая на уровень: чужие first:* закрываем в начале, тени DONE снимаем prune напрямую
 * (без rebuild — чужая пересборка раздавала бы задания духам других сцен).
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardFirstStepsGameTests {
    private static final String BATCH = "board_first_steps";
    // Геометрия обзора FirstStepsRunner.buildFacts: радиус и высоты обязаны совпадать,
    // иначе диагностика «чужие деревья» увидит не то, что видит генератор.
    private static final int SCAN_RADIUS = 16;

    private BoardFirstStepsGameTests() {
    }

    @BeforeBatch(batch = BATCH)
    public static void stopMobsTargetingSpirits(ServerLevel level) {
        SpiritConfig.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = BATCH)
    public static void restoreMobTargeting(ServerLevel level) {
        SpiritConfig.setMobsTargetSpirits(true);
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 100)
    public static void firstSteps_NewSettlement_ChainInOrderWithoutDuplicates(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.setHome(absolute(helper, new BlockPos(4, 1, 4)));
        ServerLevel level = helper.getLevel();
        BoardService board = BoardRegistry.get(level);
        placeLogs(helper, false);
        int prevIndex = -1;
        BoardCase lastCase = null;
        int streak = 0;
        boolean foreignTrees = false;

        // Чужие шаги из общей доски (поставлены глобальным тиком для других сцен) закрываем
        // помечая выполненными: иначе активных больше одного и тест видит чужую работу.
        for (BoardTicket ticket : board.board().all()) {
            if (ticket.id().startsWith("first:")) {
                closeTicket(board, ticket.id(), spirit);
            }
        }

        for (int step = 0; step < 20 && !foreignTrees; step++) {
            long now = level.getGameTime();
            FirstStepsRunner.tick(level, board, List.of(spirit), now);
            List<BoardTicket> active = board.board().all().stream()
                    .filter(ticket -> ticket.id().startsWith("first:"))
                    .filter(ticket -> {
                        BoardStatus status = board.board().statusOf(ticket.id()).orElse(null);
                        return status == BoardStatus.PENDING
                                || status == BoardStatus.WAITING_RESOURCES
                                || status == BoardStatus.ASSIGNED;
                    })
                    .toList();
            helper.assertTrue(active.size() <= 1,
                    "Задание первых шагов всегда одно: активных " + active.size()
                            + ": иначе поселение строится вразнобой");
            if (active.isEmpty()) {
                if (lastCase != null && board.board()
                        .statusOf("first:" + lastCase.name().toLowerCase(Locale.ROOT))
                        .orElse(null) == BoardStatus.DONE) {
                    // Тень DONE мешает переизданию того же шага: снимаем напрямую.
                    board.board().prune(board.board().now());
                    continue;
                }
                break;
            }
            BoardTicket head = active.get(0);
            if (head.boardCase() == lastCase) {
                streak++;
                // Факты не продвинулись: переустанавливаем маркеры идемпотентно и пробуем ещё.
                satisfy(helper, board, spirit, head.boardCase());
                board.board().prune(board.board().now());
                if (streak >= 2) {
                    if (head.boardCase() == BoardCase.CLEAR_GROUNDS
                            && foreignTreesPresent(helper)) {
                        // Чужие деревья в обзоре держат землю не зачищенной вечно:
                        // генератор прав, дальше цепочке идти некуда — выходим через
                        // зафиксированный факт, а не через пропуск шагов.
                        foreignTrees = true;
                        break;
                    }
                    helper.assertTrue(false,
                            "Шаг " + head.boardCase() + " повторяется в третий раз, маркеры целы: "
                                    + "генератор топчется на месте");
                }
                continue;
            }
            streak = 0;
            int index = chainIndex(head.boardCase());
            helper.assertTrue(index > prevIndex,
                    "Следующий шаг " + head.boardCase() + " идёт после предыдущего (индекс "
                            + prevIndex + "): цепочка не идёт назад и не топчется на месте");
            satisfy(helper, board, spirit, head.boardCase());
            closeTicket(board, head.id(), spirit);
            prevIndex = index;
            lastCase = head.boardCase();
        }
        if (!foreignTrees) {
            helper.assertTrue(prevIndex >= chainIndex(BoardCase.BUILD_MINE),
                    "Цепочка сошлась не раньше шахты: последний шаг " + prevIndex
                            + ": ранний конец означает пропуск шагов, а не выполненную цепочку");
        }
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    /**
     * Чужие деревья в обзоре поселения: мои клетки уже пусты, любой найденный ствол/листва —
     * соседняя сцена. Геометрия обязана повторять обзор генератора.
     */
    private static boolean foreignTreesPresent(GameTestHelper helper) {
        BlockPos home = helper.absolutePos(new BlockPos(4, 1, 4));
        ServerLevel level = helper.getLevel();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                if (dx * dx + dz * dz > SCAN_RADIUS * SCAN_RADIUS) {
                    continue;
                }
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos check = home.offset(dx, dy, dz);
                    if (!level.isLoaded(check)) {
                        continue;
                    }
                    var state = level.getBlockState(check);
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int chainIndex(BoardCase boardCase) {
        return switch (boardCase) {
            case CLEAR_GROUNDS -> 0;
            case STOCK_CHESTS -> 1;
            case CRAFTING_STATION -> 2;
            case CRAFT_TOOLS -> 3;
            case BUILD_MINE -> 4;
            case BUILD_FENCE -> 5;
            case LIGHTING -> 6;
            case BUILD_HOUSE -> 7;
            default -> throw new IllegalArgumentException("Не шаг цепочки: " + boardCase);
        };
    }

    private static void satisfy(GameTestHelper helper, BoardService board, SpiritEntity spirit,
            BoardCase head) {
        switch (head) {
            case CLEAR_GROUNDS -> placeLogs(helper, true);
            case STOCK_CHESTS -> {
                helper.setBlock(new BlockPos(6, 1, 2), Blocks.CHEST);
                helper.setBlock(new BlockPos(7, 1, 2), Blocks.CHEST);
            }
            case CRAFTING_STATION -> helper.setBlock(new BlockPos(2, 1, 6), Blocks.CRAFTING_TABLE);
            case CRAFT_TOOLS -> {
                spirit.getInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE, 1));
                ServerLevel level = helper.getLevel();
                board.rebuild(level, level.getGameTime());
            }
            case BUILD_MINE -> {
                helper.setBlock(new BlockPos(6, 1, 6), Blocks.COBBLESTONE);
                helper.setBlock(new BlockPos(6, 2, 6), Blocks.COBBLESTONE);
                helper.setBlock(new BlockPos(6, 1, 5), Blocks.LADDER);
            }
            case BUILD_FENCE -> helper.setBlock(new BlockPos(3, 1, 7), Blocks.OAK_FENCE);
            case LIGHTING -> {
                helper.setBlock(new BlockPos(1, 1, 7), Blocks.TORCH);
                helper.setBlock(new BlockPos(7, 1, 7), Blocks.TORCH);
                helper.setBlock(new BlockPos(1, 1, 3), Blocks.TORCH);
                helper.setBlock(new BlockPos(7, 1, 3), Blocks.TORCH);
            }
            case BUILD_HOUSE -> helper.setBlock(new BlockPos(4, 1, 7), Blocks.OAK_STAIRS);
            default -> throw new IllegalArgumentException("Не шаг цепочки: " + head);
        }
    }

    private static void placeLogs(GameTestHelper helper, boolean clear) {
        BlockPos[] trees = {new BlockPos(2, 1, 2), new BlockPos(3, 1, 2), new BlockPos(2, 1, 3)};
        for (BlockPos tree : trees) {
            helper.setBlock(tree, clear ? Blocks.AIR : Blocks.OAK_LOG);
        }
    }

    private static void closeTicket(BoardService board, String id, SpiritEntity spirit) {
        long now = board.board().now();
        if (board.board().statusOf(id).orElse(null) == BoardStatus.PENDING) {
            board.board().claim(id, spirit.getUUID(), now);
        }
        if (board.board().statusOf(id).orElse(null) == BoardStatus.ASSIGNED) {
            board.board().complete(id);
        } else if (board.board().statusOf(id).orElse(null) == BoardStatus.WAITING_RESOURCES) {
            board.board().cancel(id);
        }
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
