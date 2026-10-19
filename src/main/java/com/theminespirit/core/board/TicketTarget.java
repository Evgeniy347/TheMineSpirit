package com.theminespirit.core.board;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.ResourceKnowledge;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Точка и параметры задания для сборки плана: куда идти, какая схема, какая приманка,
 * сколько комплектов инструментов, от кого и кому передать предмет, какой предмет крафтить
 * и сколько голов или предметов обработать. Неизменяемый: план собирается из этих данных,
 * подмена после сборки сломала бы выдачу.
 */
public record TicketTarget(
        BlockCoord target,
        @Nullable String schemeFile,
        @Nullable String fenceBlock,
        @Nullable String lureItem,
        @Nullable String supplyItem,
        int supplyCount,
        int craterBlocks,
        int toolCount,
        @Nullable Species species,
        @Nullable BlockCoord from,
        @Nullable BlockCoord to,
        @Nullable ResourceKnowledge knowledge,
        List<String> dropItems,
        @Nullable UUID transferFrom,
        @Nullable UUID transferTo,
        @Nullable String craftItem,
        int count) {

    public TicketTarget {
        dropItems = List.copyOf(Objects.requireNonNull(
                dropItems,
                "Список выкинутых предметов не может быть null: задание без предметов задают пустым списком"));
    }

    public static TicketTarget at(BlockCoord target) {
        return new TicketTarget(
                target, null, null, null, null, 0, 0, 0, null, null, null, null, List.of(), null, null, null, 0);
    }

    public TicketTarget withScheme(String scheme) {
        return new TicketTarget(
                target,
                scheme,
                fenceBlock,
                lureItem,
                supplyItem,
                supplyCount,
                craterBlocks,
                toolCount,
                species,
                from,
                to,
                knowledge,
                dropItems,
                transferFrom,
                transferTo,
                craftItem,
                count);
    }

    public TicketTarget withFenceBlock(String fence) {
        return new TicketTarget(
                target,
                schemeFile,
                fence,
                lureItem,
                supplyItem,
                supplyCount,
                craterBlocks,
                toolCount,
                species,
                from,
                to,
                knowledge,
                dropItems,
                transferFrom,
                transferTo,
                craftItem,
                count);
    }

    public TicketTarget withLureItem(String lure) {
        return new TicketTarget(
                target,
                schemeFile,
                fenceBlock,
                lure,
                supplyItem,
                supplyCount,
                craterBlocks,
                toolCount,
                species,
                from,
                to,
                knowledge,
                dropItems,
                transferFrom,
                transferTo,
                craftItem,
                count);
    }

    public TicketTarget withSupply(String item, int supply) {
        return new TicketTarget(
                target,
                schemeFile,
                fenceBlock,
                lureItem,
                item,
                supply,
                craterBlocks,
                toolCount,
                species,
                from,
                to,
                knowledge,
                dropItems,
                transferFrom,
                transferTo,
                craftItem,
                count);
    }

    public TicketTarget withCraterBlocks(int blocks) {
        return new TicketTarget(
                target,
                schemeFile,
                fenceBlock,
                lureItem,
                supplyItem,
                supplyCount,
                blocks,
                toolCount,
                species,
                from,
                to,
                knowledge,
                dropItems,
                transferFrom,
                transferTo,
                craftItem,
                count);
    }

    public TicketTarget withToolCount(int tools) {
        return new TicketTarget(
                target,
                schemeFile,
                fenceBlock,
                lureItem,
                supplyItem,
                supplyCount,
                craterBlocks,
                tools,
                species,
                from,
                to,
                knowledge,
                dropItems,
                transferFrom,
                transferTo,
                craftItem,
                count);
    }

    public TicketTarget withSpecies(Species animal) {
        return new TicketTarget(
                target,
                schemeFile,
                fenceBlock,
                lureItem,
                supplyItem,
                supplyCount,
                craterBlocks,
                toolCount,
                animal,
                from,
                to,
                knowledge,
                dropItems,
                transferFrom,
                transferTo,
                craftItem,
                count);
    }

    public TicketTarget withRelocate(BlockCoord source, BlockCoord destination) {
        return new TicketTarget(
                target,
                schemeFile,
                fenceBlock,
                lureItem,
                supplyItem,
                supplyCount,
                craterBlocks,
                toolCount,
                species,
                source,
                destination,
                knowledge,
                dropItems,
                transferFrom,
                transferTo,
                craftItem,
                count);
    }

    public TicketTarget withDrops(List<String> items) {
        return new TicketTarget(
                target,
                schemeFile,
                fenceBlock,
                lureItem,
                supplyItem,
                supplyCount,
                craterBlocks,
                toolCount,
                species,
                from,
                to,
                knowledge,
                items,
                transferFrom,
                transferTo,
                craftItem,
                count);
    }

    public TicketTarget withTransfer(UUID source, UUID recipient, String item, int amount) {
        return new TicketTarget(
                target,
                schemeFile,
                fenceBlock,
                lureItem,
                item,
                amount,
                craterBlocks,
                toolCount,
                species,
                from,
                to,
                knowledge,
                dropItems,
                source,
                recipient,
                craftItem,
                count);
    }

    public TicketTarget withCraft(String item, int amount) {
        return new TicketTarget(
                target,
                schemeFile,
                fenceBlock,
                lureItem,
                supplyItem,
                supplyCount,
                craterBlocks,
                toolCount,
                species,
                from,
                to,
                knowledge,
                dropItems,
                transferFrom,
                transferTo,
                item,
                amount);
    }

    public TicketTarget withCount(int amount) {
        return new TicketTarget(
                target,
                schemeFile,
                fenceBlock,
                lureItem,
                supplyItem,
                supplyCount,
                craterBlocks,
                toolCount,
                species,
                from,
                to,
                knowledge,
                dropItems,
                transferFrom,
                transferTo,
                craftItem,
                amount);
    }
}
