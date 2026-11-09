package com.theminespirit.neoforge;

import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public final class BlockPlacer {
    public enum RefuseReason {
        CHUNK_NOT_LOADED,
        OCCUPIED,
        NO_SUPPORT,
        OUT_OF_REACH,
        NO_MATERIAL
    }

    public sealed interface Begin permits Started, Refused, Protected {}

    public record Started(BlockPos pos) implements Begin {}

    public record Refused(RefuseReason reason, String message) implements Begin {}

    public record Protected(PolicyId policy, String reason) implements Begin {}

    private final SpiritEntity spirit;
    private final PlotProtection.Ground ground;

    public BlockPlacer(SpiritEntity spirit, PlotProtection.Ground ground) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, кто ставит");
        this.ground =
                Objects.requireNonNull(ground, "Основание постановки не может быть null: без него не отличить стройку");
    }

    public Begin begin(BlockPos target, BlockState state, String blockId) {
        return PlaceRefusal.checked(spirit, target, blockId, begin(target, state, blockId, true));
    }

    public Begin beginWithoutSupport(BlockPos target, BlockState state, String blockId) {
        return PlaceRefusal.checked(spirit, target, blockId, begin(target, state, blockId, false));
    }

    private Begin begin(BlockPos target, BlockState state, String blockId, boolean supportRequired) {
        Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, куда ставить");
        Objects.requireNonNull(state, "Состояние не может быть null: без него неизвестно, что ставить");
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, что списывать");
        Level level = spirit.level();
        if (level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null) {
            return new Refused(
                    RefuseReason.CHUNK_NOT_LOADED,
                    "чанк с клеткой " + target.toShortString() + " не загружен: дух не грузит чанки ради стройки");
        }
        boolean action = SchematicActions.isActionPlaced(blockId);
        Begin occupied = PlaceOccupancy.check(spirit, level, target, action);
        if (occupied != null) {
            return occupied;
        }
        BlockCoord feet = feet();
        BlockCoord cell = new BlockCoord(target.getX(), target.getY(), target.getZ());
        SpiritReach reach = SpiritReach.current();
        if (!reach.reaches(feet, cell)) {
            return new Refused(
                    RefuseReason.OUT_OF_REACH,
                    "клетка " + target.toShortString() + " вне досягаемости духа (" + reach.describe()
                            + "): сначала подойти");
        }
        if (spirit.protectionGate().checkPlace(target) instanceof BlockProtection.Refused refused) {
            return new Protected(refused.policy(), refused.reason());
        }
        PlotProtection.Decision plot =
                PlotProtection.check(site(level), cell, PlotProtection.Action.PLACE, ground, BuildPlanOwner.of(spirit));
        if (plot instanceof PlotProtection.Refused refused) {
            return new Protected(PolicyId.PROTECTED_BLOCKS, refused.reason());
        }
        if (action) {
            return SchematicActions.isTilled(blockId)
                    ? farmland(spirit, level, target)
                    : PathTrample.trample(spirit, target);
        }
        Begin guard = placementGuard(level, target, state, supportRequired);
        if (guard != null) {
            return guard;
        }
        Item wanted = BlockItems.itemFor(blockId);
        if (spirit.getInventory().countItem(wanted) < 1) {
            return new Refused(RefuseReason.NO_MATERIAL, "нет материала '" + blockId + "': нужно 1, есть 0");
        }
        if (PlaceParts.firstOfTwoParts(state) && !secondPartFree(level, target, state)) {
            return new Refused(
                    RefuseReason.OCCUPIED,
                    "вторая половина " + blockId + " у " + target.toShortString()
                            + " занята: дверь и кровать ставятся целиком");
        }
        ItemStack used = new ItemStack(wanted);
        spirit.getInventory().removeItemType(wanted, 1);
        SpiritBlocks.place((ServerLevel) spirit.level(), target, state, spirit, blockId);
        if (PlaceParts.firstOfTwoParts(state)) {
            state.getBlock().setPlacedBy(level, target, state, spirit, used);
        } else if (!pairedChestHalf(state)) {
            BlockState updated = Block.updateFromNeighbourShapes(state, level, target);
            if (!updated.equals(state)) {
                SpiritBlocks.place((ServerLevel) spirit.level(), target, updated, spirit, blockId);
            }
        }
        if (!level.getBlockState(target).is(state.getBlock())) {
            spirit.getInventory().addItem(used);
            return new Refused(
                    RefuseReason.NO_SUPPORT,
                    "блок " + blockId + " в " + target.toShortString()
                            + " не держится и сразу исчез: настенному блоку нужна стена со стороны крепления");
        }
        PlaceParts.announce(spirit, level, target, state);
        if (level instanceof ServerLevel server) {
            SettlementChestWatch.placed(server, target, level.getBlockState(target));
        }
        return new Started(target.immutable());
    }

    private static @Nullable StageSite site(Level level) {
        return level instanceof ServerLevel server
                ? SettlementPlotStore.liveSite(server).orElse(null)
                : null;
    }

    private static Begin farmland(SpiritEntity spirit, Level level, BlockPos target) {
        if (new FarmActions(spirit).till(target)) {
            if (level instanceof ServerLevel server) {
                PlacedBlocksData.get(server).markSpirit(target);
            }
            return new Started(target.immutable());
        }
        return new Refused(
                RefuseReason.NO_MATERIAL,
                "тяпкой по " + target.toShortString() + " пашня не вышла: нужна земля или трава и пустая "
                        + "клетка сверху (в клетке " + BlockStates.idOf(level.getBlockState(target))
                        + ", сверху " + BlockStates.idOf(level.getBlockState(target.above())) + ")");
    }

    private @Nullable Begin placementGuard(Level level, BlockPos target, BlockState state, boolean supportRequired) {
        if (supportRequired && !PlaceSupport.hasSupport(level, target, state)) {
            return new Refused(
                    RefuseReason.NO_SUPPORT,
                    "под клеткой " + target.toShortString() + " нет опоры (ниже "
                            + BuiltInRegistries.BLOCK.getKey(
                                    level.getBlockState(target.below()).getBlock())
                            + "): блок без опоры обвалится или повиснет");
        }
        if (!pairedChestHalf(state)) {
            return null;
        }
        ChestPairCheck.Result pair = ChestPairCheck.inspect(spirit, level, target, state);
        return pair.refused() ? new Refused(pair.reason(), pair.message()) : null;
    }

    private BlockCoord feet() {
        BlockPos pos = spirit.blockPosition();
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    // mc: ChestBlock.updateShape сбрасывает половину двойного сундука в SINGLE, пока соседа нет; пара из схемы
    // ставится по одной половине, и вторая половина сама соединит первую.
    static boolean pairedChestHalf(BlockState state) {
        return state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE;
    }

    private boolean secondPartFree(Level level, BlockPos target, BlockState state) {
        BlockPos second = PlaceParts.secondPart(target, state);
        BlockState current = level.getBlockState(second);
        return (current.isAir() || current.canBeReplaced())
                && livingEntities(level, second, spirit).isEmpty();
    }

    static List<LivingEntity> livingEntities(Level level, BlockPos target, SpiritEntity spirit) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(target),
                entity -> !entity.equals(spirit) && !entity.isRemoved() && !entity.isSpectator());
    }
}
