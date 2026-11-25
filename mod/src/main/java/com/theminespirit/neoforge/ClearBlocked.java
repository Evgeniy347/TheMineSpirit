package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.api.BuildBox;
import com.theminespirit.core.mining.BreakSkipTally;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.mining.UnreachableCells;
import com.theminespirit.core.mining.api.BreakSafety;
import com.theminespirit.core.mining.api.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.ProtectionMemo;
import com.theminespirit.neoforge.board.PlotCellStructure;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class ClearBlocked {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long SLOW_PROTECTION_SCAN_NANOS = 200_000_000L;
    private static final long PROTECTION_REFRESH_MS = 60_000L;

    record Sweep(
            List<ClearSummary.Leftover> leftover, int unsafe, int noStand, int unreachable, int excluded, int planned) {
        int skipped() {
            return unsafe + noStand + unreachable + excluded + planned;
        }
    }

    private final Level level;
    private final UnreachableCells cells;
    private final String area;

    ClearBlocked(Level level, BuildBox box) {
        this.level = level;
        this.cells = UnreachableCellsStore.of(level);
        this.area = box.minX() + "," + box.minY() + "," + box.minZ() + ".." + box.maxX() + "," + box.maxY() + ","
                + box.maxZ();
    }

    void reached(BlockCoord cell) {
        cells.reached(cell);
    }

    void failed(BlockCoord cell) {
        cells.noteFailure(cell);
    }

    Sweep dropExcluded(List<BlockCoord> remaining) {
        List<ClearSummary.Leftover> leftover = new ArrayList<>();
        int excluded = 0;
        for (int index = remaining.size() - 1; index >= 0; index--) {
            BlockCoord cell = remaining.get(index);
            if (!cells.excluded(cell)) {
                continue;
            }
            excluded++;
            leftover.add(new ClearSummary.Leftover(
                    cell, blockAt(cell), BreakSkipTally.Reason.NO_STAND.title(), cells.failureCount(cell)));
            remaining.remove(index);
        }
        return new Sweep(leftover, 0, 0, 0, excluded, 0);
    }

    static long protectionStamp() {
        long window = System.currentTimeMillis() / PROTECTION_REFRESH_MS;
        return window * 104729L + PlotCellStructure.generation() * 7919L;
    }

    Sweep dropProtected(List<BlockCoord> remaining, SpiritEntity spirit, boolean demolish, ProtectionMemo memo) {
        long started = System.nanoTime();
        int scanned = 0;
        List<ClearSummary.Leftover> leftover = new ArrayList<>();
        int protectedCount = 0;
        for (int index = remaining.size() - 1; index >= 0; index--) {
            BlockCoord cell = remaining.get(index);
            if (memo.certified(cell)) {
                continue;
            }
            scanned++;
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            BlockState state = level.getBlockState(pos);
            String reason = protectedReason(spirit, pos, state, demolish);
            if (reason == null) {
                memo.certify(cell);
                continue;
            }
            protectedCount++;
            leftover.add(new ClearSummary.Leftover(cell, blockAt(cell), reason, 0));
            remaining.remove(index);
        }
        long spent = System.nanoTime() - started;
        if (spent >= SLOW_PROTECTION_SCAN_NANOS) {
            LOGGER.warn(
                    "[clear] {}: проверка защиты {} клеток заняла {} мс — это время стоит серверный тик;"
                            + " искать дорогую проверку в защите поселения, а не в самом духе",
                    spirit.getName().getString(),
                    scanned,
                    spent / 1_000_000L);
        }
        return new Sweep(leftover, 0, 0, 0, 0, protectedCount);
    }

    private static @Nullable String protectedReason(
            SpiritEntity spirit, BlockPos pos, BlockState state, boolean demolish) {
        if (!demolish && PlanCells.keep(spirit.level(), pos)) {
            return "блок постройки плана поселения";
        }
        BlockProtection.Decision decision =
                BreakProtection.check(spirit, BlockBreaker.intent(false, demolish), pos, state, false, false);
        if (decision instanceof BlockProtection.Refused refused) {
            return "защищено: " + refused.reason();
        }
        return null;
    }

    Sweep dropPlanned(List<BlockCoord> remaining) {
        List<ClearSummary.Leftover> leftover = new ArrayList<>();
        int planned = 0;
        for (int index = remaining.size() - 1; index >= 0; index--) {
            BlockCoord cell = remaining.get(index);
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            BlockState state = level.getBlockState(pos);
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (!plannedFence(id, onFenceStrip(pos))) {
                continue;
            }
            planned++;
            leftover.add(new ClearSummary.Leftover(cell, blockAt(cell), "забор плана ограды", 0));
            remaining.remove(index);
        }
        return new Sweep(leftover, 0, 0, 0, 0, planned);
    }

    static boolean plannedFence(String blockId, boolean onStrip) {
        if (!onStrip) {
            return false;
        }
        String path = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
        return "fence".equals(path)
                || "fence_gate".equals(path)
                || path.endsWith("_fence")
                || path.endsWith("_fence_gate");
    }

    private boolean onFenceStrip(BlockPos pos) {
        return level instanceof ServerLevel server
                && SettlementPlotStore.liveSite(server)
                        .filter(site -> site.fenceStrip().contains(pos.getX(), pos.getZ()))
                        .isPresent();
    }

    Sweep exhausted(List<BlockCoord> remaining, LevelSafetyView safety, BlockCoord from) {
        List<ClearSummary.Leftover> leftover = new ArrayList<>();
        int unsafe = 0;
        int noStand = 0;
        int unreachable = 0;
        for (BlockCoord cell : remaining) {
            String reason;
            if (!(MiningSafety.canBreak(cell, safety) instanceof BreakSafety.Safe)) {
                unsafe++;
                reason = BreakSkipTally.Reason.UNBREAKABLE.title();
            } else {
                List<BlockCoord> stands = StandSpots.of(cell, safety, from);
                if (stands.isEmpty()) {
                    noStand++;
                    reason = BreakSkipTally.Reason.NO_STAND.title();
                } else {
                    unreachable++;
                    reason = unreachableReason(stands, from);
                }
            }
            leftover.add(new ClearSummary.Leftover(cell, blockAt(cell), reason, cells.noteFailure(cell)));
        }
        return new Sweep(leftover, unsafe, noStand, unreachable, 0, 0);
    }

    void approachFailure(ClearAreaSession session) {
        boolean exhausted = session.approachFailures.recordFailure(session.currentStand, session.currentTarget);
        boolean retry = !exhausted;
        if (retry) {
            BlockCoord target = session.currentTarget;
            session.releaseCurrent();
            session.remaining.add(0, target);
        } else {
            session.apply(approachFailure(session.currentTarget, session.currentStand, session.approach.refusal()));
            session.releaseCurrent();
        }
        session.state = ClearAreaSession.State.PICK;
    }

    private Sweep approachFailure(BlockCoord target, BlockCoord stand, String refusal) {
        String reason = "срыв подхода к стоянке " + stand + ":" + (refusal == null ? " без причины" : " " + refusal);
        ClearSummary.Leftover step =
                new ClearSummary.Leftover(target, blockAt(target), reason, cells.noteFailure(target));
        return new Sweep(List.of(step), 0, 0, 1, 0, 0);
    }

    private static String unreachableReason(List<BlockCoord> stands, BlockCoord from) {
        for (BlockCoord stand : stands) {
            if (!StandSpots.walkable(stand, from)) {
                return "стоянка " + stand + " на " + (stand.y() - from.y()) + " выше духа — не подняться";
            }
        }
        return "недостижимо";
    }

    void escalate(Logger logger, SpiritEntity spirit, String reason) {
        int attempts = cells.noteAttempt(area);
        if (cells.escalating(area)) {
            logger.warn(
                    "[clear] {}: участок {} не расчищается — попыток {}, причина: {}",
                    spirit.getName().getString(),
                    area,
                    attempts,
                    reason);
        }
    }

    void warnExcluded(Logger logger, SpiritEntity spirit, int excluded, List<ClearSummary.Leftover> leftover) {
        if (excluded == 0) {
            return;
        }
        logger.warn(
                "[clear] {}: участок {} не расчищается — исключено недостижимых {}: {}",
                spirit.getName().getString(),
                area,
                excluded,
                leftover);
    }

    String blockAt(BlockCoord cell) {
        return blockAt(level, cell);
    }

    static String blockAt(Level level, BlockCoord cell) {
        return BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()))
                        .getBlock())
                .getPath();
    }
}
