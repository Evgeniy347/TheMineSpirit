package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardState;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DigOutGameTests {
    private static final int TOP = 12;
    private static final int POCKET = TOP - 7;

    private DigOutGameTests() {}

    // [флак] 7 ступеней по 3 блока камня деревянной киркой (~70 тиков на ступень) и шаги; запас 2× к ~1000 тиков
    @SceneStep(template = "mine_arena", timeoutTicks = 2400, board = true)
    public static void digOut_SealedPocketSevenDeep_ReachesSurface(GameTestHelper helper) {
        pocket(
                helper,
                List.of(
                        List.of(new BlockPos(2, POCKET, 5), new BlockPos(2, POCKET + 1, 5)),
                        List.of(new BlockPos(5, POCKET, 5), new BlockPos(5, POCKET + 1, 5))));
    }

    private static void pocket(GameTestHelper helper, List<List<BlockPos>> pockets) {
        GameTestFixtures.reset(helper, 16);
        BoardService board = BoardRegistry.get(helper.getLevel());
        board.board().restore(new BoardState(List.of(), Set.of()));
        for (int x = 0; x <= 10; x++) {
            for (int y = 1; y <= TOP; y++) {
                for (int z = 0; z <= 10; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        pockets.forEach(cells -> cells.forEach(cell -> helper.setBlock(cell, Blocks.AIR)));
        List<BlockPos> planks = new ArrayList<>();
        for (List<BlockPos> cells : pockets) {
            BlockPos plank = cells.get(0).above(2);
            helper.setBlock(plank, Blocks.JUNGLE_PLANKS);
            planks.add(plank);
        }
        List<SpiritEntity> spirits = new ArrayList<>();
        List<String> tickets = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        for (List<BlockPos> cells : pockets) {
            BlockPos feetRelative = cells.get(0);
            SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), feetRelative);
            spirit.getInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
            spirit.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 48));
            BlockPos home = helper.absolutePos(new BlockPos(8, TOP + 1, 5));
            spirit.setHome(new BlockCoord(home.getX(), home.getY(), home.getZ()));
            BlockPos feet = spirit.blockPosition();
            BlockCoord at = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
            BlockCoord goal = new BlockCoord(home.getX(), home.getY(), home.getZ());
            UUID id = spirit.getUUID();
            long now = helper.getLevel().getGameTime();
            String ticket = "rescue:" + id;
            board.board().stuck().see(id, at, now);
            board.board().stuck().mark(id, now, "сцена: дух замурован в яме");
            TaskNode plan = new ScriptCall("dig_out", Map.of("x", goal.x(), "y", goal.y(), "z", goal.z()));
            board.submit(
                    new BoardTicket(
                            ticket,
                            BoardCase.RESCUE_SPIRIT,
                            Optional.of(id),
                            BoardRules.priorityOf(BoardCase.RESCUE_SPIRIT),
                            now,
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            Map.of()),
                    TicketTarget.at(at).withPlan(plan));
            spirits.add(spirit);
            tickets.add(ticket);
            starts.add(feet.getY());
        }

        helper.succeedWhen(() -> {
            for (int index = 0; index < spirits.size(); index++) {
                SpiritEntity spirit = spirits.get(index);
                String ticket = tickets.get(index);
                int startY = starts.get(index);
                BlockPos current = spirit.blockPosition();
                helper.assertTrue(
                        DigOutScript.open(helper.getLevel(), current) || current.getY() >= startY + 3,
                        "замурованный дух обязан выбраться из кармана (INC-45): стоит в " + current + ", старт "
                                + startY + ", задача "
                                + spirit.currentTaskScript().orElse("-") + " "
                                + spirit.taskStatus().orElse(null)
                                + ", причина " + spirit.taskFailureReason().orElse("нет"));
                helper.assertTrue(
                        board.board().statusOf(ticket).orElse(null) == BoardStatus.DONE,
                        "выход закрывается заданием доски, статус "
                                + board.board().statusOf(ticket).orElse(null));
                helper.assertFalse(
                        board.board()
                                .stuck()
                                .isStuck(spirit.getUUID(), board.board().now()),
                        "выполненный выход снимает метку «не двигается» по факту, а не по тайм-ауту");
                helper.assertTrue(
                        helper.getBlockState(planks.get(index)).is(Blocks.JUNGLE_PLANKS),
                        "Разбор при выходе временный: сломанная доска плана обязана вернуться на место после "
                                + "спасения, иначе в постройке остаётся дыра (observe25 B2); в клетке "
                                + planks.get(index) + " сейчас " + helper.getBlockState(planks.get(index)));
            }
            spirits.forEach(SpiritEntity::discard);
            GameTestFixtures.finish(helper, 16);
        });
    }
}
