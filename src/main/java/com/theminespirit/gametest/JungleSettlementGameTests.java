package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardState;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.net.BoardViewChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Приёмка владельца: новое поселение в джунглях без дуба, три духа с пустым инвентарём на общей доске
 * проходят этап 1 сами — верстак, сундуки, инструменты, шахта, печь, факелы, расчистка, забор, дом — доска
 * показывает этап 2, и днём ни один дух не простаивает дольше {@link #IDLE_LIMIT} тиков.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true, coordinator = true)
public final class JungleSettlementGameTests {
    private static final int CENTER = 64;
    private static final int GROUND_HALF = 40;
    private static final int IDLE_LIMIT = 1200;
    private static final long NOON = 6000;
    private static final BlockPos HOME = new BlockPos(CENTER, 1, CENTER);

    private JungleSettlementGameTests() {}

    // [флак] приёмка этапа 1 с нуля: рубка, крафт, шахта, плавка, расчистка, забор и дом тремя духами — десятки
    // тысяч тиков; предел — потолок прогона пачки, а не замер (замера ещё нет: тест краснеет до доделки этапа 1).
    @GameTest(template = "settlement_arena", batch = "jungle_settlement", timeoutTicks = 24000)
    public static void jungle_EmptySettlementThreeSpirits_FirstStageDone(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.holdDayTime(helper, NOON);
        ground(helper);
        jungleTrees(helper);
        ServerLevel level = helper.getLevel();
        BoardService board = BoardRegistry.get(level);
        board.board().restore(new BoardState(List.of(), Set.of()));
        List<SpiritEntity> spirits = List.of(
                spirit(helper, HOME.offset(-2, 0, 0)), spirit(helper, HOME.offset(2, 0, 0)), spirit(helper, HOME));
        Map<UUID, Integer> idle = new HashMap<>();
        int[] worstIdle = {0};
        String[] worstWhen = {"-"};
        helper.onEachTick(() -> {
            for (SpiritEntity spirit : spirits) {
                boolean busy = spirit.taskStatus().orElse(null) == RunStatus.RUNNING
                        || spirit.tactics().wantsControl()
                        || !board.board().assignedTo(spirit.getUUID()).isEmpty();
                int ticks = busy ? 0 : idle.getOrDefault(spirit.getUUID(), 0) + 1;
                idle.put(spirit.getUUID(), ticks);
                if (ticks > worstIdle[0]) {
                    worstIdle[0] = ticks;
                    // Кто и когда простаивал дольше всех: без этого красный прогон не говорит, какой шаг держал духа.
                    worstWhen[0] = spirit.displayName() + " до тика " + helper.getTick() + ", открыто "
                            + board.board().all().stream()
                                    .filter(ticket -> board.board()
                                            .statusOf(ticket.id())
                                            .map(status -> status != BoardStatus.DONE)
                                            .orElse(false))
                                    .map(ticket -> ticket.id() + "="
                                            + board.board()
                                                    .statusOf(ticket.id())
                                                    .orElse(null))
                                    .toList();
                }
            }
        });

        helper.succeedWhen(() -> {
            List<BoardCase> missing = new ArrayList<>();
            for (BoardCase step : stageOne()) {
                if (!board.board().ledger().has(step, 1)) {
                    missing.add(step);
                }
            }
            // Переход на этап 2 — надпись этапа в снимке доски, тот же расчёт, что видит игрок.
            int stage = BoardViewChannel.stage(level).number();
            helper.assertTrue(
                    worstIdle[0] <= IDLE_LIMIT && missing.isEmpty() && stage >= 2,
                    "Этап 1 в джунглях: простой " + worstIdle[0] + " тиков подряд (" + worstWhen[0] + "; предел "
                            + IDLE_LIMIT
                            + "), нет " + missing + ", этап на доске " + stage + "; духи "
                            + spirits.stream()
                                    .map(spirit -> spirit.displayName() + "="
                                            + spirit.currentTaskScript().orElse("-"))
                                    .toList()
                            + "; доска "
                            + board.board().all().stream()
                                    .map(ticket -> ticket.id() + "="
                                            + board.board()
                                                    .statusOf(ticket.id())
                                                    .orElse(null))
                                    .toList());
            for (SpiritEntity spirit : spirits) {
                spirit.discard();
            }
            GameTestFixtures.finish(helper);
        });
    }

    private static List<BoardCase> stageOne() {
        List<BoardCase> steps = new ArrayList<>(List.of(BoardCase.CRAFTING_STATION, BoardCase.STOCK_CHESTS));
        steps.addAll(ToolSet.wooden());
        steps.add(BoardCase.BUILD_MINE);
        steps.addAll(ToolSet.stone());
        steps.addAll(List.of(
                BoardCase.CRAFT_FURNACE,
                BoardCase.LIGHTING,
                BoardCase.CLEAR_GROUNDS,
                BoardCase.BUILD_FENCE,
                BoardCase.BUILD_HOUSE));
        return steps;
    }

    private static void ground(GameTestHelper helper) {
        for (int x = CENTER - GROUND_HALF; x <= CENTER + GROUND_HALF; x++) {
            for (int z = CENTER - GROUND_HALF; z <= CENTER + GROUND_HALF; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
            }
        }
        for (int x = CENTER + 20; x < CENTER + 26; x++) {
            for (int z = CENTER - 3; z < CENTER + 3; z++) {
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        helper.setBlock(new BlockPos(CENTER - 22, 1, CENTER + 4), Blocks.COAL_ORE);
        helper.setBlock(new BlockPos(CENTER - 22, 2, CENTER + 4), Blocks.COAL_ORE);
    }

    private static void jungleTrees(GameTestHelper helper) {
        var leaves = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        int[][] spots = {
            {-10, -10},
            {-10, 0},
            {-10, 10},
            {0, -10},
            {0, 10},
            {10, -10},
            {10, 10},
            {-13, -5},
            {-13, 5},
            {5, -13},
            {-5, 13},
            {13, -5},
            {13, 5},
            {-5, -13},
            {5, 13},
            {-8, 13}
        };
        for (int[] spot : spots) {
            BlockPos base = new BlockPos(CENTER + spot[0], 1, CENTER + spot[1]);
            for (int y = 0; y < 7; y++) {
                helper.setBlock(base.above(y), Blocks.JUNGLE_LOG);
            }
            BlockPos top = base.above(6);
            helper.setBlock(top.above(), leaves);
            helper.setBlock(top.east(), leaves);
            helper.setBlock(top.west(), leaves);
            helper.setBlock(top.north(), leaves);
            helper.setBlock(top.south(), leaves);
        }
    }

    private static SpiritEntity spirit(GameTestHelper helper, BlockPos at) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), at);
        BlockPos home = helper.absolutePos(HOME);
        spirit.setHome(new BlockCoord(home.getX(), home.getY(), home.getZ()));
        return spirit;
    }
}
