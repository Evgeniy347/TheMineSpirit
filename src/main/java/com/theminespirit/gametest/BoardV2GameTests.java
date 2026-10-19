package com.theminespirit.gametest;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardConfig;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.CraterProfiles;
import com.theminespirit.core.board.HerdCap;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.RelocatePlan;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.hunger.SpiritHunger;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.TableStatus;
import com.theminespirit.neoforge.DataReload;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.BoardSources;
import com.theminespirit.neoforge.board.EnchantRoomWatch;
import com.theminespirit.neoforge.board.HerdWatch;
import com.theminespirit.neoforge.board.JournalService;
import com.theminespirit.neoforge.board.StockReader;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Доска v2 в мире: постоянная еда, актуальность воронки, уровни шахты и чаровальни,
 * загоны по видам, перенос постройки и журнал с версией конфигурации. Каждая сцена
 * работает на собственной доске с временным журналом: общий ledger прошлых прогонов
 * не должен решать, какой уровень шахты уже стоит.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true)
public final class BoardV2GameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BATCH = "board_v2";
    private static final String EVERGREEN_BATCH = "board_v2_evergreen";
    private static final String FOOD_BATCH = "board_v2_food";
    private static final int CLEAR_RADIUS = 4;
    private static final long NIGHT_TIME = 18_000L;
    private static final int NOON_TIME = 6_000;
    private static final int HUNGRY_LEVEL = 19;
    private static final int FULL_LEVEL = SpiritHunger.MAX_FOOD;

    private BoardV2GameTests() {}

    @BeforeBatch(batch = BATCH)
    public static void prepareBatch(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = BATCH)
    public static void restoreBatch(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(true);
    }

    @BeforeBatch(batch = EVERGREEN_BATCH)
    public static void prepareEvergreenBatch(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = EVERGREEN_BATCH)
    public static void restoreEvergreenBatch(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(true);
    }

    @GameTest(template = "arena", batch = EVERGREEN_BATCH, timeoutTicks = 400)
    public static void evergreen_HungryAndFull_TakesOnlyByNeed(GameTestHelper helper) {
        GameTestFixtures.holdDayTime(helper, NIGHT_TIME);
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        SpiritEntity hungry = spawnAt(helper, new BlockPos(1, 1, 1), new BlockPos(4, 1, 4));
        SpiritEntity full = spawnAt(helper, new BlockPos(2, 1, 1), new BlockPos(4, 1, 5));
        SpiritEntity nightHungry = spawnAt(helper, new BlockPos(3, 1, 1), new BlockPos(4, 1, 6));
        hungry.getInventory().setItem(0, new ItemStack(Items.BREAD, 3));
        nightHungry.getInventory().setItem(0, new ItemStack(Items.BREAD, 3));
        hungry.hunger().restore(SpiritHunger.of(HUNGRY_LEVEL, 0, 0));
        nightHungry.hunger().restore(SpiritHunger.of(HUNGRY_LEVEL, 0, 0));
        full.hunger().restore(SpiritHunger.full());
        BoardService board = BoardRegistry.get(level);
        drainForeignTickets(board);
        board.rebuild(level, level.getGameTime());
        String hungryId = "eat:" + hungry.getUUID();
        String nightId = "eat:" + nightHungry.getUUID();
        String fullId = "eat:" + full.getUUID();
        helper.assertTrue(
                board.board().find(hungryId).isPresent(),
                "Голодный дух обязан получить EAT_TO_FULL " + hungryId
                        + ": иначе постоянного задания еды нет в адаптере");
        helper.assertTrue(
                board.board().find(nightId).isPresent(),
                "Голодный ночью у себя обязан взять EAT_TO_FULL: ночь не отменяет еду");
        helper.assertTrue(
                board.board().find(fullId).isEmpty(),
                "Сытый и здоровый дух задание не берёт: EAT_TO_FULL применим только по нужде");

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    board.board().statusOf(hungryId).orElse(null) == BoardStatus.DONE,
                    "Поевший дух закрывает EAT_TO_FULL как DONE, статус "
                            + board.board().statusOf(hungryId).orElse(null));
            helper.assertTrue(
                    board.board().statusOf(nightId).orElse(null) == BoardStatus.DONE,
                    "Ночная еда тоже закрывается DONE, статус "
                            + board.board().statusOf(nightId).orElse(null));
            helper.assertTrue(
                    board.board().find(hungryId).isPresent(),
                    "Выполненное постоянное задание остаётся на доске условием, а не исчезает");
            helper.assertTrue(
                    hungry.hunger().foodLevel() == FULL_LEVEL,
                    "EAT_TO_FULL обязан наесть до полной сытости " + FULL_LEVEL + ", фактически "
                            + hungry.hunger().foodLevel());
            helper.assertTrue(
                    nightHungry.hunger().foodLevel() == FULL_LEVEL,
                    "Ночной обед обязан наесть до полной сытости " + FULL_LEVEL + ", фактически "
                            + nightHungry.hunger().foodLevel());
            helper.assertTrue(
                    board.board().find(fullId).isEmpty(),
                    "Сытый дух так и не получает задание: "
                            + board.board().find(fullId).orElse(null));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = FOOD_BATCH, timeoutTicks = 400)
    public static void food_HungryWithoutFood_WithdrawsFromStorehouse(GameTestHelper helper) {
        GameTestFixtures.holdDayTime(helper, NOON_TIME);
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        SpiritEntity hungry = spawnAt(helper, new BlockPos(1, 1, 1), new BlockPos(4, 1, 4));
        hungry.hunger().restore(SpiritHunger.of(HUNGRY_LEVEL, 0, 0));
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.CHEST);
        Container chest = (Container) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 1, 4)));
        chest.setItem(0, new ItemStack(Items.BREAD, 48));
        BoardService board = BoardRegistry.get(level);
        drainForeignTickets(board);
        board.rebuild(level, level.getGameTime());
        String id = "food:" + hungry.getUUID();
        helper.assertTrue(
                board.board().find(id).isPresent(),
                "Голодный дух без личной еды обязан получить RESTOCK_FOOD " + id
                        + ": иначе он останется голодным при полном складе еды");

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    GameTestFixtures.countInInventory(hungry.getInventory(), Items.BREAD) > 0,
                    "Дух обязан взять еду со склада по RESTOCK_FOOD, фактически хлеба в инвентаре "
                            + GameTestFixtures.countInInventory(hungry.getInventory(), Items.BREAD));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 200)
    public static void relevance_CraterBury_DetachesAndLevelTwoWaitsGate(GameTestHelper helper) {
        GameTestFixtures.holdDayTime(helper, NOON_TIME);
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        Path log = journalPath(helper, "relevance");
        BoardService board = isolated(level, log);
        long now = level.getGameTime();
        BlockCoord center = absolute(helper, new BlockPos(4, 0, 4));
        List<BlockCoord> profile = new ArrayList<>();
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                BlockPos hole = new BlockPos(3 + dx, 0, 3 + dz);
                helper.setBlock(hole, Blocks.AIR);
                profile.add(absolute(helper, hole));
            }
        }
        String cause = CraterProfiles.cause(center);
        board.craters().remember(cause, profile);
        String craterId = "board_v2_crater";
        board.submit(
                new BoardTicket(
                        craterId,
                        BoardCase.FILL_CRATER,
                        Optional.of(UUID.randomUUID()),
                        BoardRules.priorityOf(BoardCase.FILL_CRATER),
                        now,
                        1,
                        cause,
                        List.of(),
                        Map.of("dirt", 9)),
                TicketTarget.at(center));
        board.rebuild(level, now);
        helper.assertTrue(
                board.board().statusOf(craterId).orElse(null) != BoardStatus.CANCELLED,
                "Пока воронка пуста, задание актуально и не снимается: статус "
                        + board.board().statusOf(craterId).orElse(null));
        for (BlockCoord cell : profile) {
            level.setBlockAndUpdate(new BlockPos(cell.x(), cell.y(), cell.z()), Blocks.DIRT.defaultBlockState());
        }
        board.rebuild(level, now + 1);
        helper.assertTrue(
                board.board().statusOf(craterId).orElse(null) == BoardStatus.CANCELLED,
                "Засыпанная воронка обязана сняться на пересборке, статус "
                        + board.board().statusOf(craterId).orElse(null)
                        + ": воронка, висящая после засыпки, держит духа на пустой работе");
        board.journal().flushAll();
        boolean detached = board.journal().last(50).stream()
                .anyMatch(entry -> entry.event() == BoardLog.Event.DETACHED
                        && entry.ticketId().equals(craterId));
        helper.assertTrue(
                detached,
                "Снятие по актуальности обязано оставить запись DETACHED в журнале: иначе причина "
                        + "исчезновения задания не видна");

        SpiritEntity spirit = spawnAt(helper, new BlockPos(1, 1, 1), new BlockPos(4, 1, 4));
        board.rebuild(level, now + 2);
        helper.assertTrue(
                board.board().find("mine_level_2").isEmpty(),
                "Уровень 2 шахты не ставится, пока уровень 1 не выполнен: уровень не прыгает");
        board.submit(
                new BoardTicket(
                        "mine_1",
                        BoardCase.BUILD_MINE,
                        Optional.of(spirit.getUUID()),
                        BoardRules.priorityOf(BoardCase.BUILD_MINE),
                        now + 2,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(absolute(helper, new BlockPos(4, 1, 4))));
        helper.assertTrue(
                board.board().claim("mine_1", spirit.getUUID(), now + 2) != BoardOutcome.NOT_FOUND,
                "Уровень 1 шахты обязан выдаться духу: без него цепочка уровней не начнётся");
        board.board().complete("mine_1");
        board.rebuild(level, now + 3);
        BoardTicket levelTwo = board.board().find("mine_level_2").orElse(null);
        helper.assertTrue(
                levelTwo != null && levelTwo.level() == 2,
                "После уровня 1 обязан появиться уровень 2 (расширение), фактически " + levelTwo);
        helper.assertTrue(
                board.board().statusOf("mine_level_2").orElse(null) == BoardStatus.WAITING_RESOURCES,
                "Без камня уровень 2 ждёт гейт WAITING_RESOURCES, статус "
                        + board.board().statusOf("mine_level_2").orElse(null)
                        + ": расширение, стартовавшее без материалов, ломает гейт");
        TaskNode minePlan =
                TicketPlans.plan(BoardCase.BUILD_MINE, 2, TicketTarget.at(absolute(helper, new BlockPos(4, 1, 4))));
        helper.assertTrue(
                minePlan instanceof ScriptCall call && "dig_area".equals(call.script()),
                "План уровня 2 шахты — расширение dig_area, фактически " + minePlan);
        spirit.getInventory().setItem(0, new ItemStack(Items.STONE, 32));
        board.rebuild(level, now + 4);
        BoardStatus afterStone = board.board().statusOf("mine_level_2").orElse(null);
        helper.assertTrue(
                afterStone == BoardStatus.ASSIGNED || afterStone == BoardStatus.PENDING,
                "После добычи 32 камня уровень 2 обязан выйти из гейта, статус " + afterStone);
        board.cancel(level, "mine_level_2");
        close(board, log, helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 200)
    public static void herd_TwoSpeciesParallel_PenLevelOneLureAndBreedLimit(GameTestHelper helper) {
        GameTestFixtures.holdDayTime(helper, NOON_TIME);
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        Path log = journalPath(helper, "herd");
        BoardService board = isolated(level, log);
        long now = level.getGameTime();
        SpiritEntity spirit = spawnAt(helper, new BlockPos(1, 1, 1), new BlockPos(4, 1, 4));
        Cow cowOne = helper.spawn(EntityType.COW, new BlockPos(6, 1, 6));
        Cow cowTwo = helper.spawn(EntityType.COW, new BlockPos(7, 1, 6));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(6, 1, 7));
        cowOne.setNoAi(true);
        cowTwo.setNoAi(true);
        pig.setNoAi(true);

        HerdWatch.tick(level, board, List.of(spirit), now);
        String cowCapture = captureId(board, BoardCase.CAPTURE_ANIMAL, "cow");
        String pigCapture = captureId(board, BoardCase.CAPTURE_ANIMAL, "pig");
        helper.assertTrue(
                cowCapture != null && pigCapture != null,
                "Два вида рядом с базой обязаны получить по отлову параллельно: коровий " + cowCapture + ", свиной "
                        + pigCapture);
        helper.assertTrue(
                cowCapture.contains("cow") && pigCapture.contains("pig"),
                "Отлов адресован виду: корова " + cowCapture + ", свинья " + pigCapture);

        board.completeCapture(cowCapture);
        String secondCow = "capture:cow:" + cowTwo.getUUID();
        board.noteCapture(
                secondCow,
                Species.COW,
                cowTwo.getUUID(),
                TicketTarget.at(absolute(helper, new BlockPos(7, 1, 6))).withSpecies(Species.COW));
        board.submit(
                new BoardTicket(
                        secondCow,
                        BoardCase.CAPTURE_ANIMAL,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.CAPTURE_ANIMAL),
                        now,
                        1,
                        "animal:" + secondCow,
                        List.of(),
                        Map.of()),
                TicketTarget.at(absolute(helper, new BlockPos(7, 1, 6))).withSpecies(Species.COW));
        board.completeCapture(pigCapture);
        helper.assertTrue(
                active(board, BoardCase.BUILD_PEN) == 2,
                "После отлова двух видов на доске два загона параллельно, активных "
                        + active(board, BoardCase.BUILD_PEN)
                        + ": один вид не должен блокировать цикл другого");
        BoardTicket cowPen = board.board().find("cow_pen").orElse(null);
        BoardTicket pigPen = board.board().find("pig_pen").orElse(null);
        helper.assertTrue(
                cowPen != null && cowPen.level() == 1 && pigPen != null && pigPen.level() == 1,
                "Оба загона ставятся уровнем 1: коровий " + cowPen + ", свиной " + pigPen);
        assertPenLevelOne(helper, absolute(helper, new BlockPos(4, 1, 4)));

        completeTicket(board, "cow_pen", spirit, now);
        completeTicket(board, "pig_pen", spirit, now);
        board.completePen("cow_pen");
        board.completePen("pig_pen");
        BoardTicket cowLure = board.board().find("cow_lure").orElse(null);
        BoardTicket pigLure = board.board().find("pig_lure").orElse(null);
        helper.assertTrue(
                cowLure != null && pigLure != null,
                "У каждого вида своя приманка: корова " + cowLure + ", свинья " + pigLure);
        helper.assertTrue(
                Map.of("wheat", 1).equals(cowLure.requiredItems()),
                "Корову приманивают пшеницей, фактически " + cowLure.requiredItems());
        helper.assertTrue(
                Map.of("carrot", 1).equals(pigLure.requiredItems()),
                "Свинью приманивают морковью, фактически " + pigLure.requiredItems()
                        + ": приманка чужого вида увела бы животное не в тот загон");
        helper.assertTrue(
                cowLure.cause().contains(":cow:") && pigLure.cause().contains(":pig:"),
                "Причина приманки — животное своего вида: " + cowLure.cause() + " / " + pigLure.cause());

        HerdWatch.tick(level, board, List.of(spirit), now + 1);
        BoardTicket breed = board.board().find("cow_breed").orElse(null);
        helper.assertTrue(
                breed != null && breed.boardCase() == BoardCase.BREED_ANIMALS,
                "Две коровы в загоне разрешают разведение, фактически " + breed
                        + ": разведение без пары или без загона не ставится");
        helper.assertTrue(
                breed.requiredItems().getOrDefault("minecraft:wheat", 0) == HerdCap.minBreed(),
                "Гейт разведения — корм на пару (" + HerdCap.minBreed() + " пшеницы), фактически "
                        + breed.requiredItems());

        int activeCaptures = activeCaptures(board, "cow");
        cowOne.discard();
        cowTwo.discard();
        Cow fifth = helper.spawn(EntityType.COW, new BlockPos(8, 1, 8));
        fifth.setNoAi(true);
        board.noteCapture(
                "capture:cow:" + UUID.randomUUID(),
                Species.COW,
                UUID.randomUUID(),
                TicketTarget.at(absolute(helper, new BlockPos(4, 1, 4))).withSpecies(Species.COW));
        board.noteCapture(
                "capture:cow:" + UUID.randomUUID(),
                Species.COW,
                UUID.randomUUID(),
                TicketTarget.at(absolute(helper, new BlockPos(4, 1, 4))).withSpecies(Species.COW));
        HerdWatch.tick(level, board, List.of(spirit), now + 2);
        helper.assertTrue(
                HerdCap.maxPerSpecies() == 4, "Предел в тесте, факт из production: не больше 4 животных каждого вида");
        helper.assertTrue(
                activeCaptures(board, "cow") == activeCaptures,
                "Пятая корова при четырёх не порождает новый CAPTURE_ANIMAL: было активно "
                        + activeCaptures + ", стало " + activeCaptures(board, "cow")
                        + ": сверх лимита стадо не растёт");

        for (BoardTicket ticket : board.board().all()) {
            BoardStatus status = board.board().statusOf(ticket.id()).orElse(null);
            if (status != BoardStatus.DONE && status != BoardStatus.CANCELLED) {
                board.board().cancel(ticket.id());
            }
        }
        close(board, log, helper);
        helper.succeed();
    }

    // [флак] перенос: путь, разбор старого загона и стройка нового 4×5; под нагрузкой
    // машины 400 тиков мало, запас двойной.
    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 800)
    public static void relocate_PenMoved_OldClearedNewStandsAndMineRefused(GameTestHelper helper) {
        GameTestFixtures.holdDayTime(helper, NOON_TIME);
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        BoardService board = BoardRegistry.get(level);
        drainForeignTickets(board);
        SpiritEntity spirit = spawnAt(helper, new BlockPos(1, 1, 1), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.OAK_FENCE, 32));
        spirit.getInventory().setItem(1, new ItemStack(Items.OAK_FENCE_GATE, 4));
        BlockPos oldOne = new BlockPos(1, 1, 5);
        BlockPos oldTwo = new BlockPos(2, 1, 5);
        BlockPos oldThree = new BlockPos(3, 1, 5);
        helper.setBlock(oldOne, Blocks.OAK_FENCE);
        helper.setBlock(oldTwo, Blocks.OAK_FENCE);
        helper.setBlock(oldThree, Blocks.OAK_FENCE);
        markSpirit(helper, oldOne);
        markSpirit(helper, oldTwo);
        markSpirit(helper, oldThree);
        BlockCoord from = absolute(helper, new BlockPos(2, 1, 6));
        BlockCoord to = absolute(helper, new BlockPos(4, 1, 1));
        String id = "relocate_pen";
        board.submit(
                new BoardTicket(
                        id,
                        BoardCase.RELOCATE_BUILDING,
                        Optional.of(spirit.getUUID()),
                        BoardRules.priorityOf(BoardCase.RELOCATE_BUILDING),
                        level.getGameTime(),
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(to).withRelocate(from, to).withScheme("pen_4x5"));
        board.rebuild(level, level.getGameTime());
        boolean[] finished = {false};
        helper.onEachTick(() -> {
            BoardStatus status = board.board().statusOf(id).orElse(null);
            if (status == BoardStatus.FAILED) {
                helper.fail("Перенос провалился: " + spirit.taskFailureReason().orElse("причина не записана")
                        + "; прогресс " + spirit.taskProgress().orElse(null));
            }
            if (status != BoardStatus.DONE || finished[0]) {
                return;
            }
            finished[0] = true;
            helper.assertTrue(
                    helper.getBlockState(oldOne).isAir()
                            && helper.getBlockState(oldTwo).isAir()
                            && helper.getBlockState(oldThree).isAir(),
                    "Старый загон обязан быть снесён в from: остались "
                            + helper.getBlockState(oldOne) + ", " + helper.getBlockState(oldTwo)
                            + ", " + helper.getBlockState(oldThree));
            int fence = countFence(helper, to, 4, 5);
            helper.assertTrue(
                    fence > 0,
                    "Новый загон обязан стоять в to: клеток забора в 4×5 — " + fence
                            + "; в from " + countFence(helper, from, 4, 5)
                            + "; в руках осталось "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_FENCE)
                            + " забора и "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_FENCE_GATE)
                            + " калиток; клетка to "
                            + helper.getLevel().getBlockState(new BlockPos(to.x(), to.y(), to.z()))
                            + ": без новой постройки перенос теряет загон");
            helper.assertTrue(
                    hasGate(helper, to, 4, 5), "Новый загон обязан сохранить калитку: без неё животное не загнать");
            assertImmovableRefused(helper);
            GameTestFixtures.finish(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 200)
    public static void enchant_LevelOneNeedsResources_LevelTwoWaitsShelves(GameTestHelper helper) {
        GameTestFixtures.holdDayTime(helper, NOON_TIME);
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        Path log = journalPath(helper, "enchant");
        BoardService board = isolated(level, log);
        SpiritEntity spirit = spawnAt(helper, new BlockPos(1, 1, 1), new BlockPos(4, 1, 4));
        long now = level.getGameTime();
        List<SpiritEntity> spirits = List.of(spirit);

        EnchantRoomWatch.tick(level, board, spirits, new StockView(Map.of()), now);
        helper.assertTrue(
                board.board().find("enchant_room_1").isEmpty(),
                "Без лаписа и книги чаровальня не ставится: стол без ресурсов не строят");
        EnchantRoomWatch.tick(level, board, spirits, new StockView(Map.of("lapis_lazuli", 4, "book", 1)), now + 1);
        BoardTicket levelOne = board.board().find("enchant_room_1").orElse(null);
        helper.assertTrue(
                levelOne != null && levelOne.level() == 1,
                "С лаписом и книгой ставится уровень 1, фактически " + levelOne);
        helper.assertTrue(
                Map.of("lapis_lazuli", 4, "book", 1).equals(levelOne.requiredItems()),
                "Уровень 1 держит гейт по своим ресурсам, фактически " + levelOne.requiredItems()
                        + ": задание без гейта строило бы стол из ничего");
        board.board().claim("enchant_room_1", spirit.getUUID(), now + 1);
        board.board().complete("enchant_room_1");

        EnchantRoomWatch.tick(level, board, spirits, new StockView(Map.of()), now + 2);
        BoardTicket levelTwo = board.board().find("enchant_room_2").orElse(null);
        helper.assertTrue(
                levelTwo != null && levelTwo.level() == 2,
                "После уровня 1 ставится уровень 2 (кольцо 15 полок), фактически " + levelTwo);
        helper.assertTrue(
                board.board().claimable(new StockView(Map.of()), now + 2).stream()
                        .noneMatch(ticket -> ticket.id().equals("enchant_room_2")),
                "Без полок/книг уровень 2 не выдаётся: гейт держит полки");
        helper.assertTrue(
                board.board().statusOf("enchant_room_2").orElse(null) == BoardStatus.WAITING_RESOURCES,
                "Уровень 2 ждёт гейт WAITING_RESOURCES, статус "
                        + board.board().statusOf("enchant_room_2").orElse(null));
        EnchantRoomWatch.tick(level, board, spirits, new StockView(Map.of()), now + 3);
        helper.assertTrue(
                active(board, BoardCase.ENCHANT_ROOM) == 1,
                "Повторная проверка не плодит дубли уровня, активных чаровален "
                        + active(board, BoardCase.ENCHANT_ROOM));
        helper.assertTrue(
                board.board().claimable(new StockView(Map.of("bookshelf", 15)), now + 3).stream()
                        .anyMatch(ticket -> ticket.id().equals("enchant_room_2")),
                "С 15 полками уровень 2 выходит из гейта: книг хватает на кольцо");
        helper.assertTrue(
                board.board().claim("enchant_room_2", spirit.getUUID(), now + 3) != BoardOutcome.NOT_FOUND,
                "Уровень 2 обязан выдаться после появления полок");
        board.cancel(level, "enchant_room_2");
        close(board, log, helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 300)
    public static void journal_DoneAndReload_KeepsHashAndLedger(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        Path log = journalPath(helper, "journal");
        MinecraftServer server = level.getServer();
        helper.assertTrue(
                log.startsWith(server.getWorldPath(LevelResource.ROOT)),
                "Журнал теста обязан лежать в каталоге мира: путь " + log);
        String original = JsonResource.readClasspath(BoardConfig.RESOURCE);
        String changed = original.replace("\"FILL_CRATER\": 35", "\"FILL_CRATER\": 36");
        helper.assertTrue(
                !original.equals(changed), "Датапак теста обязан менять board.json, иначе смена hash не проверится");
        String beforeHash = ConfigStamp.current();
        BoardConfig savedConfig = DataTables.INSTANCE.board().current();
        TableStatus savedStatus = DataTables.INSTANCE.board().status();
        String packName = "board_v2_reload";
        Path pack = packDir(helper, packName);
        BoardService board = isolated(level, log);
        UUID holder = UUID.randomUUID();
        long now = level.getGameTime();
        board.submit(
                new BoardTicket(
                        "journal_build",
                        BoardCase.BUILD_HOUSE,
                        Optional.of(holder),
                        50,
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(absolute(helper, new BlockPos(4, 1, 4))));
        board.board().claim("journal_build", holder, now);
        board.board().complete("journal_build");
        board.journal().flushAll();
        BoardLogEntry first = entryOf(board, log, "journal_build");
        helper.assertTrue(first != null, "Выполненное задание обязано дать строку журнала");
        helper.assertTrue(
                beforeHash.equals(first.configHash()),
                "Строка несёт текущий configHash " + beforeHash + ", фактически " + first.configHash()
                        + ": без версии конфигурации журнал несравним со временем");
        try {
            reloadBoard(helper, changed, packName);
            String afterHash = ConfigStamp.current();
            helper.assertTrue(
                    !beforeHash.equals(afterHash),
                    "Изменённый board.json обязан дать новый hash: было " + beforeHash + ", стало " + afterHash);
            board.submit(
                    new BoardTicket(
                            "journal_after_reload",
                            BoardCase.BUILD_PEN,
                            Optional.of(holder),
                            65,
                            now + 1,
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            Map.of()),
                    TicketTarget.at(absolute(helper, new BlockPos(4, 1, 4))));
            board.board().claim("journal_after_reload", holder, now + 1);
            board.board().complete("journal_after_reload");
            board.journal().flushAll();
            BoardLogEntry second = entryOf(board, log, "journal_after_reload");
            helper.assertTrue(second != null, "Новое задание после перезагрузки обязано попасть в журнал");
            helper.assertTrue(
                    afterHash.equals(second.configHash()),
                    "Новая строка несёт новый hash " + afterHash + ", фактически " + second.configHash()
                            + ": старые и новые правила должны различаться");
            helper.assertTrue(
                    !beforeHash.equals(second.configHash()),
                    "Старый hash не переписан: новые записи идут с новой версией конфигурации");
        } finally {
            DataTables.INSTANCE
                    .board()
                    .replace(
                            savedConfig,
                            savedStatus.source(),
                            savedStatus.origin(),
                            savedStatus.hash(),
                            Clock.systemUTC());
            deleteTree(pack);
        }
        board.close();
        JournalService journal = new JournalService(log);
        Board restored = new Board(
                level::getGameTime, (event, ticket, holderId, tick) -> journal.record(event, ticket, holderId, tick));
        BoardService restarted = new BoardService(
                restored,
                new HerdCycles(restored),
                new StockReader(BoardTuning.stockReadBudget(), BoardTuning.stockBlockBudget()),
                BoardSources.create(),
                journal);
        helper.assertTrue(
                restarted.board().ledger().has(BoardCase.BUILD_HOUSE, 1),
                "После рестарта ledger обязан помнить выполненный дом уровня 1 из журнала: "
                        + restarted.board().ledger().keys());
        helper.assertTrue(
                restarted.board().ledger().has(BoardCase.BUILD_PEN, 1),
                "После рестарта ledger обязан помнить и загон из новой версии конфигурации");
        restarted.close();
        delete(log);
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static void drainForeignTickets(BoardService board) {
        for (BoardTicket ticket : board.board().all()) {
            BoardStatus status = board.board().statusOf(ticket.id()).orElse(null);
            if (status != BoardStatus.DONE && status != BoardStatus.CANCELLED) {
                board.board().cancel(ticket.id());
            }
        }
    }

    private static void completeTicket(BoardService board, String id, SpiritEntity spirit, long now) {
        if (board.board().statusOf(id).orElse(null) == BoardStatus.PENDING) {
            board.board().claim(id, spirit.getUUID(), now);
        }
        board.board().complete(id);
    }

    private static void markSpirit(GameTestHelper helper, BlockPos relative) {
        PlacedBlocksData.get(helper.getLevel()).markSpirit(helper.absolutePos(relative));
    }

    private static void assertPenLevelOne(GameTestHelper helper, BlockCoord at) {
        TaskNode plan =
                TicketPlans.plan(BoardCase.BUILD_PEN, 1, TicketTarget.at(at).withSpecies(Species.COW));
        helper.assertTrue(
                plan instanceof SequenceNode sequence
                        && sequence.steps().stream()
                                .anyMatch(step -> step instanceof ScriptCall call
                                        && "build_schematic".equals(call.script())
                                        && "pen_4x5".equals(call.params().get("file"))),
                "Уровень 1 загона строится схемой pen_4x5, фактически " + plan);
        Preset preset = DataTables.INSTANCE.presets().current().byId("pen_4x5").orElse(null);
        helper.assertTrue(preset != null, "Схема pen_4x5 обязана быть в библиотеке пресетов: иначе загон не построить");
        helper.assertTrue(
                preset.sizeX() == 4 && preset.sizeZ() == 5,
                "Загон уровня 1 — минимум 4×5, фактически " + preset.sizeX() + "×" + preset.sizeZ());
        helper.assertTrue(
                preset.schematic().blocks().stream()
                        .anyMatch(block -> block.block().contains("fence_gate")),
                "Загон уровня 1 обязан иметь калитку: без неё животное не загнать");
    }

    private static void assertImmovableRefused(GameTestHelper helper) {
        try {
            RelocatePlan.assertMovable(BoardCase.BUILD_MINE);
            helper.fail("Перенос входа в шахту обязан быть отвергнут: постройка неподвижна");
        } catch (IllegalArgumentException expected) {
            helper.assertTrue(
                    expected.getMessage().contains("неподвижна"),
                    "Отказ переноса шахты обязан называть причину «неподвижна», фактически: " + expected.getMessage());
        }
    }

    private static int countFence(GameTestHelper helper, BlockCoord origin, int width, int depth) {
        int count = 0;
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                if (helper.getLevel()
                                .getBlockState(new BlockPos(origin.x() + dx, origin.y(), origin.z() + dz))
                                .getBlock()
                        instanceof FenceBlock) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean hasGate(GameTestHelper helper, BlockCoord origin, int width, int depth) {
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                if (helper.getLevel()
                                .getBlockState(new BlockPos(origin.x() + dx, origin.y(), origin.z() + dz))
                                .getBlock()
                        instanceof FenceGateBlock) {
                    return true;
                }
            }
        }
        return false;
    }

    private static SpiritEntity spawnAt(GameTestHelper helper, BlockPos relative, BlockPos home) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        spirit.setHome(absolute(helper, home));
        return spirit;
    }

    private static String captureId(BoardService board, BoardCase boardCase, String species) {
        for (BoardTicket ticket : board.board().all()) {
            if (ticket.boardCase() == boardCase && ticket.id().startsWith("capture:" + species + ":")) {
                return ticket.id();
            }
        }
        return null;
    }

    private static int activeCaptures(BoardService board, String species) {
        int count = 0;
        for (BoardTicket ticket : board.board().all()) {
            if (ticket.boardCase() != BoardCase.CAPTURE_ANIMAL || !ticket.id().startsWith("capture:" + species + ":")) {
                continue;
            }
            BoardStatus status = board.board().statusOf(ticket.id()).orElse(null);
            if (status == BoardStatus.PENDING
                    || status == BoardStatus.WAITING_RESOURCES
                    || status == BoardStatus.ASSIGNED) {
                count++;
            }
        }
        return count;
    }

    private static long active(BoardService board, BoardCase boardCase) {
        return board.board().all().stream()
                .filter(ticket -> ticket.boardCase() == boardCase)
                .filter(ticket -> {
                    BoardStatus status = board.board().statusOf(ticket.id()).orElse(null);
                    return status == BoardStatus.PENDING
                            || status == BoardStatus.WAITING_RESOURCES
                            || status == BoardStatus.ASSIGNED;
                })
                .count();
    }

    private static BoardLogEntry entryOf(BoardService board, Path log, String ticketId) {
        try {
            for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                BoardLogEntry entry = BoardLog.parse(line);
                if (entry.ticketId().equals(ticketId) && entry.event() == BoardLog.Event.DONE) {
                    return entry;
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Журнал теста не прочитан: " + error.getMessage(), error);
        }
        return null;
    }

    private static void reloadBoard(GameTestHelper helper, String boardJson, String packName) {
        MinecraftServer server = helper.getLevel().getServer();
        Path pack = packDir(helper, packName);
        writePack(pack, boardJson);
        List<PackResources> packs = new ArrayList<>(server.getPackRepository().openAllSelected());
        packs.add(new PathPackResources(
                new PackLocationInfo(
                        "file/" + packName, Component.literal(packName), PackSource.WORLD, Optional.empty()),
                pack));
        MultiPackResourceManager manager = new MultiPackResourceManager(PackType.SERVER_DATA, packs);
        DataReload.reloadFrom(manager);
        manager.close();
    }

    private static Path packDir(GameTestHelper helper, String packName) {
        return helper.getLevel()
                .getServer()
                .getWorldPath(LevelResource.DATAPACK_DIR)
                .resolve(packName);
    }

    private static void writePack(Path pack, String boardJson) {
        Path dir = pack.resolve("data/theminespirit/board");
        try {
            Files.createDirectories(dir);
            Files.writeString(
                    pack.resolve("pack.mcmeta"),
                    "{\"pack\": {\"pack_format\": 48, \"description\": \"board v2 gametest\"}}\n",
                    StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("board.json"), boardJson, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("датапак теста не записан: " + error.getMessage(), error);
        }
    }

    private static Path journalPath(GameTestHelper helper, String name) {
        return helper.getLevel()
                .getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("theminespirit")
                .resolve("board-v2-" + name + "-" + UUID.randomUUID() + ".jsonl");
    }

    private static BoardService isolated(ServerLevel level, Path log) {
        JournalService journal = new JournalService(log);
        Board board = new Board(
                level::getGameTime, (event, ticket, holder, tick) -> journal.record(event, ticket, holder, tick));
        return new BoardService(
                board,
                new HerdCycles(board),
                new StockReader(BoardTuning.stockReadBudget(), BoardTuning.stockBlockBudget()),
                BoardSources.create(),
                journal);
    }

    private static void close(BoardService board, Path log, GameTestHelper helper) {
        board.close();
        delete(log);
        GameTestFixtures.finish(helper);
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            LOGGER.warn("Журнал теста не удалён ({}): {}", path, error.getMessage());
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException error) {
            LOGGER.warn("Датапак теста не удалён ({}): {}", root, error.getMessage());
        }
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
