package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardCraterGameTests {
    private static final float BLAST_RADIUS = 5.0f;
    private static final int BLAST_DIRT = 79;
    // [флак] заделка 3×3: путь к яме, добыча из инвентаря и 9 постановок плюс ожидание двух
    // пересборок (выдача и учёт): под нагрузкой машины в 400 тиков не укладывается, запас двойной.
    private static final int FILL_TIMEOUT_TICKS = 800;
    private static final String SCENE_TICKET_PREFIX = "board_test_";

    private BoardCraterGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 100, board = true, calmMobs = true)
    public static void crater_BlastOutsideTerritory_TicketWithDirtCount(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        BlockCoord home = absolute(helper, new BlockPos(4, 1, 4));
        spirit.setHome(home);
        ServerLevel level = helper.getLevel();
        Creeper creeper = Objects.requireNonNull(EntityType.CREEPER.create(level), "крипер-источник взрыва не создан");
        Pig pig = Objects.requireNonNull(EntityType.PIG.create(level), "свинья-источник взрыва не создана");
        int farX = home.x() + 100_000;
        int farZ = home.z() + 100_000;
        NeoForge.EVENT_BUS.post(new ExplosionEvent.Detonate(
                level,
                new Explosion(
                        level,
                        creeper,
                        farX + 0.5,
                        home.y() + 0.5,
                        farZ + 0.5,
                        BLAST_RADIUS,
                        false,
                        Explosion.BlockInteraction.DESTROY_WITH_DECAY),
                List.of()));
        String craterId = "crater:" + farX + "," + home.y() + "," + farZ;
        int alienX = home.x() + 200_000;
        int alienZ = home.z() + 200_000;
        NeoForge.EVENT_BUS.post(new ExplosionEvent.Detonate(
                level,
                new Explosion(
                        level,
                        pig,
                        alienX + 0.5,
                        home.y() + 0.5,
                        alienZ + 0.5,
                        BLAST_RADIUS,
                        false,
                        Explosion.BlockInteraction.DESTROY_WITH_DECAY),
                List.of()));
        String alienId = "crater:" + alienX + "," + home.y() + "," + alienZ;
        BoardService board = BoardRegistry.get(level);

        BoardTicket ticket = board.board().find(craterId).orElse(null);
        helper.assertTrue(
                ticket != null,
                "Взрыв крипера за территорией обязан поднять FILL_CRATER " + craterId
                        + ": иначе воронки за городом не заделываются");
        helper.assertTrue(
                ticket.boardCase() == BoardCase.FILL_CRATER,
                "Задание воронки — кейс FILL_CRATER, фактически " + ticket.boardCase());
        helper.assertTrue(
                Map.of("dirt", BLAST_DIRT).equals(ticket.requiredItems()),
                "Воронка радиуса 5 требует 79 земли (ceil(pi*25)), фактически "
                        + ticket.requiredItems() + ": гейт с неверным числом либо держит "
                        + "лишнее, либо отпускает без запаса");
        helper.assertTrue(
                board.board().find(alienId).isEmpty(),
                "Взрыв не крипера (свинья) — не кейс доски: задание " + alienId + " поднялось зря");
        board.board().cancel(craterId);
        helper.assertTrue(
                board.board().statusOf(craterId).orElse(null) == BoardStatus.CANCELLED,
                "Проверенная воронка снимается отменой, а не висит в очереди: статус "
                        + board.board().statusOf(craterId).orElse(null));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = FILL_TIMEOUT_TICKS, board = true, calmMobs = true)
    public static void crater_DirtGate_FillsFlat(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                helper.setBlock(new BlockPos(3 + dx, 0, 3 + dz), Blocks.AIR);
            }
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.setHome(absolute(helper, new BlockPos(4, 1, 4)));
        spirit.getInventory().setItem(0, new ItemStack(Items.DIRT, 16));
        ServerLevel level = helper.getLevel();
        BoardService board = BoardRegistry.get(level);
        drainForeignTickets(board);
        BlockCoord center = absolute(helper, new BlockPos(4, 0, 4));
        board.submit(
                new BoardTicket(
                        "board_test_crater_fill",
                        BoardCase.FILL_CRATER,
                        Optional.of(spirit.getUUID()),
                        BoardRules.priorityOf(BoardCase.FILL_CRATER),
                        level.getGameTime(),
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of("dirt", 9)),
                TicketTarget.at(center).withCraterBlocks(9));
        board.rebuild(level, level.getGameTime());

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    spirit.getUUID()
                            .equals(board.board()
                                    .holderOf("board_test_crater_fill")
                                    .orElse(null)),
                    "Заделку держит назначенный дух: держатель "
                            + board.board().holderOf("board_test_crater_fill").orElse(null));
            helper.assertTrue(
                    countHoles(helper) == 0,
                    "С землёй воронка 3×3 заделана ровно: осталось " + countHoles(helper)
                            + " пустот на уровне пола; задача духа "
                            + spirit.currentTaskScript().orElse("нет")
                            + ", статус " + spirit.taskStatus().map(Enum::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + ", дух в "
                            + helper.relativePos(spirit.blockPosition()) + ", земли "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIRT)
                            + ", план "
                            + spirit.currentPlan().map(Object::toString).orElse("нет")
                            + ", центр " + center + ", дом "
                            + spirit.home().map(Object::toString).orElse("нет")
                            + ", задания доски "
                            + board.board().all().stream()
                                    .map(ticket -> ticket.id() + "→"
                                            + board.board()
                                                    .holderOf(ticket.id())
                                                    .map(Object::toString)
                                                    .orElse("-"))
                                    .toList()
                            + ", дух " + spirit.getUUID() + " в мире " + spirit.blockPosition() + " (точно "
                            + spirit.position() + "), поворот " + helper.getTestRotation() + ", политика "
                            + spirit.policies().verdict() + " владелец "
                            + spirit.policies().owner()
                            + ", тактика хочет " + spirit.tactics().wantsControl() + " исход "
                            + spirit.tactics()
                                    .lastOutcome()
                                    .map(Object::toString)
                                    .orElse("нет"));
            helper.assertTrue(
                    board.board().statusOf("board_test_crater_fill").orElse(null) == BoardStatus.DONE,
                    "После заделки задание закрыто как DONE, статус "
                            + board.board().statusOf("board_test_crater_fill").orElse(null));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void issue_AddressedTicketWaiting_GenericTicketGoesToOtherSpirit(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity addressee = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        SpiritEntity other = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(8, 1, 8));
        addressee.getInventory().setItem(0, new ItemStack(Items.DIRT, 16));
        ServerLevel level = helper.getLevel();
        BoardService board = BoardRegistry.get(level);
        drainForeignTickets(board);
        BlockCoord near = absolute(helper, new BlockPos(2, 1, 2));
        board.submit(
                new BoardTicket(
                        "board_test_generic_planks",
                        BoardCase.GATHER_MATERIALS,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                        level.getGameTime(),
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(near).withSupply("oak_planks", 4));
        board.submit(
                new BoardTicket(
                        "board_test_addressed_crater",
                        BoardCase.FILL_CRATER,
                        Optional.of(addressee.getUUID()),
                        BoardRules.priorityOf(BoardCase.FILL_CRATER),
                        level.getGameTime(),
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of("dirt", 9)),
                TicketTarget.at(absolute(helper, new BlockPos(3, 0, 3))).withCraterBlocks(9));
        board.rebuild(level, level.getGameTime());

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    addressee
                            .getUUID()
                            .equals(board.board()
                                    .holderOf("board_test_addressed_crater")
                                    .orElse(null)),
                    "Адресное задание обязано достаться своему духу, хотя «добыть доски» выше и ближе к нему: "
                            + "держатель "
                            + board.board()
                                    .holderOf("board_test_addressed_crater")
                                    .orElse(null));
            helper.assertTrue(
                    other.getUUID()
                            .equals(board.board()
                                    .holderOf("board_test_generic_planks")
                                    .orElse(null)),
                    "Задание без адресата берёт другой свободный дух, а не тот, кого ждёт адресное: держатель "
                            + board.board()
                                    .holderOf("board_test_generic_planks")
                                    .orElse(null));
            board.board().cancel("board_test_generic_planks");
            board.board().cancel("board_test_addressed_crater");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = FILL_TIMEOUT_TICKS, board = true, calmMobs = true)
    public static void crater_DirtGate_WaitsSupply(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.setHome(absolute(helper, new BlockPos(4, 1, 4)));
        ServerLevel level = helper.getLevel();
        BoardService board = BoardRegistry.get(level);
        BlockCoord center = absolute(helper, new BlockPos(4, 0, 4));
        board.submit(
                new BoardTicket(
                        "board_test_crater_wait",
                        BoardCase.FILL_CRATER,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.FILL_CRATER),
                        level.getGameTime(),
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of("dirt", 5000)),
                TicketTarget.at(center).withCraterBlocks(5000));
        board.rebuild(level, level.getGameTime());

        helper.succeedWhen(() -> {
            long now = level.getGameTime();
            helper.assertTrue(
                    board.board().statusOf("board_test_crater_wait").orElse(null) == BoardStatus.WAITING_RESOURCES,
                    "Без 5000 земли задание висит в WAITING_RESOURCES, статус "
                            + board.board().statusOf("board_test_crater_wait").orElse(null)
                            + ": стройка, стартовавшая без ресурсов, ломает гейт доски");
            helper.assertTrue(
                    board.board().statusOf("supply:dirt").isPresent()
                            || board.board().recentlyFailed("supply:dirt", now),
                    "При нехватке доска ставит добычу supply:dirt: без неё ждущее задание "
                            + "зависнет навсегда (добыча видна как задание, либо как свежий провал)");
            board.board().cancel("board_test_crater_wait");
            board.board().cancel("supply:dirt");
            GameTestFixtures.finish(helper);
        });
    }

    private static int countHoles(GameTestHelper helper) {
        int holes = 0;
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                if (helper.getBlockState(new BlockPos(3 + dx, 0, 3 + dz)).isAir()) {
                    holes++;
                }
            }
        }
        return holes;
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static void drainForeignTickets(BoardService board) {
        for (BoardTicket ticket : board.board().all()) {
            if (!ticket.id().startsWith(SCENE_TICKET_PREFIX)) {
                board.board().cancel(ticket.id());
            }
        }
    }
}
