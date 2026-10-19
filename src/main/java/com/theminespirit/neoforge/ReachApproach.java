package com.theminespirit.neoforge;

import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Подход «на досягаемость» к блоку (упрощение 44.2): сундук, верстак, печь, наковальня и другие блоки работы
 * считаются достигнутыми, как только блок в досягаемости духа ({@link SpiritReach}: 3 по горизонтали, 15 вверх),
 * без прямой видимости и точного подхода. Если не дотянуться — дух идёт к ближайшей стоянке, откуда дотянется.
 */
public final class ReachApproach {
    private static final int MAX_STANDS = 4;
    private static final double STAND_ARRIVAL = 0.8;

    private final SpiritEntity spirit;
    private final Approach approach;
    private final LevelSafetyView safety;
    private final Set<BlockCoord> tried = new HashSet<>();
    private BlockCoord target;
    private BlockCoord stand;
    private boolean active;
    private String refusal;

    public ReachApproach(SpiritEntity spirit) {
        this.spirit = spirit;
        this.approach = new Approach(spirit);
        this.safety = new LevelSafetyView(spirit.level());
    }

    public void begin(BlockPos block) {
        approach.cancel();
        target = new BlockCoord(block.getX(), block.getY(), block.getZ());
        tried.clear();
        stand = null;
        refusal = null;
        active = true;
    }

    public Approach.State tick() {
        if (!active) {
            throw new IllegalStateException("Подход к блоку не начат: сначала begin(...)");
        }
        if (inReach()) {
            finish();
            return Approach.State.ARRIVED;
        }
        if (!approach.isActive()) {
            if (stand != null) {
                tried.add(stand);
            }
            if (tried.size() >= MAX_STANDS || !nextStand()) {
                refusal = "ни с одной стоянки не дотянуться до " + target + " ("
                        + SpiritReach.current().describe() + ")" + (refusal == null ? "" : "; " + refusal);
                finish();
                return Approach.State.STUCK;
            }
        }
        Approach.State state = approach.tick();
        if (state == Approach.State.MOVING) {
            return state;
        }
        if (approach.refusal() != null) {
            refusal = approach.refusal();
        }
        approach.cancel();
        if (inReach()) {
            finish();
            return Approach.State.ARRIVED;
        }
        return Approach.State.MOVING;
    }

    public boolean inReach() {
        return target != null && SpiritReach.reachable(feet(), target);
    }

    public boolean isActive() {
        return active;
    }

    public String refusal() {
        return refusal;
    }

    public void cancel() {
        approach.cancel();
        active = false;
    }

    public void suspend() {
        approach.suspend();
    }

    private boolean nextStand() {
        ReachPlanner.World world = new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                return true;
            }

            @Override
            public boolean standable(BlockCoord feet) {
                return MiningSafety.canStand(feet, safety);
            }
        };
        Optional<BlockCoord> next =
                ReachPlanner.nextStand(SpiritReach.current(), feet(), List.of(target), world, tried, Set.of(target));
        if (next.isEmpty()) {
            return false;
        }
        stand = next.get();
        approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), STAND_ARRIVAL);
        return true;
    }

    private void finish() {
        approach.cancel();
        active = false;
    }

    private BlockCoord feet() {
        BlockPos pos = spirit.blockPosition();
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
