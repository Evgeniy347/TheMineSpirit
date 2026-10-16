package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.FirstStepsPlan;
import com.theminespirit.core.board.SettlementFacts;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;

/**
 * Генератор первых шагов: по фактам поселения ставит очередное задание из цепочки.
 */
public final class FirstStepsRunner {
    private static final int SCAN_RADIUS = 16;

    private FirstStepsRunner() {
    }

    public static void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        BlockCoord home = homeOf(spirits);
        SettlementFacts facts = buildFacts(level, home, service);
        Optional<BoardCase> next = FirstStepsPlan.next(facts);
        if (next.isEmpty()) {
            return;
        }
        BoardCase caseValue = next.orElseThrow();
        if (hasActive(service, caseValue)) {
            return;
        }
        String id = "first:" + caseValue.name().toLowerCase(Locale.ROOT);
        service.submit(new BoardTicket(id, caseValue, Optional.empty(), BoardRules.priorityOf(caseValue), now,
                BoardTuning.DEFAULT_TTL_TICKS, List.of(), Map.of()), TicketTarget.at(home));
    }

    private static boolean hasActive(BoardService service, BoardCase boardCase) {
        return service.board().all().stream()
                .filter(t -> t.boardCase() == boardCase)
                .anyMatch(t -> service.board().statusOf(t.id())
                        .map(s -> s == BoardStatus.PENDING || s == BoardStatus.WAITING_RESOURCES
                                || s == BoardStatus.ASSIGNED)
                        .orElse(false));
    }

    /**
     * Факты поселения из снимка мира: деревья (брёвна/листва — земля не зачищена), вход в шахту
     * (лестница спуска — знак построенного входа), забор (то же множество ограждений, что видит
     * {@code FenceWatch}, иначе runner и событие говорили бы о разных заборах). Сканирование
     * бюджетировано радиусом и вызывается раз в пересборку, а не каждый тик.
     */
    private static SettlementFacts buildFacts(ServerLevel level, BlockCoord home, BoardService service) {
        boolean hasHome = true;
        boolean groundsCleared = true;
        int chests = 0;
        boolean craftingStation = false;
        boolean tools = false;
        boolean mineEntrance = false;
        boolean fence = false;
        boolean lighting = false;
        boolean houses = false;
        BlockPos start = new BlockPos(home.x(), home.y(), home.z());
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                if (dx * dx + dz * dz > SCAN_RADIUS * SCAN_RADIUS) {
                    continue;
                }
                BlockPos pos = start.offset(dx, 0, dz);
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos check = pos.offset(0, dy, 0);
                    if (!level.isLoaded(check)) {
                        continue;
                    }
                    var state = level.getBlockState(check);
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                        groundsCleared = false;
                    }
                    if (state.is(Blocks.LADDER)) {
                        mineEntrance = true;
                    }
                    if (state.is(Blocks.CHEST)) {
                        chests++;
                    }
                    if (state.is(Blocks.CRAFTING_TABLE)) {
                        craftingStation = true;
                    }
                    if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
                        lighting = true;
                    }
                    if (FenceWatch.isFence(state)) {
                        fence = true;
                    }
                    if (state.getBlock() instanceof StairBlock || state.getBlock() instanceof SlabBlock) {
                        houses = true;
                    }
                }
            }
        }
        tools = service.stock().available().containsKey("minecraft:wooden_pickaxe")
                || service.stock().available().containsKey("wooden_pickaxe");
        return new SettlementFacts(hasHome, groundsCleared, chests, craftingStation, tools, mineEntrance, fence,
                lighting, houses);
    }

    private static BlockCoord homeOf(List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            Optional<BlockCoord> home = spirit.home();
            if (home.isPresent()) {
                return home.orElseThrow();
            }
        }
        return new BlockCoord(0, 64, 0);
    }
}
