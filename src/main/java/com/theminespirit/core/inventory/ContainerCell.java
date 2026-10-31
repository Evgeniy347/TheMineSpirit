package com.theminespirit.core.inventory;

import com.theminespirit.core.observation.BlockCoord;
import org.jspecify.annotations.Nullable;

public record ContainerCell(
        BlockCoord pos,
        String block,
        boolean chestBlock,
        boolean chestTypeProperty,
        @Nullable String chestType,
        @Nullable String entity) {

    public static final String AIR = "minecraft:air";
    public static final String CHEST_ENTITY = "minecraft:chest";

    public enum Problem {
        NONE("расхождения нет"),
        AIR_WITH_ENTITY("блок air, а BlockEntity есть"),
        BLOCK_WITHOUT_ENTITY("блок контейнера, а BlockEntity нет"),
        ENTITY_BLOCK_MISMATCH("BlockEntity сундука стоит на блоке другого типа"),
        CHEST_WITHOUT_TYPE("сундук без свойства type");

        private final String text;

        Problem(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }

    public Problem problem() {
        if (entity != null && AIR.equals(block)) {
            return Problem.AIR_WITH_ENTITY;
        }
        if (chestBlock && entity == null) {
            return Problem.BLOCK_WITHOUT_ENTITY;
        }
        if (CHEST_ENTITY.equals(entity) && !chestBlock) {
            return Problem.ENTITY_BLOCK_MISMATCH;
        }
        if (chestBlock && CHEST_ENTITY.equals(entity) && !chestTypeProperty) {
            return Problem.CHEST_WITHOUT_TYPE;
        }
        return Problem.NONE;
    }

    public boolean hasChestType() {
        return chestBlock && chestTypeProperty;
    }

    public String describe() {
        return "pos=" + pos + " block=" + block + " be=" + (entity == null ? "нет" : entity) + " type="
                + (chestType == null ? "нет" : chestType);
    }
}
