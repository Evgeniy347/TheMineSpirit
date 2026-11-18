package com.theminespirit.gametest;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.ChestDemolition;
import com.theminespirit.neoforge.board.ChestOpenScan;
import com.theminespirit.neoforge.board.GraphSource;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

final class ChestGraphScene {
    private ChestGraphScene() {}

    static void check(GameTestHelper helper, SpiritEntity spirit, BlockPos empty) {
        ServerLevel level = helper.getLevel();
        checkSurfaceLevel(helper, level, empty.getX() - 5, empty.getY() + 1, empty.getZ() - 5);
        var service = BoardRegistry.get(level);
        Set<String> before = service.board().all().stream().map(BoardTicket::id).collect(Collectors.toSet());
        Optional<BlockCoord> home = spirit.home();
        BlockPos full = empty.east(2);
        var original = level.getBlockState(full);
        BlockPos independentChest = empty.south(2);
        var independentOriginal = level.getBlockState(independentChest);
        level.setBlockAndUpdate(full, Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity) level.getBlockEntity(full)).setItem(0, new ItemStack(Items.DIAMOND, 5));
        spirit.setHome(coord(full.above()));
        GameTestFilter.setBoardEnabled(true);
        try {
            long now = level.getGameTime();
            service.submit(
                    new BoardTicket(
                            "warehouse_chest:ожидание",
                            BoardCase.STOCK_CHESTS,
                            Optional.empty(),
                            BoardRules.priorityOf(BoardCase.STOCK_CHESTS),
                            now,
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            Map.of()),
                    TicketTarget.at(coord(full.above())));
            GraphSource graph = new GraphSource();
            StockView stock = new StockView(Map.of("minecraft:oak_planks", 8));
            graph.tick(level, service, List.of(spirit), stock, Set.of(), now);
            graph.tick(level, service, List.of(spirit), stock, Set.of(), now);
            String id = "demolish_chest:" + empty.getX() + "," + empty.getY() + "," + empty.getZ();
            boolean issued = service.board().find(id).isPresent();
            helper.onEachTick(
                    () -> helper.assertTrue(
                            issued,
                            "Граф обязан выдать разбор пустого внепланового сундука: ближайший полный сундук и ожидание расширения склада не блокируют независимую работу"));
            helper.assertTrue(
                    level.getBlockState(full).is(Blocks.CHEST),
                    "Содержимое полного сундука сохраняется, пока нет места для переноса");
            service.cancel(level, id);
            ((ChestBlockEntity) level.getBlockEntity(full)).clearContent();
            GraphSource nearest = new GraphSource();
            nearest.tick(level, service, List.of(spirit), stock, Set.of(), now);
            nearest.tick(level, service, List.of(spirit), stock, Set.of(), now);
            String failed = "demolish_chest:" + full.getX() + "," + full.getY() + "," + full.getZ();
            helper.assertTrue(
                    service.board().claim(failed, spirit.getUUID(), now) == BoardOutcome.CLAIMED
                            && service.board().fail(failed),
                    "Отказ исполнителя оставляет ближайший сундук в ожидании повторной попытки, а не отменяет его защиту");
            level.setBlockAndUpdate(independentChest, Blocks.CHEST.defaultBlockState());
            GraphSource retry = new GraphSource();
            retry.tick(level, service, List.of(spirit), stock, Set.of(), now);
            retry.tick(level, service, List.of(spirit), stock, Set.of(), now);
            String next = "demolish_chest:" + independentChest.getX() + "," + independentChest.getY() + ","
                    + independentChest.getZ();
            boolean independent = service.board().statusOf(next).orElse(null) == BoardStatus.PENDING;
            helper.onEachTick(
                    () -> helper.assertTrue(
                            independent,
                            "Отказ ближайшего сундука не блокирует разбор следующего: граф обязан продолжить поиск после непринятого билета"));
            helper.assertTrue(
                    service.board().statusOf(failed).orElseThrow() == BoardStatus.FAILED,
                    "Независимая работа не снимает задержку повторного разбора первого сундука");
        } finally {
            GameTestFilter.setBoardEnabled(false);
            service.board().all().stream()
                    .map(BoardTicket::id)
                    .filter(id -> !before.contains(id))
                    .toList()
                    .forEach(id -> service.cancel(level, id));
            if (home.isPresent()) {
                spirit.setHome(home.orElseThrow());
            } else {
                spirit.clearHome();
            }
            ((ChestBlockEntity) level.getBlockEntity(full)).clearContent();
            level.setBlockAndUpdate(full, original);
            level.setBlockAndUpdate(independentChest, independentOriginal);
        }
    }

    private static void checkSurfaceLevel(GameTestHelper helper, ServerLevel level, int x, int y, int z) {
        PlacedBlocksData placed = PlacedBlocksData.get(level);
        level.setBlockAndUpdate(new BlockPos(x, y - 1, z), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.CHEST.defaultBlockState());
        placed.mark(new BlockPos(x, y, z));
        helper.assertTrue(
                !ChestDemolition.removable(level, new BlockCoord(x, y, z)),
                "Сундук духа на высоте поселения +1 стоит на земле: разбирать его нельзя");
        level.setBlockAndUpdate(new BlockPos(x, y + 2, z), Blocks.CHEST.defaultBlockState());
        placed.mark(new BlockPos(x, y + 2, z));
        helper.assertTrue(
                ChestDemolition.removable(level, new BlockCoord(x, y + 2, z)),
                "Сундук в воздухе выше высоты поселения +1 под снос, даже если его поставил дух");
        level.setBlockAndUpdate(new BlockPos(x, y + 1, z), Blocks.STONE.defaultBlockState());
        helper.assertTrue(
                ChestOpenScan.marks(level).stream()
                        .anyMatch(mark -> coord(new BlockPos(x, y + 1, z)).equals(mark.cell())),
                "Сундук на месте закрыт сверху — доступ к нему расчищают, иначе дух не откроет склад");
        level.setBlockAndUpdate(new BlockPos(x, y + 3, z), Blocks.STONE.defaultBlockState());
        helper.assertTrue(
                ChestOpenScan.marks(level).stream()
                        .noneMatch(mark -> coord(new BlockPos(x, y + 3, z)).equals(mark.cell())),
                "К висящему сундуку под снос доступ не расчищают: его разбирают целиком, а не ломают крышу над ним");
        level.setBlockAndUpdate(new BlockPos(x, y + 1, z), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(x, y + 3, z), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(x, y - 1, z), Blocks.CHEST.defaultBlockState());
        placed.mark(new BlockPos(x, y - 1, z));
        helper.assertTrue(
                ChestDemolition.removable(level, new BlockCoord(x, y - 1, z)),
                "Сундук ниже поверхности поселения под снос, даже если его поставил дух");
        for (int dy : new int[] {-1, 0, 2}) {
            placed.unmark(new BlockPos(x, y + dy, z));
            level.setBlockAndUpdate(new BlockPos(x, y + dy, z), Blocks.AIR.defaultBlockState());
        }
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
