package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.BoardSources;
import com.theminespirit.neoforge.board.HerdWatch;
import com.theminespirit.neoforge.board.JournalService;
import com.theminespirit.neoforge.board.StockReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Жизненный цикл животного на доске: животное рядом с базой → CAPTURE_ANIMAL один раз;
 * отлов → BUILD_PEN; загон → LURE_ANIMAL с гейтом по приманке; укрытия из блоков нет (упрощение 44.4),
 * поэтому разбирать нечего; гибель → «приманить» снято. Всё синхронно: доска — чистая очередь,
 * succeedWhen здесь не нужен (он завершает тест при первом нормальном выходе).
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardAnimalGameTests {
    private static final Set<BoardCase> LIFECYCLE = EnumSet.of(
            BoardCase.CAPTURE_ANIMAL, BoardCase.BUILD_PEN, BoardCase.LURE_ANIMAL, BoardCase.DISMANTLE_SHELTER);

    private BoardAnimalGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 100, board = true, calmMobs = true)
    public static void animal_NearBase_LifecycleThroughGateWithoutShelter(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.setHome(absolute(helper, new BlockPos(4, 1, 4)));
        Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(5, 1, 5));
        sheep.setNoAi(true);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level);
        long now = level.getGameTime();

        drainLifecycle(board, spirit.getUUID(), now);
        HerdWatch.tick(level, board, List.of(spirit), now);
        long captures = activeCount(board, BoardCase.CAPTURE_ANIMAL);
        helper.assertTrue(
                captures == 0,
                "Без загона отлов не ставится: загон нужен до поимки, иначе дух получает "
                        + "невыполнимое задание и падает через 100 тиков, активных " + captures);
        BlockCoord home = absolute(helper, new BlockPos(4, 1, 4));
        buildPen(board, spirit, home, now);
        HerdWatch.tick(level, board, List.of(spirit), now);
        captures = activeCount(board, BoardCase.CAPTURE_ANIMAL);
        helper.assertTrue(
                captures >= 1,
                "Загон стоит — живое животное рядом с базой обязано поднять CAPTURE_ANIMAL, активных " + captures
                        + ": источник «животное найдено» молчит");
        HerdWatch.tick(level, board, List.of(spirit), now);
        helper.assertTrue(
                activeCount(board, BoardCase.CAPTURE_ANIMAL) == captures,
                "Повторный обзор при живом цикле не плодит второй отлов: было " + captures);
        String captureId = firstCaptureId(board);
        helper.assertTrue(captureId != null, "Задание отлова обязано найтись на доске: иначе нечего вести по циклу");
        BoardTicket captureTicket = findTicket(board, BoardCase.CAPTURE_ANIMAL);
        helper.assertTrue(
                captureTicket != null && captureTicket.dependsOn().contains("sheep_pen"),
                "Отлов зависит от задания загона: без зависимости доска выдала бы поимку раньше места");
        closeTicket(board, captureId, spirit.getUUID(), now);
        board.completeCapture(captureId);
        helper.assertTrue(
                activeCount(board, BoardCase.BUILD_PEN) == 1,
                "Отлов завершён — на доске ровно один BUILD_PEN: цикл «отлов → загон» идёт по порядку");

        closeTicket(board, "sheep_pen", spirit.getUUID(), now);
        board.completePen("sheep_pen");
        BoardTicket lure = findTicket(board, BoardCase.LURE_ANIMAL);
        helper.assertTrue(lure != null, "Загон построен — на доске LURE_ANIMAL: третье звено цикла");
        helper.assertTrue(
                Map.of("wheat", 1).equals(lure.requiredItems()),
                "Гейт «приманить» — одна пшеница, фактически " + lure.requiredItems()
                        + ": без приманки дух ушёл бы в пустой загон");
        helper.assertTrue(
                board.board().claimable(new StockView(Map.of()), now).stream()
                        .noneMatch(ticket -> ticket.boardCase() == BoardCase.LURE_ANIMAL),
                "Без приманки «приманить» не выдаётся: гейт держит задание в очереди");
        helper.assertTrue(
                board.board().statusOf("sheep_lure").orElse(null) == BoardStatus.WAITING_RESOURCES,
                "Наблюдатель видит «приманить» как WAITING_RESOURCES, статус "
                        + board.board().statusOf("sheep_lure").orElse(null));
        helper.assertTrue(
                board.board().claimable(new StockView(Map.of("wheat", 1)), now).stream()
                        .anyMatch(ticket -> ticket.boardCase() == BoardCase.LURE_ANIMAL),
                "С пшеницей в запасах «приманить» выдаётся: доска сама снимает гейт");

        board.completeLure(Species.SHEEP);
        helper.assertTrue(
                activeCount(board, BoardCase.DISMANTLE_SHELTER) == 0,
                "Животное в загоне — разбирать нечего: отлов приманкой не ставит укрытия (упрощение 44.4)");
        board.notifyAnimalDied(Species.SHEEP);
        helper.assertTrue(
                board.board().statusOf("sheep_lure").orElse(null) == BoardStatus.CANCELLED,
                "Гибель снимает «приманить», статус "
                        + board.board().statusOf("sheep_lure").orElse(null));
        helper.assertTrue(
                activeCount(board, BoardCase.DISMANTLE_SHELTER) == 0, "После гибели разбирать нечего: укрытия нет");
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static long activeCount(BoardService service, BoardCase boardCase) {
        return service.board().all().stream()
                .filter(ticket -> ticket.boardCase() == boardCase)
                .filter(ticket -> service.board()
                        .statusOf(ticket.id())
                        .map(status -> status == BoardStatus.PENDING
                                || status == BoardStatus.WAITING_RESOURCES
                                || status == BoardStatus.ASSIGNED)
                        .orElse(false))
                .count();
    }

    private static String firstCaptureId(BoardService service) {
        String pending = service.board().all().stream()
                .filter(ticket -> ticket.boardCase() == BoardCase.CAPTURE_ANIMAL)
                .filter(ticket -> service.board().statusOf(ticket.id()).orElse(null) == BoardStatus.PENDING)
                .map(BoardTicket::id)
                .findFirst()
                .orElse(null);
        if (pending != null) {
            return pending;
        }
        return service.board().all().stream()
                .filter(ticket -> ticket.boardCase() == BoardCase.CAPTURE_ANIMAL)
                .filter(ticket -> {
                    BoardStatus status = service.board().statusOf(ticket.id()).orElse(null);
                    return status == BoardStatus.WAITING_RESOURCES || status == BoardStatus.ASSIGNED;
                })
                .map(BoardTicket::id)
                .findFirst()
                .orElse(null);
    }

    private static BoardTicket findTicket(BoardService service, BoardCase boardCase) {
        return service.board().all().stream()
                .filter(ticket -> ticket.boardCase() == boardCase)
                .findFirst()
                .orElse(null);
    }

    private static void buildPen(BoardService board, SpiritEntity spirit, BlockCoord home, long now) {
        board.submit(
                new BoardTicket(
                        "sheep_pen",
                        BoardCase.BUILD_PEN,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.BUILD_PEN),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(home).withSpecies(Species.SHEEP));
        closeTicket(board, "sheep_pen", spirit.getUUID(), now);
    }

    private static void drainLifecycle(BoardService service, UUID holder, long now) {
        for (BoardTicket ticket : service.board().all()) {
            if (!LIFECYCLE.contains(ticket.boardCase())) {
                continue;
            }
            closeTicket(service, ticket.id(), holder, now);
        }
    }

    private static void closeTicket(BoardService service, String id, UUID holder, long now) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        if (status == BoardStatus.PENDING) {
            service.board().claim(id, holder, now);
            status = service.board().statusOf(id).orElse(null);
        }
        if (status == BoardStatus.ASSIGNED) {
            service.board().complete(id);
        } else if (status == BoardStatus.WAITING_RESOURCES) {
            service.board().cancel(id);
        }
    }

    private static BoardService isolatedBoard(ServerLevel level) {
        Path journalPath = FMLPaths.CONFIGDIR.get().resolve("theminespirit/gametest-board-animal.jsonl");
        try {
            Files.deleteIfExists(journalPath);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        JournalService journal = new JournalService(journalPath);
        Board board = new Board(level::getGameTime);
        return new BoardService(
                board,
                new HerdCycles(board),
                new StockReader(BoardTuning.stockReadBudget(), BoardTuning.stockBlockBudget()),
                BoardSources.create(),
                journal);
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
