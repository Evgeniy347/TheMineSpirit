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
import com.theminespirit.neoforge.BlockPlacer;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.PlanBreachRestore;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.policy.PlanBreachData;
import com.theminespirit.neoforge.policy.RescueBreach;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
        pocket(helper, List.of(new BlockPos(2, POCKET, 5), new BlockPos(2, POCKET + 1, 5)));
    }

    private static void pocket(GameTestHelper helper, List<BlockPos> air) {
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
        air.forEach(cell -> helper.setBlock(cell, Blocks.AIR));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, POCKET, 5));
        spirit.getInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        spirit.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 48));
        BlockPos home = helper.absolutePos(new BlockPos(8, TOP + 1, 5));
        spirit.setHome(new BlockCoord(home.getX(), home.getY(), home.getZ()));
        BlockPos feet = spirit.blockPosition();
        BlockCoord at = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        int startY = feet.getY();
        BlockCoord goal = new BlockCoord(home.getX(), home.getY(), home.getZ());
        UUID id = spirit.getUUID();
        long now = helper.getLevel().getGameTime();
        BlockPos restorerAt = helper.absolutePos(new BlockPos(8, TOP + 1, 5));
        SpiritEntity restorer = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(8, TOP + 1, 5));
        restorer.setPos(restorerAt.getX() + 0.5, restorerAt.getY(), restorerAt.getZ() + 0.5);
        restorer.getInventory().setItem(0, new ItemStack(Items.JUNGLE_PLANKS, 4));
        BlockPos restoredCell = restorerAt.east();
        helper.getLevel().setBlockAndUpdate(restoredCell, Blocks.AIR.defaultBlockState());
        PlanBreachData.get(helper.getLevel()).record(restoredCell, "minecraft:jungle_planks", restorer.getUUID(), now);
        PlanBreachRestore.restore(restorer, helper.getLevel(), new BlockPlacer(restorer), restorer.blockPosition());
        helper.assertTrue(
                helper.getLevel().getBlockState(restoredCell).is(Blocks.JUNGLE_PLANKS),
                "Пролом плана заделан тем же блоком (jungle_planks): клетка снова часть дома");
        helper.assertTrue(
                PlanBreachData.get(helper.getLevel()).breach(restoredCell) == null,
                "Заделанный пролом снимается с учёта: задание восстановления не остаётся на доске");
        restorer.discard();
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

        BlockPos breachCell = new BlockPos(-177, 72, -506);
        UUID breachSpirit = UUID.randomUUID();
        PlanBreachData breach = new PlanBreachData();
        breach.record(breachCell, "minecraft:jungle_planks", breachSpirit, 100);
        breach.record(breachCell.above(), "minecraft:jungle_planks", breachSpirit, 100);
        PlanBreachData loaded = PlanBreachData.load(
                breach.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        helper.assertTrue(
                loaded.breach(breachCell) != null
                        && "minecraft:jungle_planks"
                                .equals(loaded.breach(breachCell).blockId())
                        && breachSpirit.equals(loaded.breach(breachCell).spirit()),
                "Пролом плана переживает сохранение мира тем же блоком и духом");
        helper.assertTrue(
                loaded.clear(breachCell) && loaded.breach(breachCell) == null,
                "Заделанная клетка снимается с учёта пролома");
        helper.assertTrue(
                RescueBreach.breakable(Blocks.JUNGLE_PLANKS.defaultBlockState()),
                "Клетку плана из досок для самоспасения ломать можно");
        helper.assertFalse(
                RescueBreach.breakable(Blocks.CHEST.defaultBlockState()),
                "Сундук в клетке плана не ломается: это чужая вещь, а не стена");
        helper.assertFalse(
                RescueBreach.breakable(Blocks.OAK_DOOR.defaultBlockState()),
                "Дверь в клетке плана не ломается при самоспасении");
        helper.assertFalse(
                RescueBreach.breakable(Blocks.OAK_FENCE_GATE.defaultBlockState()),
                "Ворота в клетке плана не ломаются при самоспасении");
        helper.succeedWhen(() -> {
            BlockPos current = spirit.blockPosition();
            helper.assertTrue(
                    DigOutScript.open(helper.getLevel(), current) || current.getY() >= startY + 3,
                    "замурованный дух обязан выбраться из кармана (INC-45): стоит в " + current + ", старт "
                            + startY + ", задача " + spirit.currentTaskScript().orElse("-") + " "
                            + spirit.taskStatus().orElse(null)
                            + ", причина " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    board.board().statusOf(ticket).orElse(null) == BoardStatus.DONE,
                    "выход закрывается заданием доски, статус "
                            + board.board().statusOf(ticket).orElse(null));
            helper.assertFalse(
                    board.board().stuck().isStuck(id, board.board().now()),
                    "выполненный выход снимает метку «не двигается» по факту, а не по тайм-ауту");
            spirit.discard();
            GameTestFixtures.finish(helper, 16);
        });
    }
}
