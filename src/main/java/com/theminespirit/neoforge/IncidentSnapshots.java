package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.incident.IncidentNote;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

/**
 * Снимок инцидента для повтора в тесте: окрестность духа ±{@link IncidentNote#RADIUS} — структурой NBT в
 * {@code <мир>/theminespirit/incidents/<время>-<дух>.nbt}, рядом в том же файле (ключ {@link #META}) — дух целиком
 * (инвентарь, задача, дом), что случилось и задание. Снимок одного духа — не чаще раза в {@link #MIN_GAP_TICKS}:
 * повторный провал того же шага не засыпает мир файлами.
 */
public final class IncidentSnapshots {
    public static final String META = "theminespirit_incident";
    public static final long MIN_GAP_TICKS = 1200;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private IncidentSnapshots() {}

    // Запись инцидента: строка для лога (дух, позиция, задача, инвентарь, путь снимка) и снимок окрестности в мир.
    public static String record(ServerLevel level, SpiritEntity spirit, String what) {
        IncidentNote note = note(spirit, what);
        long now = level.getGameTime();
        Long last = LAST.get(spirit.getUUID());
        if (last != null && now - last < MIN_GAP_TICKS && now >= last) {
            return note.line() + "; снимок окрестности — прошлый, моложе " + MIN_GAP_TICKS + " тиков";
        }
        LAST.put(spirit.getUUID(), now);
        Path file = level.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve(IncidentNote.FOLDER)
                .resolve(IncidentNote.fileName(Instant.now(), spirit.displayName()));
        try {
            write(level, note, spirit, file);
        } catch (UncheckedIOException failure) {
            LOGGER.error("[incident] снимок {} не записан: {}", file, failure.getMessage());
            return note.line() + "; снимок не записан: " + failure.getMessage();
        }
        return note.line() + "; снимок " + file.normalize();
    }

    public static IncidentNote note(SpiritEntity spirit, String what) {
        BlockPos feet = spirit.blockPosition();
        return new IncidentNote(
                spirit.displayName(),
                what,
                new BlockCoord(feet.getX(), feet.getY(), feet.getZ()),
                task(spirit),
                inventory(spirit));
    }

    // Снимок окрестности духа в файл без предела частоты: запись инцидента и проверка загрузки в тесте.
    public static void write(ServerLevel level, IncidentNote note, SpiritEntity spirit, Path file) {
        StructureTemplate template = new StructureTemplate();
        BlockCoord origin = note.origin();
        template.fillFromWorld(
                level,
                new BlockPos(origin.x(), origin.y(), origin.z()),
                new Vec3i(IncidentNote.SIZE, IncidentNote.SIZE, IncidentNote.SIZE),
                false,
                null);
        CompoundTag tag = template.save(new CompoundTag());
        CompoundTag meta = new CompoundTag();
        meta.putString("spirit", note.spirit());
        meta.putString("what", note.what());
        meta.putString("task", note.task());
        meta.putIntArray(
                "at", new int[] {note.at().x(), note.at().y(), note.at().z()});
        BlockCoord offset = IncidentNote.spiritOffset();
        meta.putIntArray("offset", new int[] {offset.x(), offset.y(), offset.z()});
        CompoundTag entity = new CompoundTag();
        spirit.saveWithoutId(entity);
        meta.put("entity", entity);
        tag.put(META, meta);
        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(tag, file);
        } catch (IOException failure) {
            throw new UncheckedIOException("снимок инцидента " + file, failure);
        }
    }

    // Снимок инцидента из файла: структура для постановки в мир теста и метаданные духа.
    public static Loaded load(ServerLevel level, Path file) {
        CompoundTag tag;
        try {
            tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException failure) {
            throw new UncheckedIOException("снимок инцидента " + file + " не прочитан", failure);
        }
        StructureTemplate template = new StructureTemplate();
        template.load(level.holderLookup(Registries.BLOCK), tag);
        return new Loaded(template, tag.getCompound(META));
    }

    /** Снимок, прочитанный из файла: структура окрестности и метаданные инцидента. */
    public record Loaded(StructureTemplate template, CompoundTag meta) {
        public Optional<String> spirit() {
            return meta.contains("spirit") ? Optional.of(meta.getString("spirit")) : Optional.empty();
        }
    }

    private static String task(SpiritEntity spirit) {
        String script = spirit.currentTaskScript().orElse("-");
        return script
                + spirit.taskProgress().map(progress -> " " + progress).orElse("")
                + spirit.taskFailedAt().map(failed -> ", провал на " + failed).orElse("");
    }

    private static Map<String, Integer> inventory(SpiritEntity spirit) {
        Map<String, Integer> items = new HashMap<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(), Integer::sum);
            }
        }
        return items;
    }
}
