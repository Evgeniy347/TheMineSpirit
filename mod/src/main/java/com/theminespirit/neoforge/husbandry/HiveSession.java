package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.board.HiveRules;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class HiveSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    enum State {
        FIND,
        APPROACH,
        SMOKE,
        FIRE_APPROACH,
        FIRE,
        HARVEST
    }

    private static final String VERSION_KEY = "v";
    private static final int VERSION = 1;
    private static final String DONE_KEY = "done";

    final SpiritEntity spirit;
    final Level level;
    final int searchRadius;
    final Approach approach;
    State state = State.FIND;
    BlockPos hive;
    BlockPos fireSpot;
    int done;
    int fireWaits;
    private final int goal;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private BlockPos stand;
    private final HiveSmoke smoke;
    private final HiveHarvest harvest;

    private HiveSession(SpiritEntity spirit, int searchRadius, int goal) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него некому собирать улей");
        this.level = spirit.level();
        this.searchRadius = searchRadius;
        this.goal = goal;
        this.approach = new Approach(spirit);
        this.smoke = new HiveSmoke(this);
        this.harvest = new HiveHarvest(this);
    }

    public static HiveSession harvest(SpiritEntity spirit, int radius, int count) {
        return new HiveSession(spirit, radius, count);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время сбора улья: соты и мёд не тронуты");
        }
        switch (state) {
            case FIND -> find();
            case APPROACH -> approachTick();
            case SMOKE -> smoke.smoke();
            case FIRE_APPROACH -> smoke.fireApproachTick();
            case FIRE -> smoke.fire();
            case HARVEST -> harvest.harvest();
        }
        return outcome;
    }

    public void suspend() {
        approach.suspend();
    }

    public void cancel() {
        approach.cancel();
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public int done() {
        return done;
    }

    public Map<String, Object> saveState() {
        Map<String, Object> saved = new HashMap<>();
        saved.put(VERSION_KEY, VERSION);
        saved.put(DONE_KEY, done);
        return saved;
    }

    public void restoreState(Map<String, Object> map) {
        if (map.get(DONE_KEY) instanceof Number number) {
            done = Math.max(done, number.intValue());
        }
    }

    private void find() {
        if (done >= goal) {
            succeed();
            return;
        }
        BlockPos found = harvest.nearestFullHive();
        if (found == null) {
            if (done == 0) {
                fail("полных ульев нет в радиусе " + searchRadius + ": уровень мёда ниже " + HiveRules.FULL_HONEY_LEVEL
                        + ", улей не тронут");
            } else {
                succeed();
            }
            return;
        }
        List<BlockCoord> stands = StandSpots.of(coord(found), new LevelSafetyView(level), spiritCoord());
        if (stands.isEmpty()) {
            fail("к улью " + found.toShortString() + " не подойти: ни одной стоянки; улей не тронут");
            return;
        }
        BlockCoord chosen = stands.get(0);
        hive = found;
        stand = new BlockPos(chosen.x(), chosen.y(), chosen.z());
        approach.begin(new Vec3(chosen.x() + 0.5, chosen.y(), chosen.z() + 0.5), BuildTuning.STAND_ARRIVAL);
        state = State.APPROACH;
    }

    private void approachTick() {
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> state = State.SMOKE;
            case STUCK, TIMEOUT ->
                fail("к улью " + hive.toShortString()
                        + " не подойти со стоянки " + stand.toShortString()
                        + (approach.refusal() == null ? "" : " (" + approach.refusal() + ")")
                        + ": соты не собраны, пчёлы не тронуты");
        }
    }

    void resetTarget() {
        hive = null;
        fireSpot = null;
        stand = null;
        approach.cancel();
        state = State.FIND;
    }

    private BlockCoord spiritCoord() {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    double distanceTo(BlockPos pos) {
        return spirit.getEyePosition().distanceTo(Vec3.atCenterOf(pos));
    }

    private Outcome succeed() {
        outcome = Outcome.SUCCEEDED;
        approach.cancel();
        return outcome;
    }

    Outcome fail(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        approach.cancel();
        return outcome;
    }
}
