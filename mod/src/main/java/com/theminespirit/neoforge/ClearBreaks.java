package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.mining.api.BreakSafety;
import com.theminespirit.core.mining.api.MiningSafety;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class ClearBreaks {
    enum Result {
        BREAKING,
        BROKEN,
        GONE
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PROTECTED_LOGGED = 10;

    private final ClearAreaSession session;
    private final Map<String, Integer> brokenKinds = new TreeMap<>();
    private String brokenKind = "";
    private int vanished;

    ClearBreaks(ClearAreaSession session) {
        this.session = session;
    }

    Map<String, Integer> brokenKinds() {
        return brokenKinds;
    }

    int vanished() {
        return vanished;
    }

    void prepare() {
        if (!session.demolish && PlanCells.keep(session.level, ClearAreaSession.toPos(session.currentTarget))) {
            session.protectedSkipped++;
            if (session.protectedSkipped <= PROTECTED_LOGGED) {
                LOGGER.info(
                        "[clear] {}: {} — постройка плана поселения, не ломается",
                        session.spirit.getName().getString(),
                        session.currentTarget);
            }
            skip();
            return;
        }
        if (!(MiningSafety.canBreak(session.currentTarget, session.safety) instanceof BreakSafety.Safe)) {
            session.unsafe++;
            skip();
            return;
        }
        BlockBreaker.Begin begin =
                session.breaker.begin(ClearAreaSession.toPos(session.currentTarget), false, session.demolish);
        if (begin instanceof BlockBreaker.Protected kept) {
            session.protectedSkipped++;
            if (session.protectedSkipped <= PROTECTED_LOGGED) {
                LOGGER.info(
                        "[clear] {}: защищено {} — {} ({})",
                        session.spirit.getName().getString(),
                        session.currentTarget,
                        kept.policy(),
                        kept.reason());
            }
            skip();
            return;
        }
        if (begin instanceof BlockBreaker.Refused refused) {
            if (refused.reason() == BlockBreaker.RefuseReason.NEED_TOOL) {
                String tool = neededTool(session.level, ClearAreaSession.toPos(session.currentTarget));
                if (tool != null) {
                    session.needTool(tool);
                    skip();
                    return;
                }
                session.fail(refused.message());
                return;
            }
            skip();
            session.blocked.failed(session.currentTarget);
            return;
        }
        brokenKind = ClearBlocked.blockAt(session.level, session.currentTarget);
        session.permit.record(session.level, ClearAreaSession.toPos(session.currentTarget));
        session.state = ClearAreaSession.State.BREAK;
    }

    Result tick() {
        return switch (session.breaker.tick()) {
            case BREAKING -> Result.BREAKING;
            case BROKEN -> {
                session.removed++;
                session.felling.removed(session.currentTarget);
                session.blocked.reached(session.currentTarget);
                brokenKinds.merge(brokenKind, 1, Integer::sum);
                session.tally.removed();
                session.collector = new DropCollector(
                        session.spirit,
                        IdFilter.matchAll(),
                        session.currentTarget,
                        SpiritConfig.collectRadius(),
                        PickupTuning.DEFAULT,
                        OverflowMode.RETURN_TO_BASE);
                session.state = ClearAreaSession.State.COLLECT;
                yield Result.BROKEN;
            }
            default -> {
                vanished++;
                session.releaseCurrent();
                session.state = ClearAreaSession.State.PICK;
                yield Result.GONE;
            }
        };
    }

    private void skip() {
        session.tally.skipped();
        session.releaseCurrent();
        session.state = ClearAreaSession.State.PICK;
    }

    static @Nullable String neededTool(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return "pickaxe";
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return "axe";
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return "shovel";
        }
        return null;
    }
}
