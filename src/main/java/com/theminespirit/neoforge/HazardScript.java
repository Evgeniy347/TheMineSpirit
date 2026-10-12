package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.observation.SectionCursor;
import com.theminespirit.core.observation.SectionSlice;
import com.theminespirit.core.perception.HazardDetector;
import com.theminespirit.core.perception.HazardFacts;
import com.theminespirit.core.perception.HazardRef;
import com.theminespirit.core.perception.PerceptionQuery;
import com.theminespirit.core.perception.PerceptionResult;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class HazardScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final PerceptionQuery query;
    private final BlockCoord origin;
    private final LoadedTerrain terrain;
    private final SectionCursor cursor;
    private List<HazardRef> found = new ArrayList<>();
    private int slicesThisTick;
    private int maxSlicesPerTick;
    private String failureReason;

    public HazardScript(SpiritEntity spirit, PerceptionQuery query) {
        this.spirit = spirit;
        this.query = query;
        BlockPos position = spirit.blockPosition();
        this.origin = new BlockCoord(position.getX(), position.getY(), position.getZ());
        this.terrain = new LoadedTerrain(spirit.level());
        this.cursor = LoadedWorld.cursor(LoadedWorld.cube(spirit.level(), origin, query.radius().value()), origin);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            failureReason = "дух исчез во время поиска опасностей";
            return ScriptStatus.FAILED;
        }
        slicesThisTick = 0;
        for (SectionSlice slice : cursor.next(ScanTuning.SLICES_PER_TICK)) {
            slicesThisTick++;
            maxSlicesPerTick = Math.max(maxSlicesPerTick, slicesThisTick);
            scan(slice.bounds());
        }
        found = new ArrayList<>(new HazardFacts(found).closest(HazardFacts.MAX_REPORTED).hazards());
        if (!cursor.isDone()) {
            return ScriptStatus.RUNNING;
        }
        spirit.updatePerceptionResult(PerceptionResult.hazards(query.radius(), found));
        return ScriptStatus.SUCCEEDED;
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    public int maxSlicesPerTick() {
        return maxSlicesPerTick;
    }

    public int blockReads() {
        return terrain.reads();
    }

    private void scan(BlockBox bounds) {
        for (int x = bounds.min().x(); x <= bounds.max().x(); x++) {
            for (int y = bounds.min().y(); y <= bounds.max().y(); y++) {
                for (int z = bounds.min().z(); z <= bounds.max().z(); z++) {
                    BlockCoord pos = new BlockCoord(x, y, z);
                    HazardDetector.classify(terrain, pos)
                            .ifPresent(type -> found.add(HazardDetector.refOf(type, pos, origin)));
                }
            }
        }
    }
}
