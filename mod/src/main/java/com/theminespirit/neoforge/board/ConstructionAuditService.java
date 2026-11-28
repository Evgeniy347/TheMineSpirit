package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.ConstructionBatches;
import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.board.audit.DiffItem;
import com.theminespirit.core.board.audit.Group;
import com.theminespirit.core.board.audit.GroupRegistry;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class ConstructionAuditService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LIMIT_PER_BATCH = 2000;

    public record Batch(
            List<BlockPos> demolish, List<BlockPos> place, List<BlockPos> street, int index, boolean hasMore) {
        public Batch {
            demolish = List.copyOf(demolish);
            place = List.copyOf(place);
            street = List.copyOf(street);
        }
    }

    private ConstructionAuditService() {}

    public static Batch batch(ServerLevel level, BlockPos center, int index) {
        GroupRegistry registry = TownAuditWatch.peekGroups(level);
        if (registry == null) {
            LOGGER.debug("Аудит подсветки: группы ещё не собраны, пачка {}", index);
            return new Batch(List.of(), List.of(), List.of(), index, false);
        }
        List<Group> groups = registry.groups();
        Set<ChunkCell> included = new LinkedHashSet<>();
        for (Group group : groups) {
            for (DiffItem item : group.items()) {
                included.add(ConstructionBatches.at(item.cell().x(), item.cell().z()));
            }
        }
        List<ChunkCell> order = ConstructionBatches.order(included, center.getX() >> 4, center.getZ() >> 4);
        List<ChunkCell> chunks = ConstructionBatches.batch(order, index);
        if (chunks.isEmpty()) {
            return new Batch(List.of(), List.of(), List.of(), index, false);
        }
        Set<ChunkCell> selected = Set.copyOf(chunks);
        Set<BlockPos> demolish = new LinkedHashSet<>();
        Set<BlockPos> place = new LinkedHashSet<>();
        Set<BlockPos> street = new LinkedHashSet<>();
        for (Group group : groups) {
            boolean streetGroup = group.kind() == Group.Kind.STREET;
            for (DiffItem item : group.ordered()) {
                if (demolish.size() + place.size() + street.size() >= LIMIT_PER_BATCH) {
                    break;
                }
                BlockCoord cell = item.cell();
                if (!selected.contains(ConstructionBatches.at(cell.x(), cell.z()))) {
                    continue;
                }
                BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
                if (streetGroup) {
                    street.add(pos);
                } else if (item.action() == DiffItem.Action.BREAK) {
                    demolish.add(pos);
                } else {
                    place.add(pos);
                }
            }
        }
        return new Batch(
                List.copyOf(demolish),
                List.copyOf(place),
                List.copyOf(street),
                index,
                ConstructionBatches.hasMore(order, index));
    }
}
