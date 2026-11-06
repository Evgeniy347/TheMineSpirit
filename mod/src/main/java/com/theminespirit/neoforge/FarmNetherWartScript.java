package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.WartFarmParams;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherWartBlock;

final class FarmNetherWartScript implements ScriptInstance {
    private static final int GROW_TIMEOUT = 24000;
    private static final int VERTICAL = 2;

    private enum Phase {
        PLANT,
        GROW,
        HARVEST
    }

    private final SpiritEntity spirit;
    private final WartFarmParams params;
    private Phase phase = Phase.PLANT;
    private BlockPos ground;
    private int planted;
    private int ticks;
    private String failureReason;

    FarmNetherWartScript(SpiritEntity spirit, WartFarmParams params) {
        this.spirit = spirit;
        this.params = params;
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез на грядке адского нароста");
        }
        if (planted >= params.count()) {
            return ScriptStatus.SUCCEEDED;
        }
        return switch (phase) {
            case PLANT -> plant();
            case GROW -> grow();
            case HARVEST -> harvest();
        };
    }

    private ScriptStatus plant() {
        if (!ensureGround()) {
            return ScriptStatus.FAILED;
        }
        BlockPos crop = ground.above();
        if (!spirit.getInventory().removeItemType(Items.NETHER_WART, 1).isEmpty()) {
            spirit.level().setBlockAndUpdate(crop, Blocks.NETHER_WART.defaultBlockState());
            ticks = 0;
            phase = Phase.GROW;
            return ScriptStatus.RUNNING;
        }
        return fail("в инвентаре нет адского нароста для посадки");
    }

    private boolean ensureGround() {
        if (ground != null && spirit.level().getBlockState(ground).is(Blocks.SOUL_SAND)) {
            return true;
        }
        BlockPos found = findSpot();
        if (found == null) {
            fail("нет свободной клетки на твёрдом полу под песок душ");
            return false;
        }
        if (spirit.getInventory().removeItemType(Items.SOUL_SAND, 1).isEmpty()) {
            fail("в инвентаре нет песка душ для грядки");
            return false;
        }
        spirit.level().setBlockAndUpdate(found, Blocks.SOUL_SAND.defaultBlockState());
        ground = found;
        return true;
    }

    private ScriptStatus grow() {
        if (ground == null) {
            return fail("грядка адского нароста потеряна: песок душ исчез");
        }
        BlockPos crop = ground.above();
        if (!(spirit.level().getBlockState(crop).getBlock() instanceof NetherWartBlock)) {
            return fail("посадка адского нароста исчезла в " + crop.toShortString());
        }
        int age = spirit.level().getBlockState(crop).getValue(NetherWartBlock.AGE);
        if (age >= NetherWartBlock.MAX_AGE) {
            phase = Phase.HARVEST;
            return ScriptStatus.RUNNING;
        }
        if (++ticks > GROW_TIMEOUT) {
            return fail("адский нарост не созрел за " + GROW_TIMEOUT + " тиков");
        }
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus harvest() {
        BlockPos crop = ground.above();
        if (!(spirit.level() instanceof ServerLevel server)) {
            return fail("грядка адского нароста вне серверного мира");
        }
        Block block = spirit.level().getBlockState(crop).getBlock();
        List<ItemStack> drops = Block.getDrops(spirit.level().getBlockState(crop), server, crop, null);
        spirit.level().removeBlock(crop, false);
        for (ItemStack drop : drops) {
            ItemStack rest = spirit.getInventory().addItem(drop.copy());
            if (!rest.isEmpty()) {
                return fail("нет места в инвентаре для урожая: " + name(block));
            }
        }
        planted++;
        phase = Phase.PLANT;
        ticks = 0;
        return ScriptStatus.RUNNING;
    }

    private BlockPos findSpot() {
        BlockPos center = spirit.blockPosition();
        int radius = params.radius();
        for (int distance = 1; distance <= radius; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                for (int dz = -distance; dz <= distance; dz++) {
                    for (int dy = -VERTICAL; dy <= VERTICAL; dy++) {
                        BlockPos at = center.offset(dx, dy, dz);
                        if (fits(at)) {
                            return at;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean fits(BlockPos at) {
        if (!spirit.level().isLoaded(at)) {
            return false;
        }
        BlockPos below = at.below();
        return spirit.level().getBlockState(at).canBeReplaced()
                && spirit.level().getBlockState(at.above()).canBeReplaced()
                && spirit.level().getBlockState(below).isFaceSturdy(spirit.level(), below, Direction.UP);
    }

    private static String name(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        return ScriptStatus.FAILED;
    }

    @Override
    public void cancel() {}

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of("planted", planted);
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (state.get("planted") instanceof Number number) {
            planted = Math.max(planted, number.intValue());
        }
    }
}
