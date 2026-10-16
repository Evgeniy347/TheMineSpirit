package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.StockScan;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

/**
 * Снимок запасов поселения для гейта доски: инвентари духов плюс контейнеры вокруг заявок.
 * Мир читается только через {@link StockScan}: обход бюджетирован счётчиком блоков и
 * контейнеров, незагруженные чанки пропускаются (загрузка чанка ждёт генерации и подвесила
 * бы серверный тик — причина 41-минутного GameTest). Центры обхода — цели заявок, а не
 * позиции духов: снимок читает склад там, где идёт работа, и не растёт от числа духов.
 * Идентификаторы предметов приводятся к короткому виду ({@code minecraft:dirt} → {@code dirt}):
 * гейт сравнивает с ключами {@code CraterNeed.estimate} и {@code AnimalLifecycle}.
 */
public final class StockReader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CONTAINER_RADIUS = 8;
    private static final int SCAN_HEIGHT = 4;
    private static final String MINECRAFT_PREFIX = "minecraft:";

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
        Map<String, Integer> available = new HashMap<>();
        for (SpiritEntity spirit : spirits) {
            for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
                add(available, spirit.getInventory().getItem(slot));
            }
        }
        StockScan.Result found = StockScan.containers(rotate(centers), CONTAINER_RADIUS, SCAN_HEIGHT,
                blockBudget, blocks(level));
        int reads = 0;
        for (BlockCoord pos : found.containers()) {
            if (reads >= containerBudget) {
                break;
            }
            ContainerAccess.Open open = ContainerAccess.open(level, new BlockPos(pos.x(), pos.y(), pos.z()));
            if (open instanceof ContainerAccess.Opened container) {
                StackMapper mapper = new StackMapper();
                for (Stack stack : mapper.map(container.container())) {
                    if (stack != null) {
                        add(available, stack);
                    }
                }
                reads++;
            }
        }
        if (reads >= containerBudget) {
            LOGGER.debug("Снимок запасов обрезан бюджетом {} контейнеров: остальные сундуки "
                    + "дочитаются на следующей пересборке", containerBudget);
        }
        return new Snapshot(new StockView(available), found.blockReads(), reads);
    }

    /**
     * Итог снимка: запасы и израсходованные бюджеты — по ним тест масштаба видит, что обход
     * мира не растёт от числа духов и пересборок.
     */
    public record Snapshot(StockView view, int blockReads, int containers) {
        public Snapshot {
            if (blockReads < 0 || containers < 0) {
                throw new IllegalArgumentException("Счётчики снимка не бывают отрицательными: чтений "
                        + blockReads + ", контейнеров " + containers);
            }
        }
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
                return allowed.contains(level.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z())).getBlock());
            }
        };
    }

    /** Крутит центры между пересборками: при жёстком бюджете разные пересборки видят разные сундуки. */
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
    }

    /**
     * Контейнеры отдают полный id ({@code minecraft:oak_log}): без приведения к короткому виду
     * гейт искал бы {@code oak_log} и не видел бы склад, выдавая задание добычи снова и снова.
     */
    private static void add(Map<String, Integer> available, Stack stack) {
        available.merge(shortId(stack.item()), stack.count(), Integer::sum);
    }

    private static String shortId(String itemId) {
        return itemId.startsWith(MINECRAFT_PREFIX) ? itemId.substring(MINECRAFT_PREFIX.length()) : itemId;
    }
}
