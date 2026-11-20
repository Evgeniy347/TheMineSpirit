package com.theminespirit.neoforge;

import com.theminespirit.core.building.SchematicBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class BlockStates {
    private static final String DEFAULT_NAMESPACE = "minecraft";

    private BlockStates() {}

    public static Map<String, String> properties(String block) {
        Objects.requireNonNull(block, "Строка блока не может быть null: без неё свойств не прочитать");
        int start = block.indexOf('[');
        if (start < 0) {
            return Map.of();
        }
        if (!block.endsWith("]")) {
            throw new IllegalArgumentException(
                    "Состояние блока '" + block + "' без закрывающей скобки: ожидалось id[свойство=значение,…]");
        }
        Map<String, String> properties = new LinkedHashMap<>();
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

    public static Block blockFor(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, что ставить");
        if (blockId.isBlank()) {
            throw new IllegalArgumentException("Id блока пуст: по пустому id блок не найти");
        }
        String placedId = SchematicBlocks.placementBlock(blockId);
        String qualified = placedId.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ":" + placedId : placedId;
        ResourceLocation location = ResourceLocation.tryParse(qualified);
        Block block = location == null
                ? null
                : BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
        if (block == null || Blocks.AIR.equals(block)) {
            throw new IllegalArgumentException("Неизвестный блок '" + placedId + "': такого id нет в реестре блоков");
        }
        return block;
    }

    public static BlockState stateFor(String blockId) {
        return blockFor(blockId).defaultBlockState();
    }

    public static BlockState stateFor(String blockId, Map<String, String> properties) {
        Objects.requireNonNull(properties, "Свойства блока не могут быть null: пустые задаются пустой картой");
        BlockState state = stateFor(blockId);
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            state = withProperty(state, entry.getKey(), entry.getValue());
        }
        return state;
    }

    private static BlockState withProperty(BlockState state, String name, String value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) {
            throw new IllegalArgumentException("У блока '" + idOf(state) + "' нет свойства '" + name
                    + "': схема задаёт состояние, которого у блока не существует");
        }
        return setValue(state, property, value);
    }

    private static <T extends Comparable<T>> BlockState setValue(BlockState state, Property<T> property, String value) {
        return property.getValue(value)
                .map(parsed -> state.setValue(property, parsed))
                .orElseThrow(() -> new IllegalArgumentException("У свойства '" + property.getName() + "' блока '"
                        + idOf(state) + "' нет значения '" + value + "': допустимы " + property.getPossibleValues()
                        + ", опечатка в схеме поставила бы блок не так"));
    }

    public static String idOf(BlockState state) {
        Objects.requireNonNull(state, "Состояние блока не может быть null: без него неизвестно, чей id называть");
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }
}
