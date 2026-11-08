package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class FillPickState {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int NO_STAND_LOGGED = 4;

    private final FillSession host;

    FillPickState(FillSession host) {
        this.host = host;
    }

    void tick() {
        while (host.cursor < host.order.size()) {
            BlockCoord cell = host.order.get(host.cursor);
            if (cell.y() > host.brokenY) {
                endPass();
                return;
            }
            BlockPos pos = FillSession.toPos(cell);
            host.terrain.countRead();
            BlockState current = host.level.getBlockState(pos);
            if (current.is(host.wanted.getBlock())) {
                host.tally.prebuilt();
                host.cursor++;
                continue;
            }
            if (host.reservations.foreign(cell, host.spirit.getUUID())) {
                host.tally.foreignSkipped();
                host.tally.skipped();
                host.brokenY = Math.min(host.brokenY, cell.y());
                host.cursor++;
                continue;
            }
            host.reservations.reserve(cell, host.spirit.getUUID());
            host.current = cell;
            if (cell.equals(host.spiritCoord())) {
                host.pillar = host.standView.pillarHere(cell);
                host.state = FillSession.State.PILLAR;
                return;
            }
            if (host.standView.inReach(cell)) {
                host.placeCurrent();
                return;
            }
            List<BlockCoord> spots = StandSpots.of(cell, host.safetyView(), host.spiritCoord());
            if (spots.isEmpty() && host.standView.floorUnder(cell) && host.standView.allowedStand(cell)) {
                spots = List.of(cell);
            }

            if (spots.isEmpty()) {
                if (host.tally.noStandCount() < NO_STAND_LOGGED) {
                    BlockPos at = FillSession.toPos(cell);
                    LOGGER.info(
                            "[fill] {}: нет стоянки у {}: под ней {}, в ней {}, над ней {}, верх без листвы y={}",
                            host.spirit.getName().getString(),
                            cell,
                            host.level.getBlockState(at.below()).getBlock(),
                            host.level.getBlockState(at).getBlock(),
                            host.level.getBlockState(at.above()).getBlock(),
                            host.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.x(), cell.z()));
                }
                host.tally.noStand();
                host.tally.skipped();
                host.brokenY = Math.min(host.brokenY, cell.y());
                host.release(cell);
                host.cursor++;
                continue;
            }
            spots = spots.stream().filter(host.standView::allowedStand).toList();
            if (spots.isEmpty()) {
                host.tally.noStand();
                host.tally.skipped();
                host.brokenY = Math.min(host.brokenY, cell.y());
                host.release(cell);
                host.cursor++;
                continue;
            }
            BlockCoord stand = spots.get(0);
            host.currentStand = stand;
            host.approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), BuildTuning.STAND_ARRIVAL);
            host.state = FillSession.State.APPROACH;
            return;
        }
        endPass();
    }

    private void endPass() {
        int done = host.passPlaced;
        if (done > 0 && host.replan()) {
            return;
        }
        host.state = FillSession.State.DONE;
        host.report.finish();
    }
}
