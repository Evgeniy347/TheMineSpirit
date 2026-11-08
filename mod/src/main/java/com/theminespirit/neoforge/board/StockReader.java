package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.StockParts;
import com.theminespirit.core.board.StockScan;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.mining.LeftOreRules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

public final class StockReader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CONTAINER_RADIUS = 8;
    private static final int SCAN_HEIGHT = 4;
    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final String POTION_PREFIX = "potion_";
    private static final String SPLASH_PREFIX = "splash_potion_";

    private final int containerBudget;
    private final int blockBudget;
    private int cursor;

    public StockReader(int containerBudget, int blockBudget) {
        if (containerBudget <= 0) {
            throw new IllegalArgumentException("containerBudget равен " + containerBudget
                    + " и не положителен: снимок без бюджета читает склад целиком за пересборку");
        }
        if (blockBudget <= 0) {
            throw new IllegalArgumentException("blockBudget равен " + blockBudget
                    + " и не положителен: снимок без бюджета обходит мир целиком за пересборку");
        }
        this.containerBudget = containerBudget;
        this.blockBudget = blockBudget;
    }

    public Snapshot collect(ServerLevel level, List<SpiritEntity> spirits, List<BlockCoord> centers) {
        Map<String, Integer> chests = new HashMap<>();
        Map<String, Integer> carried = new HashMap<>();
        for (SpiritEntity spirit : spirits) {
            for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
                add(carried, spirit.getInventory().getItem(slot));
            }
            if (spirit.mainHandOwned()) {
                add(carried, spirit.getMainHandItem());
            }
        }
        StockScan.Result found =
                StockScan.containers(rotate(centers), CONTAINER_RADIUS, SCAN_HEIGHT, blockBudget, blocks(level));
        int reads = 0;
        for (BlockCoord pos : StockScan.knownFirst(ChestSnapshots.positions(level), found.containers())) {
            if (reads >= containerBudget) {
                break;
            }
            if (!ChestSnapshots.countable(level, pos)) {
                continue;
            }
            BlockPos at = new BlockPos(pos.x(), pos.y(), pos.z());
            ContainerAccess.Open open = ContainerAccess.open(level, at);
            if (open instanceof ContainerAccess.Opened container) {
                StackMapper mapper = new StackMapper();
                for (Stack stack : mapper.mapPresent(container.container())) {
                    add(chests, stack);
                }
                for (int slot = 0; slot < container.container().getContainerSize(); slot++) {
                    addPotion(chests, container.container().getItem(slot));
                }
                reads++;
            }
        }
        if (reads >= containerBudget) {
            LOGGER.debug(
                    "Снимок запасов обрезан бюджетом {} контейнеров: остальные сундуки "
                            + "дочитаются на следующей пересборке",
                    containerBudget);
        }
        return new Snapshot(new StockParts(new StockView(chests), new StockView(carried)), found.blockReads(), reads);
    }

    public record Snapshot(StockParts parts, int blockReads, int containers) {
        public Snapshot {
            if (blockReads < 0 || containers < 0) {
                throw new IllegalArgumentException("Счётчики снимка не бывают отрицательными: чтений " + blockReads
                        + ", контейнеров " + containers);
            }
        }
    }

    public static List<ContainerSnapshot> chests(ServerLevel level) {
        return ChestSnapshots.read(level, ChestSnapshots.positions(level));
    }

    public static List<ContainerSnapshot> chests(ServerLevel level, ColumnMask area) {
        Set<BlockCoord> positions = new LinkedHashSet<>();
        for (BlockCoord pos : SettlementChestStore.get(level).chests().all()) {
            if (area.contains(pos.x(), pos.z())) {
                positions.add(pos);
            }
        }
        positions.addAll(ChestSnapshots.inArea(level, area));
        return ChestSnapshots.read(level, List.copyOf(positions));
    }

    private static StockScan.Blocks blocks(ServerLevel level) {
        Set<Block> allowed = ContainerAccess.allowedBlocks();
        return new StockScan.Blocks() {
            @Override
            public boolean isLoaded(BlockCoord pos) {
                return level.isLoaded(new BlockPos(pos.x(), pos.y(), pos.z()));
            }

            @Override
            public boolean isContainer(BlockCoord pos) {
                return allowed.contains(level.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()))
                        .getBlock());
            }
        };
    }

    private List<BlockCoord> rotate(List<BlockCoord> centers) {
        if (centers.size() <= 1) {
            return centers;
        }
        List<BlockCoord> ordered = new ArrayList<>(centers);
        Collections.rotate(ordered, -(cursor % ordered.size()));
        cursor++;
        return ordered;
    }

    private static void add(Map<String, Integer> available, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        available.merge(shortId(StackMapper.itemId(stack)), stack.getCount(), Integer::sum);
        addPotion(available, stack);
        if (stack.getItem() instanceof PickaxeItem) {
            for (String key : pickaxeKeys(stack)) {
                available.merge(key, 1, Integer::sum);
            }
        }
    }

    static List<String> pickaxeKeys(ItemStack stack) {
        List<String> keys = new ArrayList<>();
        if (stack.isCorrectToolForDrops(Blocks.COAL_ORE.defaultBlockState())) {
            keys.add(LeftOreRules.WOOD_TIER);
        }
        if (stack.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState())) {
            keys.add(LeftOreRules.STONE_TIER);
        }
        boolean ironTier = stack.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState());
        if (ironTier) {
            keys.add(LeftOreRules.IRON_TIER);
        }
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        boolean lucky = enchantments.keySet().stream()
                .anyMatch(holder -> holder.is(Enchantments.FORTUNE) || holder.is(Enchantments.SILK_TOUCH));
        if (ironTier && lucky) {
            keys.add(LeftOreRules.LUCK_PICKAXE);
        }
        return keys;
    }

    private static void add(Map<String, Integer> available, Stack stack) {
        available.merge(shortId(stack.item()), stack.count(), Integer::sum);
    }

    private static void addPotion(Map<String, Integer> available, ItemStack stack) {
        if (!stack.is(Items.POTION) && !stack.is(Items.SPLASH_POTION)) {
            return;
        }
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return;
        }
        String prefix = stack.is(Items.SPLASH_POTION) ? SPLASH_PREFIX : POTION_PREFIX;
        contents.potion()
                .flatMap(holder -> holder.unwrapKey())
                .ifPresent(key -> available.merge(prefix + key.location().getPath(), stack.getCount(), Integer::sum));
    }

    private static String shortId(String itemId) {
        return itemId.startsWith(MINECRAFT_PREFIX) ? itemId.substring(MINECRAFT_PREFIX.length()) : itemId;
    }
}
