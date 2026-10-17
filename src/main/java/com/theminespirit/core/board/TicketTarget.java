package com.theminespirit.core.board;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.ResourceKnowledge;

/**
 * Точка и параметры задания для сборки плана: куда идти, какая схема, какая приманка,
 * сколько комплектов инструментов. Неизменяемый: план собирается из этих данных,
 * подмена после сборки сломала бы выдачу.
 */
public record TicketTarget(BlockCoord target, String schemeFile, String fenceBlock, String lureItem,
        String supplyItem, int supplyCount, int craterBlocks, int toolCount, Species species,
        BlockCoord from, BlockCoord to, ResourceKnowledge knowledge) {

    public static TicketTarget at(BlockCoord target) {
        return new TicketTarget(target, null, null, null, null, 0, 0, 0, null, null, null, null);
    }

    public TicketTarget withScheme(String scheme) {
        return new TicketTarget(target, scheme, fenceBlock, lureItem, supplyItem, supplyCount,
                craterBlocks, toolCount, species, from, to, knowledge);
    }

    public TicketTarget withFenceBlock(String fence) {
        return new TicketTarget(target, schemeFile, fence, lureItem, supplyItem, supplyCount,
                craterBlocks, toolCount, species, from, to, knowledge);
    }

    public TicketTarget withLureItem(String lure) {
        return new TicketTarget(target, schemeFile, fenceBlock, lure, supplyItem, supplyCount,
                craterBlocks, toolCount, species, from, to, knowledge);
    }

    public TicketTarget withSupply(String item, int count) {
        return new TicketTarget(target, schemeFile, fenceBlock, lureItem, item, count,
                craterBlocks, toolCount, species, from, to, knowledge);
    }

    public TicketTarget withCraterBlocks(int blocks) {
        return new TicketTarget(target, schemeFile, fenceBlock, lureItem, supplyItem, supplyCount,
                blocks, toolCount, species, from, to, knowledge);
    }

    public TicketTarget withToolCount(int count) {
        return new TicketTarget(target, schemeFile, fenceBlock, lureItem, supplyItem, supplyCount,
                craterBlocks, count, species, from, to, knowledge);
    }

    public TicketTarget withSpecies(Species animal) {
        return new TicketTarget(target, schemeFile, fenceBlock, lureItem, supplyItem, supplyCount,
                craterBlocks, toolCount, animal, from, to, knowledge);
    }

    public TicketTarget withRelocate(BlockCoord source, BlockCoord destination) {
        return new TicketTarget(target, schemeFile, fenceBlock, lureItem, supplyItem, supplyCount,
                craterBlocks, toolCount, species, source, destination, knowledge);
    }

    public TicketTarget withKnowledge(ResourceKnowledge known) {
        return new TicketTarget(target, schemeFile, fenceBlock, lureItem, supplyItem, supplyCount,
                craterBlocks, toolCount, species, from, to, known);
    }
}
