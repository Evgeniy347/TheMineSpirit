package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardState;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.RescuePlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.lease.LeaseStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.lease.LeaseData;
import com.theminespirit.neoforge.lease.LeaseRun;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import com.theminespirit.neoforge.policy.PlanBreachData;
import com.theminespirit.neoforge.policy.RescueBreach;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private static final BlockPos UNDER_CHEST = new BlockPos(2, TOP - 2, 2);
    private static final BlockPos WATER_POCKET = new BlockPos(6, POCKET, 2);

    private DigOutGameTests() {}

    // [флак] 7 ступеней по 3 блока камня деревянной киркой (~70 тиков на ступень) и шаги; запас 2× к ~1000 тиков
    @SceneStep(template = "mine_arena", timeoutTicks = 2400, board = true)
    public static void digOut_SealedPocketSevenDeep_ReachesSurface(GameTestHelper helper) {
        pocket(
                helper,
                List.of(
                        List.of(new BlockPos(2, POCKET, 5), new BlockPos(2, POCKET + 1, 5)),
                        List.of(new BlockPos(5, POCKET, 5), new BlockPos(5, POCKET + 1, 5)),
                        List.of(new BlockPos(8, TOP - 2, 8), new BlockPos(8, TOP - 1, 8)),
                        List.of(UNDER_CHEST, UNDER_CHEST.above()),
                        List.of(WATER_POCKET, WATER_POCKET.above())));
        helper.setBlock(WATER_POCKET, Blocks.WATER);
    }

    private static void pocket(GameTestHelper helper, List<List<BlockPos>> pockets) {
        GameTestFixtures.reset(helper, 16);
        SceneFrame.restore(helper);
        SceneFrame.assertContained(helper, TOP + 1);
        BoardService board = BoardRegistry.get(helper.getLevel());
        board.board().restore(new BoardState(List.of(), Set.of()));
        List<BlockPos> planks = prepareArena(helper, pockets);
        helper.setBlock(WATER_POCKET, Blocks.WATER);
        helper.setBlock(WATER_POCKET.above(), Blocks.STONE);
        List<SpiritEntity> spirits = new ArrayList<>();
        List<String> tickets = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        for (List<BlockPos> cells : pockets) {
            spirits.add(spawnInPocket(helper, board, cells, tickets, starts));
        }
        PillarScene pillar = spawnOnOwnPillar(helper, board);
        assertBreachStore(helper);
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
                        helper.getBlockState(planks.get(index))
                                .is(
                                        planks.get(index).equals(UNDER_CHEST.above(2))
                                                ? Blocks.CHEST
                                                : Blocks.JUNGLE_PLANKS),
                        "Разбор при выходе временный: сломанная доска плана обязана вернуться на место после "
                                + "спасения, иначе в постройке остаётся дыра (observe25 B2); в клетке "
                                + planks.get(index) + " сейчас " + helper.getBlockState(planks.get(index)));
            }
            helper.assertTrue(
                    pillar.spirit().getY() <= pillar.groundY() + 0.5,
                    "дух на своём столбе обязан спуститься на землю, а не висеть на нём: стоит на "
                            + pillar.spirit().blockPosition() + ", земля на " + pillar.groundY() + ", задача "
                            + pillar.spirit().currentTaskScript().orElse("-") + " "
                            + pillar.spirit().taskStatus().orElse(null) + ", причина "
                            + pillar.spirit().taskFailureReason().orElse("нет"));
            for (BlockPos cell : pillar.column()) {
                helper.assertTrue(
                        helper.getBlockState(cell).isAir(),
                        "свой столб разобран до конца: в " + cell + " остался блок, и блоки не вернулись в работу");
            }
            helper.assertTrue(
                    LeaseData.get(helper.getLevel())
                                    .book()
                                    .require(pillar.leaseId())
                                    .status()
                            == LeaseStatus.DONE,
                    "успешный разбор столба закрывает аренду подмостей DONE: иначе клетки остались бы в уборке "
                            + "графа, а не вернулись в работу");
            assertDigOutLeasesClosed(helper, spirits);
            helper.assertFalse(
                    board.board()
                            .stuck()
                            .isStuck(pillar.spirit().getUUID(), board.board().now()),
                    "выполненный спуск со столба снимает метку «не двигается» по факту, а не по тайм-ауту: "
                            + "иначе дух навсегда выпадает из раздачи");
            SpiritEntity walker = spirits.get(0);
            walker.getNavigation().moveTo(walker.getX() + 1, walker.getY(), walker.getZ(), 1.0);
            helper.assertFalse(
                    walker.getNavigation().isDone(),
                    "соседняя клетка — настоящая цель шага: с точностью в блок дух считает её достигнутой и стоит");
            spirits.forEach(SpiritEntity::discard);
            pillar.spirit().discard();
            GameTestFixtures.finish(helper, 16);
        });
    }

    private record PillarScene(SpiritEntity spirit, List<BlockPos> column, int groundY, String leaseId) {}

    private static PillarScene spawnOnOwnPillar(GameTestHelper helper, BoardService board) {
        BlockPos base = new BlockPos(0, TOP + 1, 0);
        List<BlockPos> column = new ArrayList<>();
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), base.above(5));
        LeaseRun lease = LeaseRun.open(spirit, "scaffold");
        PlacedBlocksData placed = PlacedBlocksData.get(helper.getLevel());
        for (int dy = 0; dy < 5; dy++) {
            BlockPos cell = base.above(dy);
            helper.setBlock(cell, Blocks.DIRT);
            placed.markSpirit(helper.absolutePos(cell));
            lease.placed(helper.absolutePos(cell), "minecraft:dirt");
            column.add(cell);
        }
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_SHOVEL));
        BlockPos home = helper.absolutePos(new BlockPos(8, TOP + 1, 5));
        spirit.setHome(new BlockCoord(home.getX(), home.getY(), home.getZ()));
        BlockPos feet = spirit.blockPosition();
        BlockCoord at = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        UUID id = spirit.getUUID();
        long now = helper.getLevel().getGameTime();
        String ticket = "rescue:" + id;
        board.board().stuck().see(id, at, now);
        board.board().stuck().mark(id, now, "сцена: дух на своём столбе", true);
        TaskNode plan = RescuePlans.descent(new BlockCoord(home.getX(), home.getY(), home.getZ()));
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
        return new PillarScene(spirit, column, helper.absolutePos(base).getY(), lease.id());
    }

    private static void assertDigOutLeasesClosed(GameTestHelper helper, List<SpiritEntity> spirits) {
        Set<UUID> owners = spirits.stream().map(SpiritEntity::getUUID).collect(Collectors.toSet());
        for (var lease : LeaseData.get(helper.getLevel()).book().all()) {
            if ("dig_out".equals(lease.purpose()) && lease.spirit() != null && owners.contains(lease.spirit())) {
                helper.assertTrue(
                        lease.status() != LeaseStatus.ACTIVE,
                        "после выхода у духа не остаётся действующей аренды dig_out: иначе скан не увидит "
                                + "брошенный spoil и пол, сейчас " + lease.status() + " в клетках " + lease.cells());
            }
        }
    }

    private static List<BlockPos> prepareArena(GameTestHelper helper, List<List<BlockPos>> pockets) {
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
            helper.setBlock(plank, cells.get(0).equals(UNDER_CHEST) ? Blocks.CHEST : Blocks.JUNGLE_PLANKS);
            planks.add(plank);
        }
        return planks;
    }

    private static SpiritEntity spawnInPocket(
            GameTestHelper helper,
            BoardService board,
            List<BlockPos> cells,
            List<String> tickets,
            List<Integer> starts) {
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
        tickets.add(ticket);
        starts.add(feet.getY());
        return spirit;
    }

    private static void assertBreachStore(GameTestHelper helper) {
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
    }
}
