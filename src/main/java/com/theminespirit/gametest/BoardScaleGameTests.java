package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
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
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Масштаб доски: двадцать духов и шесть заявок за 1000 тиков. Доска обязана работать раз в
 * период, читать склад бюджетом и выдавать не больше, чем свободных духов, — иначе снимок
 * запасов грузит чанки на главном потоке и полный GameTest идёт 41 минуту вместо 3–5.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true)
public final class BoardScaleGameTests {
    private static final int SPIRITS = 20;
    private static final int TICKETS = 6;
    private static final int OBSERVED_TICKS = 1000;
    private static final int REQUIRED_DIRT = 9;
    // [флак] наблюдение 1000 тиков (откат провала 200 + период пересборки 100): запас 2×
    private static final int TIMEOUT_TICKS = 2000;

    private BoardScaleGameTests() {
    }

    @GameTest(template = "arena", batch = "board_scale", timeoutTicks = TIMEOUT_TICKS)
    public static void scale_TwentySpiritsSixTickets_WorkPerThousandTicksStaysWithinLimits(
            GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        List<SpiritEntity> spirits = new ArrayList<>();
        List<BlockCoord> homes = new ArrayList<>();
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 4; column++) {
                BlockPos spot = new BlockPos(1 + column * 2, 1, 1 + row * 2);
                SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), spot);
                BlockCoord home = absolute(helper, spot);
                spirit.setHome(home);
                spirits.add(spirit);
                homes.add(home);
            }
        }
        helper.assertTrue(spirits.size() == SPIRITS,
                "в тесте на масштаб " + SPIRITS + " духов, создано " + spirits.size());
        BoardService board = BoardRegistry.get(level);
        int baseRebuilds = board.rebuilds();
        int baseClaims = board.claims();
        long baseReads = board.stockBlockReads();
        long baseContainers = board.stockContainers();
        for (int ticket = 0; ticket < TICKETS; ticket++) {
            SpiritEntity owner = spirits.get(ticket);
            board.submit(new BoardTicket("scale_build_" + ticket, BoardCase.BUILD_HOUSE,
                    Optional.of(owner.getUUID()), 50, level.getGameTime(), 1, BoardTicket.ALWAYS_CAUSE,
                    List.of(), Map.of("dirt", REQUIRED_DIRT)),
                    TicketTarget.at(homes.get(ticket)));
        }
        helper.succeedWhen(() -> {
            for (SpiritEntity spirit : spirits) {
                helper.assertTrue(spirit.tickCount >= OBSERVED_TICKS,
                        "дух ещё не отработал " + OBSERVED_TICKS + " тиков: " + spirit.tickCount);
            }
            int rebuilds = board.rebuilds() - baseRebuilds;
            int claims = board.claims() - baseClaims;
            long reads = board.stockBlockReads() - baseReads;
            long containers = board.stockContainers() - baseContainers;
            int maxRebuilds = (int) (OBSERVED_TICKS / BoardTuning.boardIntervalTicks()) + 1;
            long maxReads = (long) BoardTuning.stockBlockBudget() * maxRebuilds;
            long maxContainers = (long) BoardTuning.stockReadBudget() * maxRebuilds;
            long maxClaims = (long) maxRebuilds * SPIRITS * BoardTuning.maxActivePerSpirit();
            helper.assertTrue(rebuilds > 0 && reads > 0 && claims > 0,
                    "доска обязана работать, иначе предел ничего не доказывает: пересборок "
                            + rebuilds + ", чтений " + reads + ", выдач " + claims);
            helper.assertTrue(rebuilds <= maxRebuilds,
                    "пересборок за " + OBSERVED_TICKS + " тиков не больше " + maxRebuilds
                            + " (раз в период " + BoardTuning.boardIntervalTicks() + " тиков), было "
                            + rebuilds + ": доска не делает работу каждый тик");
            helper.assertTrue(reads <= maxReads,
                    "чтений блоков складом не больше " + maxReads + " (бюджет "
                            + BoardTuning.stockBlockBudget() + " × " + maxRebuilds + " пересборок), было "
                            + reads + ": снимок запасов обязан укладываться в бюджет");
            helper.assertTrue(containers <= maxContainers,
                    "прочитанных контейнеров не больше " + maxContainers + " (бюджет "
                            + BoardTuning.stockReadBudget() + " × " + maxRebuilds + "), было " + containers);
            helper.assertTrue(claims <= maxClaims,
                    "выдач не больше " + maxClaims + " (свободные духи × пересборки), было " + claims
                            + ": доска не выдаёт задания занятым");
            for (SpiritEntity spirit : spirits) {
                spirit.discard();
            }
            GameTestFixtures.finish(helper);
        });
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
