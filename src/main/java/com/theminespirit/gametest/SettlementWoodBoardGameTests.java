package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardState;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.FirstStepsRunner;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true)
public final class SettlementWoodBoardGameTests {
    private SettlementWoodBoardGameTests() {}

    @GameTest(template = "empty", batch = "settlement_wood_board", timeoutTicks = 20)
    public static void wood_JungleSettlement_DropsOakGather(GameTestHelper helper) {
        BoardService board = BoardRegistry.get(helper.getLevel());
        board.board().restore(new BoardState(List.of(), Set.of()));
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockCoord home = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        for (String item : List.of("oak_fence", "jungle_fence")) {
            board.submit(
                    new BoardTicket(
                            "gather:" + item,
                            BoardCase.GATHER_MATERIALS,
                            Optional.empty(),
                            BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                            helper.getLevel().getGameTime(),
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            Map.of()),
                    TicketTarget.at(home).withSupply(item, 6));
        }
        FirstStepsRunner.dropOtherWood(helper.getLevel(), board, Set.of("jungle"));
        BoardStatus oak = board.board().statusOf("gather:oak_fence").orElse(null);
        BoardStatus jungle = board.board().statusOf("gather:jungle_fence").orElse(null);
        board.board().restore(new BoardState(List.of(), Set.of()));
        helper.assertTrue(
                oak == BoardStatus.CANCELLED && jungle == BoardStatus.PENDING,
                "Порода jungle: дубовая добыча снимается, своя остаётся; oak=" + oak + ", jungle=" + jungle);
        helper.succeed();
    }
}
