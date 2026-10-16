package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.intellect.FoodNorm;
import com.theminespirit.core.intellect.NutritionTable;
import com.theminespirit.core.intellect.StockCatalog;
import com.theminespirit.core.intellect.StockLots;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class StockReader {
    public static final int STOCK_READ_BUDGET = 4096;
    public static final int STOCK_CONTAINER_BUDGET = 8;
    public static final int STOCK_RADIUS = 8;
    public static final int STOCK_HEIGHT = 4;

    private StockReader() {
    }

    public record Stocks(StockLots lots, String containerFood, int reads, int containers) {
        public Stocks {
            if (lots == null || containerFood == null) {
                throw new IllegalArgumentException(
                        "Запасы без долей или еды склада не описываются: отсутствие задают пустыми значениями");
            }
        }
    }

    public static Stocks read(ServerLevel level, List<SpiritEntity> members, Optional<Point> home) {
        if (level == null) {
            throw new IllegalArgumentException("Запасы без уровня не собрать: сундуки стоят в мире");
        }
        if (members == null) {
            throw new IllegalArgumentException("Запасы без духов не собрать: инвентари принадлежат духам");
        }
        Map<String, Integer> items = new HashMap<>();
        for (SpiritEntity spirit : members) {
            if (spirit.isRemoved()) {
                continue;
            }
            tallyContainer(spirit.getInventory(), items);
        }
        Map<String, Integer> containerItems = new HashMap<>();
        int reads = 0;
        int containers = 0;
        BlockPos center = centerOf(members, home);
        Set<Block> allowed = ContainerAccess.allowedBlocks();
        if (center != null) {
            for (int dx = -STOCK_RADIUS; dx <= STOCK_RADIUS && containers < STOCK_CONTAINER_BUDGET; dx++) {
                for (int dy = -STOCK_HEIGHT; dy <= STOCK_HEIGHT && containers < STOCK_CONTAINER_BUDGET; dy++) {
                    for (int dz = -STOCK_RADIUS; dz <= STOCK_RADIUS
                            && containers < STOCK_CONTAINER_BUDGET; dz++) {
                        reads++;
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (!level.isLoaded(pos)) {
                            continue;
                        }
                        if (!allowed.contains(level.getBlockState(pos).getBlock())) {
                            continue;
                        }
                        if (ContainerAccess.open(level, pos) instanceof ContainerAccess.Opened opened) {
                            tallyContainer(opened.container(), items);
                            tallyContainer(opened.container(), containerItems);
                            containers++;
                        }
                    }
                }
            }
        }
        NutritionTable table = StockCatalog.defaultTable();
        return new Stocks(new StockCatalog(table).tally(items), FoodNorm.best(table, containerItems), reads,
                containers);
    }

    private static void tallyContainer(Container container, Map<String, Integer> items) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            items.merge(StackMapper.itemId(stack), stack.getCount(), Integer::sum);
        }
    }

    private static BlockPos centerOf(List<SpiritEntity> members, Optional<Point> home) {
        if (home.isPresent()) {
            Point point = home.get();
            return new BlockPos((int) Math.floor(point.x()), (int) Math.floor(point.y()),
                    (int) Math.floor(point.z()));
        }
        for (SpiritEntity spirit : members) {
            if (!spirit.isRemoved()) {
                return spirit.blockPosition();
            }
        }
        return null;
    }
}
