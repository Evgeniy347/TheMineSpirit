package com.theminespirit.core.board;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.ResourceKnowledge;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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
        int count,
        @Nullable String wood,
        @Nullable PlotLeveling leveling) {

    public TicketTarget {
        dropItems = List.copyOf(Objects.requireNonNull(
                dropItems,
                "Список выкинутых предметов не может быть null: задание без предметов задают пустым списком"));
    }

    public static TicketTarget at(BlockCoord target) {
        return new TicketTarget(
                target, null, null, null, null, 0, 0, 0, null, null, null, null, List.of(), null, null, null, 0, null,
                null);
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
                count,
                wood,
                leveling);
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
                count,
                wood,
                leveling);
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
                count,
                wood,
                leveling);
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
                count,
                wood,
                leveling);
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
                count,
                wood,
                leveling);
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
                count,
                wood,
                leveling);
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
                count,
                wood,
                leveling);
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
                count,
                wood,
                leveling);
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
                count,
                wood,
                leveling);
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
                count,
                wood,
                leveling);
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
                amount,
                wood,
                leveling);
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
                amount,
                wood,
                leveling);
    }

    public TicketTarget withWood(String woodSpecies) {
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
                count,
                Objects.requireNonNull(woodSpecies, "Порода дерева не может быть null: без неё строят по схеме"),
                leveling);
    }

    public TicketTarget withLeveling(PlotLeveling plan) {
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
                count,
                wood,
                Objects.requireNonNull(
                        plan, "План выравнивания не может быть null: ровный участок задают пустым планом"));
    }
}
