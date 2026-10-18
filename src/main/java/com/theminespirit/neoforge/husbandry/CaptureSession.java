package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.husbandry.CaptureFollow;
import com.theminespirit.core.husbandry.EscapeCheck;
import com.theminespirit.core.husbandry.RingBuildOrder;
import com.theminespirit.core.husbandry.RingGround;
import com.theminespirit.core.husbandry.RingGround.Cell;
import com.theminespirit.core.husbandry.RingPlan;
import com.theminespirit.core.husbandry.RingStands;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.BlockItems;
import com.theminespirit.neoforge.BlockPlacer;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CaptureSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    enum State {
        FIND,
        BUILD,
        VERIFY
    }

    static final String DIRT = "minecraft:dirt";
    static final String COBBLE = "minecraft:cobblestone";
    private static final int QUERY_EVERY_TICKS = 10;
    private static final int SCAN_UP = 3;
    private static final int SCAN_DOWN = 6;
    private static final int DEFER_LIMIT = 200;

    private final SpiritEntity spirit;
    private final Level level;
    private final Species species;
    private final int searchRadius;
    private final RingPlan.Size size;
    private final LevelSafetyView safety;
    private final BlockPlacer placer;
    private final Approach approach;

    private State state = State.FIND;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private Animal target;
    private String targetId;
    private Cell center;
    private RingGround ground;
    private RingPlan.Ring ring;
    private final List<BlockCoord> remaining = new ArrayList<>();
    private final Map<BlockCoord, Integer> attempts = new HashMap<>();
    private String blockId;
    private BlockState wanted;
    private int planSize;
    private int placed;
    private int rechecks;
    private int ticks;
    private int queryCooldown;
    private int defers;
    private BlockCoord current;
    private BlockCoord approachFor;
    private Cell previousAnimal;
    private Cell lastAnimal;
    private boolean needsPlan = true;

    public CaptureSession(SpiritEntity spirit, Species species, int radius) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него некому ловить");
        this.species = Objects.requireNonNull(species, "Вид не может быть null: без него неизвестно, кого ловить");
        if (radius < CatalogLimits.CAPTURE_RADIUS_MIN || radius > CatalogLimits.CAPTURE_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус отлова " + radius + " вне диапазона ["
                    + CatalogLimits.CAPTURE_RADIUS_MIN + ", " + CatalogLimits.CAPTURE_RADIUS_MAX
                    + "]: дальше животное не догнать до постройки кольца");
        }
        this.searchRadius = radius;
        this.size = RingPlan.sizeFor(species);
        this.level = spirit.level();
        this.safety = new LevelSafetyView(level);
        this.placer = new BlockPlacer(spirit);
        this.approach = new Approach(spirit);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время отлова");
        }
        ticks++;
        switch (state) {
            case FIND -> findTick();
            case BUILD -> buildTick();
            case VERIFY -> verifyTick();
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

    public ScriptInstance.Progress progress() {
        return new ScriptInstance.Progress(Math.min(placed, Math.max(planSize, 1)), Math.max(planSize, 1));
    }

    public Map<String, Object> resultData() {
        Map<String, Object> data = new HashMap<>();
        data.put("caught", species.name().toLowerCase(Locale.ROOT));
        data.put("placed", placed);
        data.put("rechecks", rechecks);
        if (center != null) {
            data.put("center", List.of(center.x(), ground.heightAt(center), center.z()));
        }
        return Map.copyOf(data);
    }

    public Map<String, Object> saveState() {
        Map<String, Object> saved = new HashMap<>();
        saved.put("v", 1);
        saved.put("state", state.name());
        saved.put("placed", placed);
        saved.put("rechecks", rechecks);
        if (targetId != null) {
            saved.put("target", targetId);
        }
        if (center != null) {
            saved.put("center", List.of(center.x(), center.z()));
        }
        if (blockId != null) {
            saved.put("block", blockId);
        }
        return Map.copyOf(saved);
    }

    @SuppressWarnings("unchecked")
    public void restoreState(Map<String, Object> saved) {
        Objects.requireNonNull(saved, "Состояние не может быть null: пустое задают пустой картой");
        placed = Math.max(placed, number(saved, "placed"));
        rechecks = Math.max(rechecks, number(saved, "rechecks"));
        if (saved.get("target") instanceof String id && !id.isBlank()) {
            targetId = id;
        }
        if (saved.get("center") instanceof List<?> point && point.size() == 2 && point.get(0) instanceof Number x
                && point.get(1) instanceof Number z) {
            center = new Cell(x.intValue(), z.intValue());
        }
        if (saved.get("block") instanceof String block && !block.isBlank()) {
            blockId = block;
            wanted = BlockStates.stateFor(block);
        }
        if (saved.get("state") instanceof String name) {
            try {
                state = State.valueOf(name);
                if (state != State.FIND && center == null) {
                    state = State.FIND;
                }
            } catch (IllegalArgumentException unknown) {
                state = State.FIND;
            }
        }
        needsPlan = state != State.FIND;
        approach.cancel();
    }

    private void findTick() {
        if (queryCooldown > 0) {
            queryCooldown--;
            return;
        }
        queryCooldown = QUERY_EVERY_TICKS;
        List<Animal> animals = HerdSurvey.query(level, spirit.position(), searchRadius);
        Animal nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Animal animal : animals) {
            if (!animal.isAlive() || animal.isRemoved()) {
                continue;
            }
            if (HerdSpecies.of(animal).orElse(null) != species) {
                continue;
            }
            if (targetId != null && !animal.getStringUUID().equals(targetId)) {
                continue;
            }
            double distance = spirit.position().distanceTo(animal.position());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = animal;
            }
        }
        if (nearest == null) {
            if (targetId != null) {
                fail("животное ушло: цель отлова пропала из радиуса " + searchRadius);
            } else {
                fail("животное ушло: в радиусе " + searchRadius + " нет "
                        + species.name().toLowerCase(Locale.ROOT));
            }
            return;
        }
        target = nearest;
        targetId = nearest.getStringUUID();
        BlockPos pos = nearest.blockPosition();
        center = new Cell(pos.getX(), pos.getZ());
        needsPlan = true;
        state = State.BUILD;
    }

    private void buildTick() {
        if (!refreshTarget()) {
            return;
        }
        if (needsPlan || !planFollowsAnimal()) {
            if (!plan()) {
                return;
            }
        }
        while (!remaining.isEmpty() && alreadyBuilt(remaining.get(0))) {
            remaining.remove(0);
        }
        if (remaining.isEmpty()) {
            state = State.VERIFY;
            return;
        }
        BlockPos animalPos = target.blockPosition();
        Cell animal = animalCell(animalPos);
        if (!animal.equals(lastAnimal)) {
            previousAnimal = lastAnimal;
            lastAnimal = animal;
        }
        Cell leading = CaptureFollow.leadingCell(animal, previousAnimal);
        current = RingBuildOrder.nextTarget(remaining, current, leading)
                .orElse(null);
        if (current == null) {
            state = State.VERIFY;
            return;
        }
        if (occupiedByAnimal(current)) {
            defers++;
            if (defers > DEFER_LIMIT) {
                fail("кольцо не замыкается: животное стоит в стенке");
                return;
            }
            remaining.remove(current);
            remaining.add(current);
            current = null;
            return;
        }
        placeCurrent(false);
    }

    private void verifyTick() {
        if (!refreshTarget()) {
            return;
        }
        if (needsPlan || !planFollowsAnimal()) {
            if (!plan()) {
                return;
            }
        }
        Map<Cell, Integer> tops = measureTops();
        Optional<EscapeCheck.WeakSpot> escape = EscapeCheck.findEscape(ground, tops, ring.interior());
        if (escape.isEmpty()) {
            outcome = Outcome.SUCCEEDED;
            approach.cancel();
            return;
        }
        if (rechecks >= CatalogLimits.CAPTURE_RECHECK_MAX) {
            Cell weak = escape.orElseThrow().cell();
            fail("кольцо не держит: слабое место (" + weak.x() + ", " + weak.z() + ")");
            return;
        }
        Cell weak = escape.orElseThrow().cell();
        current = new BlockCoord(weak.x(), tops.getOrDefault(weak, ground.heightAt(weak)), weak.z());
        if (occupiedByAnimal(current)) {
            defers++;
            if (defers > DEFER_LIMIT) {
                fail("кольцо не замыкается: животное стоит в слабом месте");
            }
            return;
        }
        placeCurrent(true);
    }

    private void placeCurrent(boolean raise) {
        BlockPos pos = new BlockPos(current.x(), current.y(), current.z());
        BlockCoord feet = spiritCoord();
        if (!SpiritReach.reachable(feet, current) || SpiritReach.forbiddenStand(feet, current)) {
            BlockCoord walkingTo = current;
            approachCurrent(raise, feet);
            if (!raise) {
                placeOnTheWay(feet, walkingTo);
            }
            return;
        }
        BlockPlacer.Begin begin = placer.begin(pos, wanted, blockId);
        if (begin instanceof BlockPlacer.Started) {
            placed++;
            dropCurrent();
            if (raise) {
                rechecks++;
                state = State.VERIFY;
            }
            return;
        }
        if (begin instanceof BlockPlacer.Protected single) {
            fail("строить запрещено: " + single.reason() + "; поставлено " + placed + " из " + planSize);
            return;
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        switch (refused.reason()) {
            case NO_MATERIAL -> fail("не хватило блоков '" + blockId + "': нужно " + planSize + ", поставлено "
                    + placed);
            case OCCUPIED, NO_SUPPORT, CHUNK_NOT_LOADED, OUT_OF_REACH -> noteAttempt(raise);
        }
    }

    private void placeOnTheWay(BlockCoord feet, BlockCoord walkingTo) {
        for (BlockCoord block : remaining) {
            if (block.equals(walkingTo) || !SpiritReach.reachable(feet, block) || SpiritReach.forbiddenStand(feet, block)
                    || !restsOnBuilt(block) || occupiedByAnimal(block)) {
                continue;
            }
            BlockPos pos = new BlockPos(block.x(), block.y(), block.z());
            if (placer.begin(pos, wanted, blockId) instanceof BlockPlacer.Started) {
                placed++;
                remaining.remove(block);
            }
            return;
        }
    }

    private boolean restsOnBuilt(BlockCoord block) {
        BlockCoord below = new BlockCoord(block.x(), block.y() - 1, block.z());
        return !ring.blocks().contains(below) || alreadyBuilt(below);
    }

    private void approachCurrent(boolean raise, BlockCoord feet) {
        if (!approach.isActive() || !current.equals(approachFor)) {
            List<BlockCoord> stands = RingStands.of(current, ring, remaining, safety, feet);
            if (stands.isEmpty()) {
                noteAttempt(raise);
                return;
            }
            BlockCoord stand = stands.get(0);
            approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), BuildTuning.STAND_ARRIVAL);
            approachFor = current;
        }
        if (approach.tick() != Approach.State.MOVING) {
            noteAttempt(raise);
        }
    }

    private void noteAttempt(boolean raise) {
        if (raise) {
            rechecks++;
            current = null;
            state = State.VERIFY;
            return;
        }
        int tries = attempts.getOrDefault(current, 0) + 1;
        remaining.remove(current);
        if (tries < 3) {
            remaining.add(current);
        }
        attempts.put(current, tries);
        current = null;
    }

    private void dropCurrent() {
        remaining.remove(current);
        current = null;
    }

    private boolean plan() {
        BlockPos animalPos = target.blockPosition();
        if (center == null) {
            center = new Cell(animalPos.getX(), animalPos.getZ());
        }
        int farX = size == RingPlan.Size.SMALL ? center.x() + 2 : center.x() + 3;
        int minX = center.x() - 2;
        int minZ = center.z() - 2;
        int sizeZ = size == RingPlan.Size.SMALL ? 5 : 6;
        int sizeX = farX - minX + 1;
        int[][] heights = new int[sizeZ][sizeX];
        for (int dz = 0; dz < sizeZ; dz++) {
            for (int dx = 0; dx < sizeX; dx++) {
                int column = measureColumn(minX + dx, minZ + dz, animalPos.getY());
                if (column == Integer.MIN_VALUE) {
                    fail("землю под (" + (minX + dx) + ", " + (minZ + dz) + ") не измерить: нет опоры рядом с животным");
                    return false;
                }
                heights[dz][dx] = column;
            }
        }
        ground = RingGround.of(minX, minZ, heights);
        ring = RingPlan.plan(ground, center, size);
        if (blockId == null) {
            int dirt = BlockItems.countIn(spirit.getInventory(), DIRT);
            int cobble = BlockItems.countIn(spirit.getInventory(), COBBLE);
            if (dirt + cobble == 0) {
                fail("не хватило блоков: для кольца нужны земля или булыжник, в инвентаре пусто");
                return false;
            }
            blockId = cobble >= dirt ? COBBLE : DIRT;
            wanted = BlockStates.stateFor(blockId);
        }
        planSize = ring.blocks().size();
        remaining.clear();
        for (BlockCoord block : RingBuildOrder.order(ring, center)) {
            if (!alreadyBuilt(block)) {
                remaining.add(block);
            }
        }
        int have = BlockItems.countIn(spirit.getInventory(), blockId);
        if (!CaptureFollow.enoughMaterial(have, planSize, planSize - remaining.size())) {
            fail("не хватило блоков '" + blockId + "': нужно " + planSize + ", уже поставлено "
                    + (planSize - remaining.size()) + ", есть " + have);
            return false;
        }
        needsPlan = false;
        state = State.BUILD;
        return true;
    }

    private int measureColumn(int x, int z, int refY) {
        for (int y = refY + SCAN_UP; y >= refY - SCAN_DOWN; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) {
                return y + 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    private Map<Cell, Integer> measureTops() {
        Map<Cell, Integer> tops = new HashMap<>();
        for (Cell cell : ring.wall()) {
            int top = ground.heightAt(cell);
            while (level.getBlockState(new BlockPos(cell.x(), top, cell.z())).is(wanted.getBlock())) {
                top++;
            }
            if (top > ground.heightAt(cell)) {
                tops.put(cell, top);
            }
        }
        return tops;
    }

    private boolean alreadyBuilt(BlockCoord block) {
        return level.getBlockState(new BlockPos(block.x(), block.y(), block.z())).is(wanted.getBlock());
    }

    private boolean occupiedByAnimal(BlockCoord block) {
        AABB cell = new AABB(block.x(), block.y(), block.z(), block.x() + 1, block.y() + 1, block.z() + 1);
        return !level.getEntitiesOfClass(Animal.class, cell, animal -> animal.isAlive() && !animal.isRemoved())
                .isEmpty();
    }

    private boolean refreshTarget() {
        if (target != null && target.isAlive() && !target.isRemoved()) {
            if (spirit.position().distanceTo(target.position()) > searchRadius + 2) {
                fail("животное ушло: цель отлова дальше радиуса " + searchRadius);
                return false;
            }
            return true;
        }
        target = null;
        if (targetId == null) {
            state = State.FIND;
            return false;
        }
        for (Animal animal : HerdSurvey.query(level, spirit.position(), searchRadius + 2)) {
            if (animal.isAlive() && !animal.isRemoved() && animal.getStringUUID().equals(targetId)) {
                target = animal;
                return true;
            }
        }
        fail("животное ушло: цель отлова пропала");
        return false;
    }

    private boolean planFollowsAnimal() {
        if (ring == null || target == null) {
            return false;
        }
        Cell animal = animalCell(target.blockPosition());
        if (CaptureFollow.ringCovers(ring, animal)) {
            return true;
        }
        center = animal;
        current = null;
        approach.cancel();
        return false;
    }

    private static Cell animalCell(BlockPos pos) {
        return new Cell(pos.getX(), pos.getZ());
    }

    private BlockCoord spiritCoord() {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    private Outcome fail(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        approach.cancel();
        return outcome;
    }

    private static int number(Map<String, Object> saved, String key) {
        return saved.get(key) instanceof Number value ? Math.max(0, value.intValue()) : 0;
    }
}
