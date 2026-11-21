package com.theminespirit.neoforge.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.lease.Lease;
import com.theminespirit.core.board.lease.LeaseCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
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

        CompoundTag saved = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        LeaseData loaded = LeaseData.load(saved, null);

        assertEquals(
                data.book().all(),
                loaded.book().all(),
                "Аренды обязаны пережить перезапуск клетка в клетку: иначе брошенные подмости и мосты исчезнут "
                        + "из скана, а действующие потеряют владельца; в NBT "
                        + loaded.book().all()
                        + " При смене saveLoad_AllCellKindsAndNullOwners_SurviveRestart проверь это утверждение.");
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
                        + "а не выдуманный дух; получено " + restored.spirit() + " / " + restored.ticket()
                        + " Иначе тест load_MissingOptionalFields_ReadsOlderRecord перестанет ловить эту поломку. При смене load_MissingOptionalFields_ReadsOlderRecord проверь это утверждение.");
        assertTrue(
                restored.cells().get(0).before() == null
                        && restored.cells().get(0).after() == null,
                "Старая клетка без прежнего и нового блока читается без подстановки: иначе откат сочтёт, что "
                        + "помнит состояние, и поставит воздух; получено "
                        + restored.cells().get(0)
                        + " При смене load_MissingOptionalFields_ReadsOlderRecord проверь это утверждение.");
    }

    @Test
    void load_UnknownStatus_NamesFileAndValue() {
        CompoundTag saved = roundTripTag();
        firstLease(saved).putString("Status", "TELEPORTED");

        IllegalStateException refusal = assertThrows(
                IllegalStateException.class,
                () -> LeaseData.load(saved, null),
                "Неизвестный статус аренды обязан останавливать загрузку, а не подставлять ACTIVE"
                        + " Иначе тест load_UnknownStatus_NamesFileAndValue перестанет ловить эту поломку. При смене load_UnknownStatus_NamesFileAndValue проверь это утверждение.");
        assertTrue(
                refusal.getMessage().contains("TELEPORTED")
                        && refusal.getMessage().contains("theminespirit_leases.dat"),
                "Неизвестный статус аренды обязан назвать значение и файл сохранения: иначе скан не поймёт, "
                        + "трогать ли клетки, а игрок не найдёт, что править; получено: " + refusal.getMessage()
                        + " При смене load_UnknownStatus_NamesFileAndValue проверь это утверждение.");
    }

    @Test
    void load_BrokenSpiritUuid_NamesFileAndValue() {
        CompoundTag saved = roundTripTag();
        firstLease(saved).putString("Spirit", "не-uuid");

        IllegalStateException refusal = assertThrows(
                IllegalStateException.class,
                () -> LeaseData.load(saved, null),
                "Неверный uuid владельца обязан останавливать загрузку, а не терять аренду молча"
                        + " Иначе тест load_BrokenSpiritUuid_NamesFileAndValue перестанет ловить эту поломку. При смене load_BrokenSpiritUuid_NamesFileAndValue проверь это утверждение.");
        assertTrue(
                refusal.getMessage().contains("не-uuid") && refusal.getMessage().contains("theminespirit_leases.dat"),
                "Неверный uuid владельца обязан назвать значение и файл: иначе владельца не восстановить, а "
                        + "сообщение не подскажет, где чинить; получено: " + refusal.getMessage()
                        + " При смене load_BrokenSpiritUuid_NamesFileAndValue проверь это утверждение.");
    }

    @Test
    void load_UnknownCellKind_NamesFileAndValue() {
        CompoundTag saved = roundTripTag();
        firstCell(firstLease(saved)).putString("Kind", "FLOATING");

        IllegalStateException refusal = assertThrows(
                IllegalStateException.class,
                () -> LeaseData.load(saved, null),
                "Неизвестный вид клетки обязан останавливать загрузку: откат без него не знает, что делать"
                        + " Иначе тест load_UnknownCellKind_NamesFileAndValue перестанет ловить эту поломку. При смене load_UnknownCellKind_NamesFileAndValue проверь это утверждение.");
        assertTrue(
                refusal.getMessage().contains("FLOATING")
                        && refusal.getMessage().contains("theminespirit_leases.dat"),
                "Неизвестный вид правки клетки обязан назвать значение и файл: откат не поймёт, что делать с "
                        + "блоком; получено: " + refusal.getMessage()
                        + " Иначе тест load_UnknownCellKind_NamesFileAndValue перестанет ловить эту поломку. При смене load_UnknownCellKind_NamesFileAndValue проверь это утверждение.");
    }

    private static CompoundTag roundTripTag() {
        LeaseData data = new LeaseData();
        Lease lease = data.book().open(SPIRIT, "ticket", "scaffold", 10);
        data.book().record(lease.id(), LeaseCell.placed(new BlockCoord(0, 64, 0), "minecraft:dirt"));
        return data.save(new CompoundTag(), RegistryAccess.EMPTY);
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
