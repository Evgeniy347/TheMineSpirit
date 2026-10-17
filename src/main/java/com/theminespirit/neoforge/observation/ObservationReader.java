package com.theminespirit.neoforge.observation;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.BoardTaskRef;
import com.theminespirit.core.observation.ContainerContents;
import com.theminespirit.core.observation.ContainerRef;
import com.theminespirit.core.observation.GroundItem;
import com.theminespirit.core.observation.HeldItem;
import com.theminespirit.core.observation.ItemCount;
import com.theminespirit.core.observation.ItemCounts;
import com.theminespirit.core.observation.ObservationLimits;
import com.theminespirit.core.observation.SpiritStatus;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.observation.WorldTime;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.WorldScanner;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Достраивает наблюдение духа до полного: предмет в руке, здоровье и сытость, задание доски, выпавшие
 * предметы, содержимое ближайших контейнеров и время суток. Мир читает только здесь — базовое наблюдение
 * (блоки, сущности, контейнеры, инвентарь) собирает {@link WorldScanner}.
 */
public final class ObservationReader {
    private ObservationReader() {
    }

    public static WorldObservation read(SpiritEntity spirit, WorldObservation base) {
        ObservationLimits limits = ObservationLimits.loadDefault();
        Level level = spirit.level();
        return new WorldObservation(base.botId(), base.tick(), base.position(), base.inventory(), base.blocks(),
                base.entities(), base.containers(), base.radius(), status(spirit),
                groundItems(level, base.position(), base.radius(), limits.maxGroundItems()),
                containerContents(level, base.containers(), limits),
                new WorldTime(level.getDayTime(), level.isDay()));
    }

    private static SpiritStatus status(SpiritEntity spirit) {
        return SpiritStatus.of(held(spirit.getMainHandItem()), spirit.getHealth(), spirit.getMaxHealth(),
                spirit.hunger().satietyFraction(), boardTask(spirit));
    }

    private static HeldItem held(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Integer durability = stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : null;
        return new HeldItem(StackMapper.itemId(stack), durability);
    }

    private static BoardTaskRef boardTask(SpiritEntity spirit) {
        if (!(spirit.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Optional<BoardService> service = BoardRegistry.peek(serverLevel);
        if (service.isEmpty()) {
            return null;
        }
        Board board = service.get().board();
        List<BoardTicket> held = board.assignedTo(spirit.getUUID());
        if (held.isEmpty()) {
            return null;
        }
        BoardTicket ticket = held.get(0);
        String status = board.statusOf(ticket.id()).map(Enum::name).orElse("PENDING");
        return new BoardTaskRef(ticket.boardCase().name(), status);
    }

    private static List<GroundItem> groundItems(Level level, BlockCoord center, int radius, int cap) {
        AABB bounds = new AABB(center.x() - radius, center.y() - (double) radius, center.z() - radius,
                center.x() + radius + 1.0, center.y() + radius + 1.0, center.z() + radius + 1.0);
        Map<String, Integer> counts = new TreeMap<>();
        for (ItemEntity dropped : level.getEntitiesOfClass(ItemEntity.class, bounds, entity -> !entity.isRemoved())) {
            ItemStack stack = dropped.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            counts.merge(StackMapper.itemId(stack), stack.getCount(), Integer::sum);
        }
        List<GroundItem> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (result.size() >= cap) {
                break;
            }
            result.add(new GroundItem(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static List<ContainerContents> containerContents(Level level, List<ContainerRef> containers,
            ObservationLimits limits) {
        List<ContainerContents> result = new ArrayList<>();
        for (ContainerRef ref : containers) {
            if (result.size() >= limits.maxContainers()) {
                break;
            }
            BlockPos pos = new BlockPos(ref.pos().x(), ref.pos().y(), ref.pos().z());
            if (ContainerAccess.open(level, pos) instanceof ContainerAccess.Opened opened) {
                List<ItemCount> items = ItemCounts.aggregate(WorldScanner.inventoryEntries(opened.container()),
                        "containerItems");
                result.add(new ContainerContents(ref.pos(), capItems(items, limits.maxContainerItems())));
            }
        }
        return result;
    }

    private static List<ItemCount> capItems(List<ItemCount> items, int cap) {
        return items.size() <= cap ? items : List.copyOf(items.subList(0, cap));
    }
}
