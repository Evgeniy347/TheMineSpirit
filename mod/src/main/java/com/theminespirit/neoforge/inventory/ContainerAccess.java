package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.neoforge.BlockSets;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public final class ContainerAccess {
    private static final IdFilter ALLOWED = IdFilter.of(
            List.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:*shulker_box"));

    private ContainerAccess() {}

    public sealed interface Open permits Opened, Refused {}

    public record Opened(Container container) implements Open {}

    public record Refused(String reason) implements Open {}

    public static boolean allowed(BlockState state) {
        return ALLOWED.matches(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    public static Open open(Level level, BlockPos pos) {
        return read(level, pos, false);
    }

    public static Open inspect(Level level, BlockPos pos) {
        return read(level, pos, true);
    }

    private static Open read(Level level, BlockPos pos, boolean ignoreLid) {
        if (!level.isLoaded(pos)) {
            return new Refused("контейнер в " + pos.toShortString() + " не загружен: содержимое неизвестно");
        }
        BlockState state = level.getBlockState(pos);
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!ALLOWED.matches(id)) {
            return new Refused("блок " + id + " в " + pos.toShortString() + " не допустимый контейнер: "
                    + "годятся сундуки (в том числе двойные), бочки и шалкер-боксы, а печи, воронки, "
                    + "раздатчики и эндер-сундук запрещены");
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof RandomizableContainerBlockEntity random && random.getLootTable() != null) {
            return new Refused("контейнер " + id + " в " + pos.toShortString()
                    + " с нераскрытой таблицей добычи (сундук структуры): сначала его должен открыть игрок, "
                    + "иначе дух забрал бы чужую добычу");
        }
        if (state.getBlock() instanceof ChestBlock chest) {
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos partner = pos.relative(ChestBlock.getConnectedDirection(state));
                if (!level.isLoaded(partner)) {
                    return new Refused("вторая половина сундука в " + partner.toShortString() + " не загружена");
                }
                BlockState other = level.getBlockState(partner);
                if (!other.is(chest)
                        || other.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                        || other.getValue(ChestBlock.TYPE) == state.getValue(ChestBlock.TYPE)
                        || other.getValue(ChestBlock.FACING) != state.getValue(ChestBlock.FACING)) {
                    return new Refused("вторая половина сундука в " + partner.toShortString() + " не образует пару");
                }
                if (!(level.getBlockEntity(partner) instanceof RandomizableContainerBlockEntity paired)
                        || paired.getLootTable() != null) {
                    return new Refused("вторая половина сундука в " + partner.toShortString()
                            + " недоступна или хранит нераскрытую добычу структуры");
                }
            }
            Container both = ChestBlock.getContainer(chest, state, level, pos, ignoreLid);
            if (both == null) {
                return new Refused("сундук в " + pos.toShortString()
                        + " недоступен: нет блочной сущности сундука, проверьте, что блок стоит целиком");
            }
            return new Opened(both);
        }
        if (entity instanceof BaseContainerBlockEntity base) {
            return new Opened(base);
        }
        return new Refused(
                "блок " + id + " в " + pos.toShortString() + " без блочной сущности контейнера: открыть нечего");
    }

    public static Set<Block> allowedBlocks() {
        return BlockSets.resolve(ALLOWED);
    }
}
