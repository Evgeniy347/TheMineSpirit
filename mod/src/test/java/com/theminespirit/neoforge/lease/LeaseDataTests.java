package com.theminespirit.neoforge.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.lease.Lease;
import com.theminespirit.core.board.lease.LeaseCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class LeaseDataTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void saveLoad_AllCellKindsAndNullOwners_SurviveRestart() {
        LeaseData data = new LeaseData();
        Lease active = data.book().open(SPIRIT, "ticket-restart", "scaffold", 10);
        data.book()
                .record(
                        active.id(),
                        List.of(
                                LeaseCell.placed(new BlockCoord(0, 64, 0), "minecraft:dirt"),
                                LeaseCell.placed(new BlockCoord(1, 64, 0), "minecraft:stone"),
                                LeaseCell.broken(new BlockCoord(2, 64, 0), "minecraft:oak_planks"),
                                LeaseCell.state(new BlockCoord(3, 64, 0), "open", "closed")));
        Lease orphan = data.book().open(null, null, "bridge", 20);
        data.book().record(orphan.id(), LeaseCell.state(new BlockCoord(4, 64, 0), "lit", "unlit"));
        data.book().orphan(orphan.id());
        data.migrateLegacy(List.of(new BlockCoord(5, 64, 0)), 30);

        CompoundTag saved = data.save(new CompoundTag(), null);
        LeaseData loaded = LeaseData.load(saved, null);

        assertEquals(
                data.book().all(),
                loaded.book().all(),
                "Аренды обязаны пережить перезапуск клетка в клетку: иначе брошенные подмости и мосты исчезнут "
                        + "из скана, а действующие потеряют владельца; в NBT "
                        + loaded.book().all());
        assertTrue(
                loaded.migrated(),
                "Признак миграции старых T-меток обязан пережить перезапуск: иначе они мигрируют снова и "
                        + "перезапишут аренду legacy");
    }

    @Test
    void load_MissingOptionalFields_ReadsOlderRecord() {
        CompoundTag saved = roundTripTag();
        CompoundTag lease = firstLease(saved);
        lease.remove("Spirit");
        lease.remove("Ticket");
        CompoundTag cell = firstCell(lease);
        cell.remove("Before");
        cell.remove("After");

        LeaseData loaded = LeaseData.load(saved, null);

        Lease restored = loaded.book().all().get(0);
        assertTrue(
                restored.spirit() == null && restored.ticket() == null,
                "Старая запись без владельца и тикета читается как брошенная аренда: пустые поля — это null, "
                        + "а не выдуманный дух; получено " + restored.spirit() + " / " + restored.ticket());
        assertTrue(
                restored.cells().get(0).before() == null
                        && restored.cells().get(0).after() == null,
                "Старая клетка без прежнего и нового блока читается без подстановки: иначе откат сочтёт, что "
                        + "помнит состояние, и поставит воздух; получено "
                        + restored.cells().get(0));
    }

    @Test
    void load_UnknownStatus_NamesFileAndValue() {
        CompoundTag saved = roundTripTag();
        firstLease(saved).putString("Status", "TELEPORTED");

        IllegalStateException refusal = assertThrows(
                IllegalStateException.class,
                () -> LeaseData.load(saved, null),
                "Неизвестный статус аренды обязан останавливать загрузку, а не подставлять ACTIVE");
        assertTrue(
                refusal.getMessage().contains("TELEPORTED")
                        && refusal.getMessage().contains("theminespirit_leases.dat"),
                "Неизвестный статус аренды обязан назвать значение и файл сохранения: иначе скан не поймёт, "
                        + "трогать ли клетки, а игрок не найдёт, что править; получено: " + refusal.getMessage());
    }

    @Test
    void load_BrokenSpiritUuid_NamesFileAndValue() {
        CompoundTag saved = roundTripTag();
        firstLease(saved).putString("Spirit", "не-uuid");

        IllegalStateException refusal = assertThrows(
                IllegalStateException.class,
                () -> LeaseData.load(saved, null),
                "Неверный uuid владельца обязан останавливать загрузку, а не терять аренду молча");
        assertTrue(
                refusal.getMessage().contains("не-uuid") && refusal.getMessage().contains("theminespirit_leases.dat"),
                "Неверный uuid владельца обязан назвать значение и файл: иначе владельца не восстановить, а "
                        + "сообщение не подскажет, где чинить; получено: " + refusal.getMessage());
    }

    @Test
    void load_UnknownCellKind_NamesFileAndValue() {
        CompoundTag saved = roundTripTag();
        firstCell(firstLease(saved)).putString("Kind", "FLOATING");

        IllegalStateException refusal = assertThrows(
                IllegalStateException.class,
                () -> LeaseData.load(saved, null),
                "Неизвестный вид клетки обязан останавливать загрузку: откат без него не знает, что делать");
        assertTrue(
                refusal.getMessage().contains("FLOATING")
                        && refusal.getMessage().contains("theminespirit_leases.dat"),
                "Неизвестный вид правки клетки обязан назвать значение и файл: откат не поймёт, что делать с "
                        + "блоком; получено: " + refusal.getMessage());
    }

    private static CompoundTag roundTripTag() {
        LeaseData data = new LeaseData();
        Lease lease = data.book().open(SPIRIT, "ticket", "scaffold", 10);
        data.book().record(lease.id(), LeaseCell.placed(new BlockCoord(0, 64, 0), "minecraft:dirt"));
        return data.save(new CompoundTag(), null);
    }

    private static CompoundTag firstLease(CompoundTag saved) {
        ListTag leases = saved.getList("Leases", Tag.TAG_COMPOUND);
        return leases.getCompound(0);
    }

    private static CompoundTag firstCell(CompoundTag lease) {
        ListTag cells = lease.getList("Cells", Tag.TAG_COMPOUND);
        return cells.getCompound(0);
    }
}
