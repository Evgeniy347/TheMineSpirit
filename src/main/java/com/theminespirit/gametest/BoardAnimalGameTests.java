package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.HerdWatch;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.block.Blocks;
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
@SettlementSystems(board = true)
public final class BoardAnimalGameTests {
    private static final String BATCH = "board_animal";
    private static final Set<BoardCase> LIFECYCLE = EnumSet.of(BoardCase.CAPTURE_ANIMAL,
            BoardCase.BUILD_PEN, BoardCase.LURE_ANIMAL, BoardCase.DISMANTLE_SHELTER);

    private BoardAnimalGameTests() {
    }

    @BeforeBatch(batch = BATCH)
    public static void stopMobsTargetingSpirits(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = BATCH)
    public static void restoreMobTargeting(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(true);
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 100)
    public static void animal_NearBase_LifecycleThroughGateWithoutShelter(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.setHome(absolute(helper, new BlockPos(4, 1, 4)));
        Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(5, 1, 5));
        sheep.setNoAi(true);
        ServerLevel level = helper.getLevel();
        BoardService board = BoardRegistry.get(level);
        long now = level.getGameTime();

        drainLifecycle(board, spirit.getUUID(), now);
        HerdWatch.tick(level, board, List.of(spirit), now);
        long captures = activeCount(board, BoardCase.CAPTURE_ANIMAL);
        helper.assertTrue(captures >= 1,
                "Живое животное рядом с базой обязано поднять CAPTURE_ANIMAL, активных "
                        + captures + ": источник «животное найдено» молчит");
        HerdWatch.tick(level, board, List.of(spirit), now);
        helper.assertTrue(activeCount(board, BoardCase.CAPTURE_ANIMAL) == captures,
                "Повторный обзор при живом цикле не плодит второй отлов: было " + captures);
        String captureId = firstCaptureId(board);
        helper.assertTrue(captureId != null,
                "Задание отлова обязано найтись на доске: иначе нечего вести по циклу");
        closeTicket(board, captureId, spirit.getUUID(), now);
        board.completeCapture(captureId);
        helper.assertTrue(activeCount(board, BoardCase.BUILD_PEN) == 1,
                "Отлов завершён — на доске ровно один BUILD_PEN: цикл «отлов → загон» идёт по порядку");

        closeTicket(board, "sheep_pen", spirit.getUUID(), now);
        board.completePen("sheep_pen");
        BoardTicket lure = findTicket(board, BoardCase.LURE_ANIMAL);
        helper.assertTrue(lure != null,
                "Загон построен — на доске LURE_ANIMAL: третье звено цикла");
        helper.assertTrue(Map.of("wheat", 1).equals(lure.requiredItems()),
                "Гейт «приманить» — одна пшеница, фактически " + lure.requiredItems()
                        + ": без приманки дух ушёл бы в пустой загон");
        helper.assertTrue(board.board().claimable(new StockView(Map.of()), now).stream()
                        .noneMatch(ticket -> ticket.boardCase() == BoardCase.LURE_ANIMAL),
                "Без приманки «приманить» не выдаётся: гейт держит задание в очереди");
        helper.assertTrue(board.board().statusOf("sheep_lure").orElse(null)
                        == BoardStatus.WAITING_RESOURCES,
                "Наблюдатель видит «приманить» как WAITING_RESOURCES, статус "
                        + board.board().statusOf("sheep_lure").orElse(null));
        helper.assertTrue(board.board().claimable(new StockView(Map.of("wheat", 1)), now).stream()
                        .anyMatch(ticket -> ticket.boardCase() == BoardCase.LURE_ANIMAL),
                "С пшеницей в запасах «приманить» выдаётся: доска сама снимает гейт");

        board.completeLure(Species.SHEEP);
        helper.assertTrue(activeCount(board, BoardCase.DISMANTLE_SHELTER) == 0,
                "Животное в загоне — разбирать нечего: отлов приманкой не ставит укрытия (упрощение 44.4)");
        board.notifyAnimalDied(Species.SHEEP);
        helper.assertTrue(board.board().statusOf("sheep_lure").orElse(null)
                        == BoardStatus.CANCELLED,
                "Гибель снимает «приманить», статус "
                        + board.board().statusOf("sheep_lure").orElse(null));
        helper.assertTrue(activeCount(board, BoardCase.DISMANTLE_SHELTER) == 0,
                "После гибели разбирать нечего: укрытия нет");
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static long activeCount(BoardService service, BoardCase boardCase) {
        return service.board().all().stream()
                .filter(ticket -> ticket.boardCase() == boardCase)
                .filter(ticket -> service.board().statusOf(ticket.id())
                        .map(status -> status == BoardStatus.PENDING
                                || status == BoardStatus.WAITING_RESOURCES
                                || status == BoardStatus.ASSIGNED)
                        .orElse(false))
                .count();
    }

    private static String firstCaptureId(BoardService service) {
        String pending = service.board().all().stream()
                .filter(ticket -> ticket.boardCase() == BoardCase.CAPTURE_ANIMAL)
                .filter(ticket -> service.board().statusOf(ticket.id()).orElse(null)
                        == BoardStatus.PENDING)
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

    /** Закрывает чужое или своё активное задание цикла: выданное — выполнить, ждущее — отменить. */
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

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
