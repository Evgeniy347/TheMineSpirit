package com.theminespirit.core.board;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.movement.SelfEscape;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.ResourceKnowledge;
import com.theminespirit.core.town.StageWork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        @Nullable String building,
        @Nullable PlotLeveling leveling,
        @Nullable StageWork work,
        SelfEscape.@Nullable Plan rescue,
        @Nullable BlockCoord deposit) {

    public TicketTarget {
        dropItems = List.copyOf(Objects.requireNonNull(
                dropItems,
                "Список выкинутых предметов не может быть null: задание без предметов задают пустым списком"));
    }

    public static TicketTarget at(BlockCoord target) {
        return new Builder(target).build();
    }

    public List<String> details() {
        List<String> details = new ArrayList<>();
        details.add(BoardActivities.TARGET_KEY + coords(target));
        if (schemeFile != null) {
            details.add("scheme=" + schemeFile);
        }
        if (building != null) {
            details.add("building=" + building);
        }
        if (wood != null) {
            details.add("wood=" + wood);
        }
        if (fenceBlock != null) {
            details.add("fence=" + fenceBlock);
        }
        if (lureItem != null) {
            details.add("lure=" + lureItem);
        }
        if (supplyItem != null) {
            details.add("supply=" + supplyItem + "×" + supplyCount);
        }
        if (craftItem != null) {
            details.add("craft=" + craftItem + "×" + count);
        }
        if (species != null) {
            details.add("species=" + species.name().toLowerCase(Locale.ROOT));
        }
        if (from != null) {
            details.add("from=" + coords(from));
        }
        if (to != null) {
            details.add("to=" + coords(to));
        }
        if (deposit != null) {
            details.add("deposit=" + coords(deposit));
        }
        if (toolCount > 0) {
            details.add("tools=" + toolCount);
        }
        if (craterBlocks > 0) {
            details.add("crater=" + craterBlocks);
        }
        if (!dropItems.isEmpty()) {
            details.add("drops=" + String.join(",", dropItems));
        }
        return List.copyOf(details);
    }

    private static String coords(BlockCoord at) {
        return at.x() + "," + at.y() + "," + at.z();
    }

    public TicketTarget withScheme(String scheme) {
        Builder builder = new Builder(this);
        builder.schemeFile = scheme;
        return builder.build();
    }

    public TicketTarget withFenceBlock(String fence) {
        Builder builder = new Builder(this);
        builder.fenceBlock = fence;
        return builder.build();
    }

    public TicketTarget withLureItem(String lure) {
        Builder builder = new Builder(this);
        builder.lureItem = lure;
        return builder.build();
    }

    public TicketTarget withSupply(String item, int supply) {
        Builder builder = new Builder(this);
        builder.supplyItem = item;
        builder.supplyCount = supply;
        return builder.build();
    }

    public TicketTarget withDeposit(@Nullable BlockCoord point) {
        Builder builder = new Builder(this);
        builder.deposit = point;
        return builder.build();
    }

    public TicketTarget withCraterBlocks(int blocks) {
        Builder builder = new Builder(this);
        builder.craterBlocks = blocks;
        return builder.build();
    }

    public TicketTarget withToolCount(int tools) {
        Builder builder = new Builder(this);
        builder.toolCount = tools;
        return builder.build();
    }

    public TicketTarget withSpecies(Species animal) {
        Builder builder = new Builder(this);
        builder.species = animal;
        return builder.build();
    }

    public TicketTarget withRelocate(BlockCoord source, BlockCoord destination) {
        Builder builder = new Builder(this);
        builder.from = source;
        builder.to = destination;
        return builder.build();
    }

    public TicketTarget withDrops(List<String> items) {
        Builder builder = new Builder(this);
        builder.dropItems = items;
        return builder.build();
    }

    public TicketTarget withTransfer(@Nullable UUID source, UUID recipient, String item, int amount) {
        Builder builder = new Builder(this);
        builder.supplyItem = item;
        builder.supplyCount = amount;
        builder.transferFrom = source;
        builder.transferTo = recipient;
        return builder.build();
    }

    public TicketTarget withCraft(String item, int amount) {
        Builder builder = new Builder(this);
        builder.craftItem = item;
        builder.count = amount;
        return builder.build();
    }

    public TicketTarget withCount(int amount) {
        Builder builder = new Builder(this);
        builder.count = amount;
        return builder.build();
    }

    public TicketTarget withWood(String woodSpecies) {
        Builder builder = new Builder(this);
        builder.wood = Objects.requireNonNull(woodSpecies, "Порода дерева не может быть null: без неё строят по схеме");
        return builder.build();
    }

    public TicketTarget withBuilding(String buildingId) {
        Builder builder = new Builder(this);
        builder.building =
                Objects.requireNonNull(buildingId, "Постройка графа не может быть null: по ней берут участок");
        return builder.build();
    }

    public TicketTarget withLeveling(PlotLeveling plan) {
        Builder builder = new Builder(this);
        builder.leveling = Objects.requireNonNull(
                plan, "План выравнивания не может быть null: ровный участок задают пустым планом");
        return builder.build();
    }

    public TicketTarget withWork(StageWork stage) {
        Builder builder = new Builder(this);
        builder.work = Objects.requireNonNull(stage, "Работа этапа не может быть null: без плана задают at()");
        return builder.build();
    }

    public TicketTarget withRescue(SelfEscape.Plan plan) {
        Builder builder = new Builder(this);
        builder.rescue = Objects.requireNonNull(plan, "План спасения не может быть null: иначе задают at()");
        return builder.build();
    }

    private static final class Builder {
        private BlockCoord target;
        private @Nullable String schemeFile;
        private @Nullable String fenceBlock;
        private @Nullable String lureItem;
        private @Nullable String supplyItem;
        private int supplyCount;
        private int craterBlocks;
        private int toolCount;
        private @Nullable Species species;
        private @Nullable BlockCoord from;
        private @Nullable BlockCoord to;
        private @Nullable ResourceKnowledge knowledge;
        private List<String> dropItems = List.of();
        private @Nullable UUID transferFrom;
        private @Nullable UUID transferTo;
        private @Nullable String craftItem;
        private int count;
        private @Nullable String wood;
        private @Nullable String building;
        private @Nullable PlotLeveling leveling;
        private @Nullable StageWork work;
        private SelfEscape.@Nullable Plan rescue;
        private @Nullable BlockCoord deposit;

        Builder(BlockCoord target) {
            this.target = Objects.requireNonNull(target, "Цель задания не может быть null: от неё строится план");
        }

        Builder(TicketTarget source) {
            target = source.target;
            schemeFile = source.schemeFile;
            fenceBlock = source.fenceBlock;
            lureItem = source.lureItem;
            supplyItem = source.supplyItem;
            supplyCount = source.supplyCount;
            craterBlocks = source.craterBlocks;
            toolCount = source.toolCount;
            species = source.species;
            from = source.from;
            to = source.to;
            knowledge = source.knowledge;
            dropItems = source.dropItems;
            transferFrom = source.transferFrom;
            transferTo = source.transferTo;
            craftItem = source.craftItem;
            count = source.count;
            wood = source.wood;
            building = source.building;
            leveling = source.leveling;
            work = source.work;
            rescue = source.rescue;
            deposit = source.deposit;
        }

        TicketTarget build() {
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
                    building,
                    leveling,
                    work,
                    rescue,
                    deposit);
        }
    }
}
