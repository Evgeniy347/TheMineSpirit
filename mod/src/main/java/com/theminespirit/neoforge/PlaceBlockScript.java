package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildResult;
import com.theminespirit.core.building.BuildState;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.PlaceBlockParams;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class PlaceBlockScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final Level level;
    private final String blockId;
    private final BlockCoord target;
    private final BlockState wanted;
    private final LevelSafetyView safety;
    private final BlockPlacer placer;
    private final Approach approach;

    private boolean approached;
    private boolean standRequired;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;
    private int placed;
    private final BuildResult tally = new BuildResult();

    PlaceBlockScript(SpiritEntity spirit, PlaceBlockParams params) {
        this.spirit = spirit;
        this.level = spirit.level();
        int state = params.block().indexOf('[');
        this.blockId = state < 0 ? params.block() : params.block().substring(0, state);
        this.target = new BlockCoord(params.x(), params.y(), params.z());
        this.wanted = BlockStates.stateFor(blockId, properties(params.block(), state));
        this.safety = new LevelSafetyView(level);
        this.placer = new BlockPlacer(spirit);
        this.approach = new Approach(spirit);
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время постановки блока");
        }
        if (level.getBlockState(toPos(target)).is(wanted.getBlock())) {
            placed = 1;
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        if (!approached) {
            BlockPos pos = toPos(target);
            double distance = spirit.getEyePosition().distanceTo(Vec3.atCenterOf(pos));
            if (distance > BlockBreaker.REACH || standRequired) {
                List<BlockCoord> stands = StandSpots.of(target, safety, spiritCoord());
                if (stands.isEmpty()) {
                    return fail("к клетке " + pos.toShortString() + " не подойти: ни одной стоянки; поставлено 0 из 1");
                }
                if (!approach.isActive()) {
                    BlockCoord stand = stands.get(0);
                    approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), BuildTuning.STAND_ARRIVAL);
                }
                switch (approach.tick()) {
                    case MOVING -> {
                        return ScriptStatus.RUNNING;
                    }
                    case ARRIVED -> approached = true;
                    case STUCK, TIMEOUT -> {
                        return fail("к клетке " + pos.toShortString()
                                + " не подойти: стоянка недостижима; поставлено 0 из 1");
                    }
                }
            } else {
                approached = true;
            }
            return ScriptStatus.RUNNING;
        }
        BlockPlacer.Begin begin = placer.begin(toPos(target), wanted, blockId);
        if (begin instanceof BlockPlacer.Started) {
            placed = 1;
            tally.placed(blockId);
            tally.supplies(1, 0);
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        if (begin instanceof BlockPlacer.Protected single) {
            return fail("ставить запрещено: " + single.reason() + "; поставлено 0 из 1");
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        return switch (refused.reason()) {
            case NO_MATERIAL -> fail("нет материала '" + blockId + "': нужно 1, есть 0; поставлено 0 из 1");
            case OCCUPIED ->
                fail("клетка " + toPos(target).toShortString() + " занята: место занято; поставлено 0 из 1");
            case NO_SUPPORT -> fail(refused.message() + "; поставлено 0 из 1");
            case OUT_OF_REACH -> {
                if (!standRequired) {
                    standRequired = true;
                    approached = false;
                    yield ScriptStatus.RUNNING;
                }
                yield fail("клетка " + toPos(target).toShortString()
                        + " вне досягаемости: стоянка недостижима; поставлено 0 из 1");
            }
            case CHUNK_NOT_LOADED ->
                fail("чанк с клеткой " + toPos(target).toShortString() + " не загружен; поставлено 0 из 1");
        };
    }

    private static Map<String, String> properties(String block, int start) {
        Map<String, String> properties = new LinkedHashMap<>();
        if (start < 0) {
            return properties;
        }
        if (!block.endsWith("]")) {
            throw new IllegalArgumentException(
                    "Состояние блока '" + block + "' без закрывающей скобки: ожидалось " + "id[свойство=значение,…]");
        }
        for (String pair : block.substring(start + 1, block.length() - 1).split(",", -1)) {
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                throw new IllegalArgumentException("Свойство '" + pair + "' блока '" + block
                        + "' не в виде имя=значение: состояние блока не прочитать");
            }
            properties.put(
                    pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
        }
        return properties;
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        approach.cancel();
        return outcome;
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(new Progress(placed, 1));
    }

    @Override
    public Optional<ScriptResult> result() {
        return outcome == ScriptStatus.SUCCEEDED
                ? Optional.of(new ScriptResult.Data(tally.toData()))
                : Optional.empty();
    }

    @Override
    public void cancel() {
        approach.cancel();
    }

    @Override
    public void suspend() {
        approach.suspend();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return new BuildState(placed, 0, 0, outcome == ScriptStatus.FAILED ? 1 : 0, outcome == ScriptStatus.RUNNING)
                .toMap();
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        BuildState snapshot = BuildState.fromMap(state);
        placed = Math.max(placed, snapshot.placed());
        for (int i = 0; i < snapshot.placed(); i++) {
            tally.placed(blockId);
        }
    }

    private BlockCoord spiritCoord() {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
