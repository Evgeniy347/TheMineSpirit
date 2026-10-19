package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.perception.Finding;
import com.theminespirit.core.perception.LocateQuery;
import com.theminespirit.core.perception.NearestTracker;
import com.theminespirit.core.perception.PerceptionResultCodec;
import com.theminespirit.core.script.LocateParams;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.LevelChunk;

public final class LocateScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final LocateQuery query;
    private final BlockCoord origin;
    private final NearestTracker nearest;
    private final LocateSource source;
    private String failureReason;
    private Finding published;

    public LocateScript(SpiritEntity spirit, LocateQuery query) {
        this.spirit = spirit;
        this.query = query;
        BlockPos position = spirit.blockPosition();
        this.origin = new BlockCoord(position.getX(), position.getY(), position.getZ());
        this.nearest = new NearestTracker(origin);
        this.source = sourceFor(spirit, query, position);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            failureReason = "дух исчез во время поиска";
            return ScriptStatus.FAILED;
        }
        source.advance().forEach(nearest::offer);
        if (!source.done()) {
            return ScriptStatus.RUNNING;
        }
        return publish();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    public int examinedPositions() {
        return source.examinedPositions();
    }

    @Override
    public Optional<ScriptResult> result() {
        return published == null ? Optional.empty()
                : Optional.of(new ScriptResult.Data(PerceptionResultCodec.toMap(published)));
    }

    private ScriptStatus publish() {
        if (nearest.best().isEmpty()) {
            published = Finding.notFound(query.type());
            spirit.updateFinding(published);
            return ScriptStatus.SUCCEEDED;
        }
        BlockCoord pos = nearest.best().get();
        LevelChunk chunk = spirit.level().getChunkSource().getChunkNow(pos.x() >> 4, pos.z() >> 4);
        if (chunk == null) {
            failureReason = "чанк найденной позиции " + pos + " выгружен до чтения блока: повторите поиск";
            return ScriptStatus.FAILED;
        }
        String blockId = BuiltInRegistries.BLOCK
                .getKey(chunk.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z())).getBlock()).toString();
        published = Finding.located(query.type(), blockId, pos, origin);
        spirit.updateFinding(published);
        return ScriptStatus.SUCCEEDED;
    }

    private static LocateSource sourceFor(SpiritEntity spirit, LocateQuery query, BlockPos center) {
        int radius = query.radius().value();
        return switch (query.type()) {
            case BLOCK -> new BlockLocateSource(spirit.level(), center, radius, BlockSets.resolve(query.filter()));
            case TREE -> new TreeLocateSource(spirit.level(), center, radius, BlockSets.resolve(logFilter(query)));
            case CONTAINER -> new ContainerLocateSource(spirit,
                    new BlockCoord(center.getX(), center.getY(), center.getZ()), query.radius());
        };
    }

    private static IdFilter logFilter(LocateQuery query) {
        return IdFilter.of(LocateParams.speciesOf(query).stream().map(TreeSpecies::logId).toList());
    }
}
