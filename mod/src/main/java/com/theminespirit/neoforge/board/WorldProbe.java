package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.audit.AuditRegistry;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.lease.LeaseStates;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class WorldProbe {
    private WorldProbe() {}

    static BuildGap.Probe of(ServerLevel level) {
        return of(TownAuditWatch.peek(level), level);
    }

    static BuildGap.Probe of(@Nullable AuditRegistry registry, ServerLevel level) {
        return new BuildGap.Probe() {
            private @Nullable Set<BlockCoord> deferredCells;

            @Override
            public boolean deferred(BlockCoord at) {
                if (deferredCells == null) {
                    deferredCells = ChestTickets.cells(level);
                }
                return deferredCells.contains(at);
            }

            @Override
            public String block(BlockCoord at) {
                String standing = standing(at);
                int state = standing.indexOf('[');
                return state < 0 ? standing : standing.substring(0, state);
            }

            @Override
            public boolean passable(BlockCoord at) {
                BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
                return state(at).getCollisionShape(level, pos).isEmpty();
            }

            @Override
            public boolean open(BlockCoord at) {
                BlockState standing = state(at);
                return (standing.isAir() || standing.canBeReplaced())
                        && standing.getFluidState().isEmpty();
            }

            @Override
            public boolean loaded(BlockCoord at) {
                return registry != null && registry.blockAt(at) != null;
            }

            private @Nullable String raw(BlockCoord at) {
                return registry == null ? null : registry.blockAt(at);
            }

            private String standing(BlockCoord at) {
                String standing = raw(at);
                if (standing == null) {
                    throw new IllegalStateException("Клетка " + at
                            + " не заполнена в кэше аудита: BuildGap не должен читать мир напрямую и скрывать unknown");
                }
                return standing;
            }

            private BlockState state(BlockCoord at) {
                return LeaseStates.state(standing(at));
            }
        };
    }
}
