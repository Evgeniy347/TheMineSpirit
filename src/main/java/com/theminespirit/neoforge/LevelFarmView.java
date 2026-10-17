package com.theminespirit.neoforge;

import com.theminespirit.core.farming.Above;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.CropState;
import com.theminespirit.core.farming.FarmTile;
import com.theminespirit.core.farming.FarmView;
import com.theminespirit.core.farming.Ground;
import com.theminespirit.core.farming.HarvestMethod;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class LevelFarmView implements FarmView {
    private static final String AGE_PROPERTY = "age";

    private final Level level;
    private int reads;

    public LevelFarmView(Level level) {
        this.level = Objects.requireNonNull(level, "Уровень не может быть null: по нему читаются клетки мира");
    }

    @Override
    public FarmTile tileAt(BlockCoord ground) {
        Objects.requireNonNull(ground, "Координата не может быть null: неизвестно, какую клетку читать");
        BlockPos groundPos = new BlockPos(ground.x(), ground.y(), ground.z());
        BlockState groundState = read(groundPos);
        BlockPos abovePos = groundPos.above();
        BlockState aboveState = read(abovePos);
        Optional<CropState> crop = cropAt(aboveState);
        if (crop.isEmpty()) {
            crop = attachedTo(groundPos, groundState).map(this::stateOf);
        }
        if (crop.isPresent()) {
            return new FarmTile(groundOf(groundState), Above.CROP, crop);
        }
        return new FarmTile(groundOf(groundState), aboveOf(aboveState, abovePos), Optional.empty());
    }

    @Override
    public Optional<BlockCoord> fruitAt(BlockCoord ground, CropKind kind) {
        if (!kind.hasFruit()) {
            return Optional.empty();
        }
        Block fruit = CropRegistry.fruitBlock(kind);
        BlockPos stem = new BlockPos(ground.x(), ground.y() + 1, ground.z());
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = stem.relative(side);
            if (read(neighbor).is(fruit)) {
                return Optional.of(coordOf(neighbor));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean hasFruitSpace(BlockCoord ground) {
        BlockPos base = new BlockPos(ground.x(), ground.y(), ground.z());
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = base.relative(side);
            if (isFruitGround(read(neighbor)) && read(neighbor.above()).isAir()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<BlockCoord> attachedCrop(BlockCoord support, CropKind kind) {
        BlockPos supportPos = new BlockPos(support.x(), support.y(), support.z());
        return attachedTo(supportPos, read(supportPos))
                .filter(attached -> attached.kind() == kind)
                .map(attached -> coordOf(attached.pos()));
    }

    @Override
    public Optional<BlockCoord> attachedSpot(BlockCoord support, CropKind kind) {
        if (!kind.hasSupport()) {
            return Optional.empty();
        }
        BlockPos base = new BlockPos(support.x(), support.y(), support.z());
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = base.relative(side);
            if (read(neighbor).isAir()) {
                return Optional.of(coordOf(neighbor));
            }
        }
        return Optional.empty();
    }

    @Override
    public int columnHeight(BlockCoord ground) {
        Objects.requireNonNull(ground, "Координата не может быть null: неизвестно, где считать высоту столба");
        BlockPos base = new BlockPos(ground.x(), ground.y() + 1, ground.z());
        BlockState baseState = read(base);
        Optional<CropKind> kind = CropKind.ofBlock(BuiltInRegistries.BLOCK.getKey(baseState.getBlock()).toString());
        if (kind.isEmpty() || kind.get().harvestMethod() != HarvestMethod.BREAK_COLUMN) {
            return 1;
        }
        int height = 0;
        BlockPos cursor = base;
        while (height < 8) {
            BlockState state = height == 0 ? baseState : read(cursor);
            Optional<CropKind> curKind = CropKind.ofBlock(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            if (curKind.isEmpty() || curKind.get() != kind.get()) {
                break;
            }
            height++;
            cursor = cursor.above();
        }
        return height;
    }

    public int reads() {
        return reads;
    }

    private BlockState read(BlockPos pos) {
        reads++;
        return level.getBlockState(pos);
    }

    private Optional<Attached> attachedTo(BlockPos supportPos, BlockState supportState) {
        if (CropKind.ofSupportBlock(blockId(supportState)).isEmpty()) {
            return Optional.empty();
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = supportPos.relative(side);
            BlockState state = read(neighbor);
            Optional<CropKind> kind = CropKind.ofBlock(blockId(state));
            if (kind.isEmpty() || !kind.get().hasSupport()) {
                continue;
            }
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (supportPos.equals(neighbor.relative(facing))) {
                return Optional.of(new Attached(neighbor, kind.get(), state));
            }
        }
        return Optional.empty();
    }

    private CropState stateOf(Attached attached) {
        return new CropState(attached.kind(), ageOf(attached.state(), attached.kind().blockId()));
    }

    private static boolean isFruitGround(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.FARMLAND);
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static BlockCoord coordOf(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static Ground groundOf(BlockState state) {
        if (state.is(Blocks.FARMLAND)) {
            return Ground.FARMLAND;
        }
        if (state.is(Blocks.DIRT)) {
            return Ground.DIRT;
        }
        if (state.is(Blocks.GRASS_BLOCK)) {
            return Ground.GRASS;
        }
        if (state.is(Blocks.DIRT_PATH)) {
            return Ground.PATH;
        }
        if (CropKind.ofSupportBlock(blockId(state)).isPresent()) {
            return Ground.LOG;
        }
        return Ground.OTHER;
    }

    private Above aboveOf(BlockState state, BlockPos pos) {
        if (state.isAir()) {
            return Above.AIR;
        }
        boolean replaceablePlant = state.canBeReplaced() && state.getFluidState().isEmpty()
                && state.getDestroySpeed(level, pos) == 0.0F;
        return replaceablePlant ? Above.WEED : Above.BLOCKED;
    }

    private static Optional<CropState> cropAt(BlockState state) {
        String id = blockId(state);
        return CropKind.ofBlock(id).map(kind -> new CropState(kind, ageOf(state, id)));
    }

    private static int ageOf(BlockState state, String id) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(AGE_PROPERTY);
        if (property instanceof IntegerProperty age) {
            return state.getValue(age);
        }
        throw new IllegalStateException("У посева " + id + " нет целого свойства " + AGE_PROPERTY
                + ": зрелость не определить, проверь id в CropKind и версию игры");
    }

    private record Attached(BlockPos pos, CropKind kind, BlockState state) {
    }
}
