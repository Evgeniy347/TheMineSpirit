package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.CraterProfiles;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Масштаб доски v2: двадцать духов, три вида животных, воронки и перенос за
 * {@link #OBSERVED_TICKS} тиков. Тест считает операции, а не время: пересборки идут
 * по интервалу, склад читается бюджетом, журнал пишет по строке на переход,
 * проверка актуальности — один вызов на пересборку, а не на тик.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true)
public final class BoardV2ScaleGameTests {
    private static final String BATCH = "board_v2_scale";
    private static final int SPIRITS = 20;
    private static final int OBSERVED_TICKS = 400;
    private static final int TIMEOUT_TICKS = 1000;
    private static final int CRATERS = 3;

    private BoardV2ScaleGameTests() {}

    @BeforeBatch(batch = BATCH)
    public static void prepareBatch(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = BATCH)
    public static void restoreBatch(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(true);
    }

    // [флак] наблюдение 400 тиков (пересборки по 100, скан стада по 200) плюс старт сцены:
    // под нагрузкой машины 400 тиков тайм-аута мало, запас 2,5×.
    @GameTest(template = "arena", batch = BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void scale_TwentySpiritsThreeSpecies_CountersStayWithinPeriod(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 4; column++) {
                BlockPos spot = new BlockPos(1 + column * 2, 1, 1 + row * 2);
                SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), spot);
                BlockCoord home = absolute(helper, spot);
                spirit.setHome(home);
                spirits.add(spirit);
            }
        }
        List<Animal> animals = new ArrayList<>();
        animals.add(helper.spawn(EntityType.COW, new BlockPos(7, 1, 7)));
        animals.add(helper.spawn(EntityType.PIG, new BlockPos(7, 1, 8)));
        animals.add(helper.spawn(EntityType.CHICKEN, new BlockPos(8, 1, 7)));
        for (Animal animal : animals) {
            animal.setNoAi(true);
        }
        BoardService board = BoardRegistry.get(level);
        drainForeignTickets(board);
        long now = level.getGameTime();
        for (int crater = 0; crater < CRATERS; crater++) {
            BlockPos hole = new BlockPos(crater, 0, 8);
            helper.setBlock(hole, Blocks.AIR);
            BlockCoord cell = absolute(helper, hole);
            String cause = CraterProfiles.cause(cell);
            board.craters().remember(cause, List.of(cell));
            board.submit(
                    new BoardTicket(
                            "scale_crater_" + crater,
                            BoardCase.FILL_CRATER,
                            Optional.of(UUID.randomUUID()),
                            BoardRules.priorityOf(BoardCase.FILL_CRATER),
                            now,
                            1,
                            cause,
                            List.of(),
                            Map.of("dirt", 9)),
                    TicketTarget.at(cell));
        }
        BlockCoord from = absolute(helper, new BlockPos(2, 1, 6));
        BlockCoord to = absolute(helper, new BlockPos(4, 1, 1));
        board.submit(
                new BoardTicket(
                        "scale_relocate",
                        BoardCase.RELOCATE_BUILDING,
                        Optional.of(UUID.randomUUID()),
                        60,
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(to).withRelocate(from, to).withScheme("pen_4x5"));

        int baseRebuilds = board.rebuilds();
        int baseClaims = board.claims();
        int baseRetains = board.retains();
        int baseScans = board.herdScans();
        long baseReads = board.stockBlockReads();
        int baseEnqueued = board.journal().enqueued();

        helper.succeedWhen(() -> {
            for (SpiritEntity spirit : spirits) {
                helper.assertTrue(
                        spirit.tickCount >= OBSERVED_TICKS,
                        "дух ещё не отработал " + OBSERVED_TICKS + " тиков: " + spirit.tickCount);
            }
            board.journal().flushAll();
            int rebuilds = board.rebuilds() - baseRebuilds;
            int claims = board.claims() - baseClaims;
            int retains = board.retains() - baseRetains;
            int scans = board.herdScans() - baseScans;
            long reads = board.stockBlockReads() - baseReads;
            int enqueued = board.journal().enqueued() - baseEnqueued;
            int maxRebuilds = (int) (OBSERVED_TICKS / BoardTuning.boardIntervalTicks()) + 1;
            long maxReads = (long) BoardTuning.stockBlockBudget() * maxRebuilds;
            int maxScans = (int) (OBSERVED_TICKS / BoardTuning.herdScanIntervalTicks()) + 1;
            long maxClaims = (long) maxRebuilds * SPIRITS * BoardTuning.maxActivePerSpirit();
            helper.assertTrue(
                    rebuilds > 0 && claims > 0 && enqueued > 0,
                    "доска обязана работать, иначе предел ничего не доказывает: пересборок " + rebuilds + ", выдач "
                            + claims + ", записей журнала " + enqueued);
            helper.assertTrue(
                    rebuilds <= maxRebuilds,
                    "пересборок за " + OBSERVED_TICKS + " тиков не больше " + maxRebuilds
                            + " (раз в " + BoardTuning.boardIntervalTicks() + " тиков), было "
                            + rebuilds + ": доска не пересобирается каждый тик");
            helper.assertTrue(
                    reads <= maxReads,
                    "чтений блоков складом не больше " + maxReads + " (бюджет " + BoardTuning.stockBlockBudget() + " × "
                            + maxRebuilds + "), было " + reads);
            helper.assertTrue(
                    scans <= maxScans,
                    "сканов животных не больше " + maxScans + " (раз в "
                            + BoardTuning.herdScanIntervalTicks() + " тиков), было " + scans
                            + ": тяжёлый scan_entities не идёт каждый тик");
            helper.assertTrue(
                    retains <= rebuilds,
                    "проверок актуальности не больше пересборок: " + retains + " при " + rebuilds
                            + " — retain каждый тик взвинтил бы счётчики и чтение профилей воронок");
            helper.assertTrue(
                    retains == rebuilds,
                    "ровно одна проверка актуальности на пересборку: " + retains + " при " + rebuilds);
            helper.assertTrue(
                    claims <= maxClaims,
                    "выдач не больше " + maxClaims + " (свободные духи × пересборки), было " + claims);
            helper.assertTrue(
                    board.journal().written() == board.journal().enqueued(),
                    "строк журнала ровно по числу переходов: на диске "
                            + board.journal().written()
                            + ", переходов " + board.journal().enqueued()
                            + ": потерянная или задвоенная строка ломает журнал");
            for (BoardTicket ticket : board.board().all()) {
                if (ticket.id().startsWith("scale_")) {
                    board.cancel(level, ticket.id());
                }
            }
            for (SpiritEntity spirit : spirits) {
                spirit.discard();
            }
            for (Animal animal : animals) {
                animal.discard();
            }
            GameTestFixtures.finish(helper);
        });
    }

    private static void drainForeignTickets(BoardService board) {
        for (BoardTicket ticket : board.board().all()) {
            board.board().cancel(ticket.id());
        }
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
