package com.theminespirit.core.board;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.ResourceKnowledge;
import java.util.List;
import java.util.Objects;

/**
 * Точка и параметры задания для сборки плана: куда идти, какая схема, какая приманка.
 * Неизменяемый: план собирается из этих данных, подмена после сборки сломала бы выдачу.
 */
public record TicketTarget(BlockCoord target, String schemeFile, String fenceBlock, String lureItem,
        String supplyItem, int supplyCount, int craterBlocks, Species species,
        BlockCoord from, BlockCoord to, ResourceKnowledge knowledge, List<String> dropItems) {

    public TicketTarget {
        dropItems = List.copyOf(Objects.requireNonNull(dropItems,
                "Список выкинутых предметов не может быть null: задание без предметов задают пустым списком"));
    }

    public static TicketTarget at(BlockCoord target) {
        return new TicketTarget(target, null, null, null, null, 0, 0, null, null, null, null, List.of());
    }

    public TicketTarget withScheme(String scheme) {
        return new TicketTarget(target, scheme, fenceBlock, lureItem, supplyItem, supplyCount,
                craterBlocks, species, from, to, knowledge, dropItems);
    }

    public TicketTarget withFenceBlock(String fence) {
        return new TicketTarget(target, schemeFile, fence, lureItem, supplyItem, supplyCount,
                craterBlocks, species, from, to, knowledge, dropItems);
    }

    public TicketTarget withLureItem(String lure) {
        return new TicketTarget(target, schemeFile, fenceBlock, lure, supplyItem, supplyCount,
                craterBlocks, species, from, to, knowledge, dropItems);
    }

    public TicketTarget withSupply(String item, int count) {
        return new TicketTarget(target, schemeFile, fenceBlock, lureItem, item, count,
                craterBlocks, species, from, to, knowledge, dropItems);
    }

    public TicketTarget withCraterBlocks(int blocks) {
        return new TicketTarget(target, schemeFile, fenceBlock, lureItem, supplyItem, supplyCount,
                blocks, species, from, to, knowledge, dropItems);
    }

    public TicketTarget withSpecies(Species animal) {
        return new TicketTarget(target, schemeFile, fenceBlock, lureItem, supplyItem, supplyCount,
                craterBlocks, animal, from, to, knowledge, dropItems);
    }

    public TicketTarget withRelocate(BlockCoord source, BlockCoord destination) {
        return new TicketTarget(target, schemeFile, fenceBlock, lureItem, supplyItem, supplyCount,
                craterBlocks, species, source, destination, knowledge, dropItems);
    }

    public TicketTarget withKnowledge(ResourceKnowledge known) {
        return new TicketTarget(target, schemeFile, fenceBlock, lureItem, supplyItem, supplyCount,
                craterBlocks, species, from, to, known, dropItems);
    }

    public TicketTarget withDrops(List<String> items) {
        return new TicketTarget(target, schemeFile, fenceBlock, lureItem, supplyItem, supplyCount,
                craterBlocks, species, from, to, knowledge, items);
    }
}
