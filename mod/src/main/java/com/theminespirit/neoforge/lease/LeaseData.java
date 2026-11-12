package com.theminespirit.neoforge.lease;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.lease.Lease;
import com.theminespirit.core.board.lease.LeaseBook;
import com.theminespirit.core.board.lease.LeaseCodec;
import com.theminespirit.core.board.lease.LeaseKind;
import com.theminespirit.core.board.lease.LeaseStatus;
import com.theminespirit.core.board.lease.LeaseUndoLimit;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class LeaseData extends SavedData {
    public static final String NAME = ModIdentity.MOD_ID + "_leases";

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, Integer> undoFailures = new HashMap<>();
    private static final String LEASES = "Leases";
    private static final String MIGRATED = "Migrated";
    private static final String ID = "Id";
    private static final String SPIRIT = "Spirit";
    private static final String TICKET = "Ticket";
    private static final String PURPOSE = "Purpose";
    private static final String STATUS = "Status";
    private static final String SINCE = "Since";
    private static final String CELLS = "Cells";
    private static final String X = "X";
    private static final String Y = "Y";
    private static final String Z = "Z";
    private static final String KIND = "Kind";
    private static final String BEFORE = "Before";
    private static final String AFTER = "After";

    private static final SavedData.Factory<LeaseData> FACTORY =
            new SavedData.Factory<>(LeaseData::new, LeaseData::load, null);

    private final LeaseBook book = new LeaseBook();
    private final Map<String, Long> suspendedSince = new HashMap<>();
    private boolean migrated;

    public LeaseData() {}

    public static LeaseData get(ServerLevel level) {
        Objects.requireNonNull(level, "Уровень не может быть null: книга аренд хранится в мире");
        LeaseData data = level.getDataStorage().computeIfAbsent(FACTORY, NAME);
        if (!data.migrated) {
            PlacedBlocksData placed = PlacedBlocksData.get(level);
            data.migrateLegacy(placed.temporaryCells(), level.getGameTime());
            placed.clearLegacy();
        }
        return data;
    }

    public LeaseBook book() {
        return book;
    }

    public Map<String, Long> suspendedSince() {
        return suspendedSince;
    }

    public boolean migrated() {
        return migrated;
    }

    public int migrateLegacy(List<BlockCoord> cells, long since) {
        Objects.requireNonNull(cells, "Клетки старых временных меток не могут быть null: мигрировать нечего");
        if (migrated) {
            return 0;
        }
        int count = 0;
        if (!cells.isEmpty()) {
            book.legacy(cells, since);
            count = cells.size();
        }
        migrated = true;
        setDirty();
        return count;
    }

    public boolean undoFailed(String id, int maxFailures, String reason) {
        int failures = undoFailures.merge(id, 1, Integer::sum);
        if (!LeaseUndoLimit.exhausted(failures, maxFailures)
                || book.find(id)
                        .filter(lease -> lease.status() == LeaseStatus.ORPHANED)
                        .isEmpty()) {
            return false;
        }
        book.abandon(id);
        undoFailures.remove(id);
        setDirty();
        LOGGER.info(
                "[lease] аренда {} снята после {} неудачных откатов, клетки отданы расчистке: {}",
                id,
                failures,
                reason);
        return true;
    }

    public int orphanSpirit(UUID spirit) {
        int count = book.orphanSpirit(spirit);
        if (count > 0) {
            setDirty();
        }
        return count;
    }

    public int orphanAllActive() {
        int count = book.orphanAllActive();
        if (count > 0) {
            setDirty();
        }
        return count;
    }

    public boolean isActive(BlockPos pos) {
        return isActive(toCoord(pos));
    }

    public boolean isActive(BlockCoord pos) {
        return book.isActive(pos);
    }

    public boolean isOrphaned(BlockPos pos) {
        return isOrphaned(toCoord(pos));
    }

    public boolean isOrphaned(BlockCoord pos) {
        return book.isOrphaned(pos);
    }

    public void changed() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(MIGRATED, migrated);
        ListTag leases = new ListTag();
        for (LeaseCodec.LeaseRow row : LeaseCodec.encode(book)) {
            leases.add(writeLease(row));
        }
        tag.put(LEASES, leases);
        return tag;
    }

    public static LeaseData load(CompoundTag tag, HolderLookup.Provider registries) {
        LeaseData data = new LeaseData();
        data.migrated = tag.getBoolean(MIGRATED);
        ListTag leases = tag.getList(LEASES, Tag.TAG_COMPOUND);
        for (int index = 0; index < leases.size(); index++) {
            data.book.restore(readLease(leases.getCompound(index)));
        }
        return data;
    }

    private static CompoundTag writeLease(LeaseCodec.LeaseRow row) {
        CompoundTag lease = new CompoundTag();
        lease.putString(ID, row.id());
        if (row.spirit() != null) {
            lease.putString(SPIRIT, row.spirit().toString());
        }
        if (row.ticket() != null) {
            lease.putString(TICKET, row.ticket());
        }
        lease.putString(PURPOSE, row.purpose());
        lease.putString(STATUS, row.status().name());
        lease.putLong(SINCE, row.since());
        ListTag cells = new ListTag();
        for (LeaseCodec.CellRow cell : row.cells()) {
            cells.add(writeCell(cell));
        }
        lease.put(CELLS, cells);
        return lease;
    }

    private static CompoundTag writeCell(LeaseCodec.CellRow cell) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(X, cell.x());
        tag.putInt(Y, cell.y());
        tag.putInt(Z, cell.z());
        tag.putString(KIND, cell.kind().name());
        if (cell.before() != null) {
            tag.putString(BEFORE, cell.before());
        }
        if (cell.after() != null) {
            tag.putString(AFTER, cell.after());
        }
        return tag;
    }

    private static Lease readLease(CompoundTag lease) {
        String id = lease.getString(ID);
        List<LeaseCodec.CellRow> cells = new ArrayList<>();
        ListTag stored = lease.getList(CELLS, Tag.TAG_COMPOUND);
        for (int index = 0; index < stored.size(); index++) {
            cells.add(readCell(stored.getCompound(index)));
        }
        return new LeaseCodec.LeaseRow(
                        id,
                        optionalUuid(lease.getString(SPIRIT)),
                        lease.contains(TICKET) ? lease.getString(TICKET) : null,
                        lease.getString(PURPOSE),
                        status(lease.getString(STATUS)),
                        lease.getLong(SINCE),
                        cells)
                .toLease();
    }

    private static LeaseCodec.CellRow readCell(CompoundTag cell) {
        return new LeaseCodec.CellRow(
                cell.getInt(X),
                cell.getInt(Y),
                cell.getInt(Z),
                kind(cell.getString(KIND)),
                cell.contains(BEFORE) ? cell.getString(BEFORE) : null,
                cell.contains(AFTER) ? cell.getString(AFTER) : null);
    }

    private static @Nullable UUID optionalUuid(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException broken) {
            throw new IllegalStateException(
                    "В сохранении аренды записан неверный uuid духа '" + text
                            + "': владельца не восстановить, проверьте theminespirit_leases.dat",
                    broken);
        }
    }

    private static LeaseStatus status(String text) {
        try {
            return LeaseStatus.valueOf(text);
        } catch (IllegalArgumentException broken) {
            throw new IllegalStateException(
                    "В сохранении аренды записан неизвестный статус '" + text
                            + "': скан не поймёт, трогать ли клетки, проверьте theminespirit_leases.dat",
                    broken);
        }
    }

    private static LeaseKind kind(String text) {
        try {
            return LeaseKind.valueOf(text);
        } catch (IllegalArgumentException broken) {
            throw new IllegalStateException(
                    "В сохранении аренды записан неизвестный вид клетки '" + text
                            + "': откат не поймёт, что делать с блоком, проверьте theminespirit_leases.dat",
                    broken);
        }
    }

    private static BlockCoord toCoord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
