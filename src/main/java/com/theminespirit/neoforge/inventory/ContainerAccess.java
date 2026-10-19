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

public final class ContainerAccess {
    private static final IdFilter ALLOWED = IdFilter.of(
            List.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:*shulker_box"));

    private ContainerAccess() {}

    public sealed interface Open permits Opened, Refused {}

    public record Opened(Container container) implements Open {}

    public record Refused(String reason) implements Open {}

    public static Open open(Level level, BlockPos pos) {
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
            Container both = ChestBlock.getContainer(chest, state, level, pos, false);
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
