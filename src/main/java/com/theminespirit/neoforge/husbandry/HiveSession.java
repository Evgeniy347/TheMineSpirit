package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.board.HiveRules;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.BlockBreaker;
import com.theminespirit.neoforge.BlockPlacer;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.CampfireKit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class HiveSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum State {
        FIND,
        APPROACH,
        SMOKE,
        FIRE_APPROACH,
        FIRE,
        HARVEST
    }

    private static final String VERSION_KEY = "v";
    private static final int VERSION = 1;
    private static final int FIRE_WAIT_TICKS = 100;
    private static final String DONE_KEY = "done";
    private static final String CAMPFIRE_ID = "minecraft:campfire";
    private static final double FIRE_ARRIVAL = 1.2;

    private final SpiritEntity spirit;
    private final Level level;
    private final int searchRadius;
    private final int goal;
    private final Approach approach;

    private State state = State.FIND;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private BlockPos hive;
    private BlockPos fireSpot;
    private BlockPos stand;
    private int done;
    private int fireWaits;

    private HiveSession(SpiritEntity spirit, int searchRadius, int goal) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него некому собирать улей");
        this.level = spirit.level();
        this.searchRadius = searchRadius;
        this.goal = goal;
        this.approach = new Approach(spirit);
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
            case SMOKE -> smoke();
            case FIRE_APPROACH -> fireApproachTick();
            case FIRE -> fire();
            case HARVEST -> harvest();
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
        BlockPos found = nearestFullHive();
        if (found == null) {
            if (done == 0) {
                fail("полных ульев нет в радиусе " + searchRadius + ": уровень мёда ниже "
                        + HiveRules.FULL_HONEY_LEVEL + ", улей не тронут");
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
            case MOVING -> {
            }
            case ARRIVED -> state = State.SMOKE;
            case STUCK, TIMEOUT -> fail("к улью " + hive.toShortString()
                    + " не подойти со стоянки " + stand.toShortString()
                    + (approach.refusal() == null ? "" : " (" + approach.refusal() + ")")
                    + ": соты не собраны, пчёлы не тронуты");
        }
    }

    private void smoke() {
        if (!HiveRules.campfireNeeded(CampfireBlock.isSmokeyPos(level, hive))) {
            state = State.HARVEST;
            return;
        }
        if (lightUnlitCampfire()) {
            state = State.HARVEST;
            return;
        }
        if (!CampfireKit.ensure(spirit)) {
            fail("костра под ульем нет и материалов для него не хватает: пчёлы разозлятся, "
                    + "улей " + hive.toShortString() + " не тронут");
            return;
        }
        fireSpot = campfireSpot();
        if (fireSpot == null) {
            fail("под ульем " + hive.toShortString() + " нет места для костра в "
                    + HiveRules.smokeDepth() + " блоках: собирать без дыма нельзя, пчёлы разозлятся");
            return;
        }
        if (distanceTo(fireSpot) > BlockBreaker.REACH) {
            approach.begin(Vec3.atCenterOf(fireSpot), FIRE_ARRIVAL);
            state = State.FIRE_APPROACH;
            return;
        }
        state = State.FIRE;
    }

    private void fireApproachTick() {
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> state = State.FIRE;
            case STUCK, TIMEOUT -> fail("к месту костра " + fireSpot.toShortString()
                    + " не подойти: улей не тронут");
        }
    }

    private void fire() {
        BlockPlacer placer = new BlockPlacer(spirit);
        BlockPlacer.Begin begin = placer.begin(fireSpot, BlockStates.stateFor(CAMPFIRE_ID), CAMPFIRE_ID);
        if (begin instanceof BlockPlacer.Started) {
            if (!CampfireBlock.isSmokeyPos(level, hive)) {
                fail("костёр в " + fireSpot.toShortString() + " поставлен, но дым не доходит до улья "
                        + hive.toShortString() + ": улей не тронут");
                return;
            }
            state = State.HARVEST;
            return;
        }
        if (begin instanceof BlockPlacer.Protected protectedSpot) {
            fail("ставить костёр запрещено: " + protectedSpot.reason() + "; улей не тронут");
            return;
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        boolean creatureInCell = refused.reason() == BlockPlacer.RefuseReason.OCCUPIED
                && level.getBlockState(fireSpot).canBeReplaced();
        if (creatureInCell && ++fireWaits <= FIRE_WAIT_TICKS) {
            return;
        }
        fail("костёр в " + fireSpot.toShortString() + " не поставлен (" + refused.reason()
                + (creatureInCell ? ", клетку " + FIRE_WAIT_TICKS + " тиков занимало существо" : "")
                + "): улей не тронут");
    }

    private void harvest() {
        BlockState state = level.getBlockState(hive);
        if (!fullHive(state)) {
            resetTarget();
            return;
        }
        HiveRules.Tool tool = HiveRules.chooseTool(spirit.getInventory().countItem(Items.SHEARS) > 0,
                spirit.getInventory().countItem(Items.GLASS_BOTTLE));
        if (tool == HiveRules.Tool.NONE) {
            fail("нечем собирать улей " + hive.toShortString()
                    + ": нужны ножницы или стеклянная бутылка, мир не изменён");
            return;
        }
        if (tool == HiveRules.Tool.SHEARS) {
            shears(hive);
        } else {
            bottle(hive);
        }
    }

    private void shears(BlockPos pos) {
        ItemStack shears = firstStack(Items.SHEARS);
        if (shears.isEmpty()) {
            fail("ножницы исчезли из инвентаря: улей " + pos.toShortString() + " не тронут");
            return;
        }
        if (!spirit.getInventory().canAddItem(new ItemStack(Items.HONEYCOMB, 3))) {
            fail("инвентарь полон: не помещаются 3 соты, улей " + pos.toShortString() + " не тронут");
            return;
        }
        spirit.getInventory().addItem(new ItemStack(Items.HONEYCOMB, 3));
        shears.hurtAndBreak(1, spirit, EquipmentSlot.MAINHAND);
        resetHoneyLevel(pos);
        done++;
        resetTarget();
    }

    private void bottle(BlockPos pos) {
        if (spirit.getInventory().countItem(Items.GLASS_BOTTLE) < 1) {
            fail("стеклянная бутылка исчезла из инвентаря: улей " + pos.toShortString() + " не тронут");
            return;
        }
        if (!spirit.getInventory().canAddItem(new ItemStack(Items.HONEY_BOTTLE))) {
            fail("инвентарь полон: не помещается бутылка мёда, улей " + pos.toShortString() + " не тронут");
            return;
        }
        spirit.getInventory().removeItemType(Items.GLASS_BOTTLE, 1);
        spirit.getInventory().addItem(new ItemStack(Items.HONEY_BOTTLE));
        resetHoneyLevel(pos);
        done++;
        resetTarget();
    }

    private void resetTarget() {
        hive = null;
        fireSpot = null;
        stand = null;
        approach.cancel();
        state = State.FIND;
    }

    private void resetHoneyLevel(BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (current.hasProperty(BeehiveBlock.HONEY_LEVEL)) {
            level.setBlock(pos, current.setValue(BeehiveBlock.HONEY_LEVEL, 0), 3);
        }
    }

    private boolean lightUnlitCampfire() {
        for (int offset = 1; offset <= HiveRules.smokeDepth(); offset++) {
            if (!HiveRules.smokeOffsetWithinDepth(offset)) {
                continue;
            }
            BlockPos pos = hive.below(offset);
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.CAMPFIRE) && state.hasProperty(CampfireBlock.LIT)
                    && !state.getValue(CampfireBlock.LIT)) {
                level.setBlock(pos, state.setValue(CampfireBlock.LIT, true), 3);
            }
        }
        return CampfireBlock.isSmokeyPos(level, hive);
    }

    private BlockPos campfireSpot() {
        for (int offset = 1; offset <= HiveRules.smokeDepth(); offset++) {
            if (!HiveRules.smokeOffsetWithinDepth(offset)) {
                continue;
            }
            BlockPos pos = hive.below(offset);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.canBeReplaced()) {
                continue;
            }
            if (supports(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private BlockPos nearestFullHive() {
        BlockPos origin = spirit.blockPosition();
        BlockPos found = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-searchRadius, -searchRadius, -searchRadius),
                origin.offset(searchRadius, searchRadius, searchRadius))) {
            if (!fullHive(level.getBlockState(pos))) {
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < best) {
                best = distance;
                found = pos.immutable();
            }
        }
        return found;
    }

    private static boolean fullHive(BlockState state) {
        return state.getBlock() instanceof BeehiveBlock && state.hasProperty(BeehiveBlock.HONEY_LEVEL)
                && HiveRules.fullHive(state.getValue(BeehiveBlock.HONEY_LEVEL));
    }

    private static boolean supports(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return true;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = pos.relative(side);
            if (level.getBlockState(neighbour).isFaceSturdy(level, neighbour, side.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    private BlockCoord spiritCoord() {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private double distanceTo(BlockPos pos) {
        return spirit.getEyePosition().distanceTo(Vec3.atCenterOf(pos));
    }

    private ItemStack firstStack(Item item) {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private Outcome succeed() {
        outcome = Outcome.SUCCEEDED;
        approach.cancel();
        return outcome;
    }

    private Outcome fail(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        approach.cancel();
        return outcome;
    }
}
