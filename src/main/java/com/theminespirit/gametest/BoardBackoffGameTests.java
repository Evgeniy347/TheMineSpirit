package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardBackoffGameTests {
    private static final BoardCase CASE = BoardCase.STOCK_CHESTS;

    private BoardBackoffGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void backoff_AlwaysFailingCase_NotIssuedBeforePause(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        long[] now = {level.getGameTime()};
        Board board = new Board(() -> now[0]);
        long previousPause = 0;

        for (int attempt = 1; attempt < BoardTuning.caseFailLimit(); attempt++) {
            String failed = "backoff_fail_" + attempt;
            board.submit(ticket(failed, now[0]));
            helper.assertTrue(
                    board.claim(failed, spirit.getUUID(), now[0]) == BoardOutcome.CLAIMED,
                    "Попытка " + attempt + ": задание обязано выдаться духу, иначе провал не к чему привязать");
            helper.assertTrue(board.fail(failed), "Попытка " + attempt + ": провал обязан записаться");

            long pause = board.casePauseTicks(spirit.getUUID(), CASE);
            helper.assertTrue(pause > 0, "После провала у пары дух+кейс есть пауза, фактически " + pause);
            helper.assertTrue(
                    attempt == 1 || pause > previousPause,
                    "Ступень " + attempt + " длиннее прошлой (" + pause + " > " + previousPause
                            + "): иначе откат не растёт и дух долбит задание с той же частотой");
            helper.assertTrue(
                    pause <= BoardTuning.caseFailPauseCapTicks(),
                    "Пауза " + pause + " не выше потолка " + BoardTuning.caseFailPauseCapTicks());
            previousPause = pause;

            String next = "backoff_next_" + attempt;
            board.submit(ticket(next, now[0]));
            now[0] += pause - 1;
            helper.assertTrue(
                    board.claim(next, spirit.getUUID(), now[0]) == BoardOutcome.SPIRIT_PAUSED,
                    "За тик до конца паузы (" + pause + ") тот же дух не получает тот же кейс: "
                            + "иначе цикл задания повторяется каждой пересборкой");
            now[0] += 1;
            helper.assertTrue(
                    board.claim(next, spirit.getUUID(), now[0]) == BoardOutcome.CLAIMED,
                    "Ровно через паузу кейс снова доступен этому духу: пауза не снимает его навсегда");
            board.cancel(next);
        }

        spirit.discard();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static BoardTicket ticket(String id, long now) {
        return new BoardTicket(
                id,
                CASE,
                Optional.empty(),
                BoardRules.priorityOf(CASE),
                now,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }
}
