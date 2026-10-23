package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * Память поселения об оставленной руде (решение владельца 22.10): руда на удачу без кирки с Удачей остаётся в стене
 * шахты, её место записано здесь, чтобы вернуться с нужной киркой. Хранится с миром, а не с духом: дух может
 * погибнуть, а руда остаётся.
 */
public final class MineOreLedger extends SavedData {
    public static final int MAX_ENTRIES = 1024;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = ModIdentity.MOD_ID + "_left_ores";
    private static final String ENTRIES = "Entries";
    private static final String BLOCK = "Block";
    private static final SavedData.Factory<MineOreLedger> FACTORY =
            new SavedData.Factory<>(MineOreLedger::new, MineOreLedger::load);

    // Оставленная руда: id блока и стоянка в шахте, откуда её видел строитель.
    public record Left(String block, BlockCoord stand) {}

    private final Map<BlockCoord, Left> ores = new LinkedHashMap<>();

    private MineOreLedger() {}

    public static MineOreLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void remember(BlockCoord pos, String blockId, BlockCoord stand) {
        Objects.requireNonNull(pos, "Позиция руды не может быть null: без неё к руде не вернуться");
        Objects.requireNonNull(blockId, "Id руды не может быть null: без него неизвестно, за чем возвращаться");
        Left left = new Left(blockId, stand);
        if (left.equals(ores.get(pos))) {
            return;
        }
        if (!ores.containsKey(pos) && ores.size() >= MAX_ENTRIES) {
            Map.Entry<BlockCoord, Left> oldest = ores.entrySet().iterator().next();
            LOGGER.warn(
                    "Память оставленной руды полна ({}): забыта самая старая {} {}",
                    MAX_ENTRIES,
                    oldest.getKey(),
                    oldest.getValue());
            ores.remove(oldest.getKey());
        }
        ores.put(pos, left);
        setDirty();
    }

    public void forget(BlockCoord pos) {
        if (ores.remove(pos) != null) {
            setDirty();
        }
    }

    public Map<BlockCoord, Left> ores() {
        return Map.copyOf(ores);
    }

    private static MineOreLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        MineOreLedger ledger = new MineOreLedger();
        ListTag entries = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ledger.ores.put(
                    new BlockCoord(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")),
                    new Left(
                            entry.getString(BLOCK),
                            new BlockCoord(entry.getInt("SX"), entry.getInt("SY"), entry.getInt("SZ"))));
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<BlockCoord, Left> ore : ores.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", ore.getKey().x());
            entry.putInt("Y", ore.getKey().y());
            entry.putInt("Z", ore.getKey().z());
            entry.putString(BLOCK, ore.getValue().block());
            entry.putInt("SX", ore.getValue().stand().x());
            entry.putInt("SY", ore.getValue().stand().y());
            entry.putInt("SZ", ore.getValue().stand().z());
            entries.add(entry);
        }
        tag.put(ENTRIES, entries);
        return tag;
    }
}
