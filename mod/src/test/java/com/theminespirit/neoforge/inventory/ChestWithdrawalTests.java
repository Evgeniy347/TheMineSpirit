package com.theminespirit.neoforge.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.town.ColumnMask;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class ChestWithdrawalTests {
    private static final ColumnMask TOWN = ColumnMask.rect(-512, -512, 1023, 1023);

    @Test
    void inside_BothInSettlement_AllowsAnyDistance() {
        assertTrue(
                ChestWithdrawal.inside(TOWN, new BlockPos(-120, 64, -460), new BlockPos(100, 120, 100)),
                "Дух и сундук внутри поселения разрешают получение на любом расстоянии: иначе удалённые "
                        + "запасы снова недоступны. При смене inside_BothInSettlement_AllowsAnyDistance проверь "
                        + "WithdrawScript и живой дальний сундук.");
    }

    @Test
    void inside_SpiritOutsideSettlement_RejectsRemoteWithdrawal() {
        assertFalse(
                ChestWithdrawal.inside(TOWN, new BlockPos(2000, 64, 2000), new BlockPos(0, 64, 0)),
                "Снаружи поселения дистанционный забор запрещён: иначе дух в экспедиции вынимает предметы "
                        + "из дома через всю карту. При смене inside_SpiritOutsideSettlement_RejectsRemoteWithdrawal "
                        + "проверь границу TownPlan.region.");
    }

    @Test
    void inside_ChestOutsideSettlement_RejectsRemoteWithdrawal() {
        assertFalse(
                ChestWithdrawal.inside(TOWN, new BlockPos(0, 64, 0), new BlockPos(2000, 64, 2000)),
                "Сундук снаружи поселения не попадает в дистанционный доступ: иначе правило J распространится "
                        + "на дикие сундуки. При смене inside_ChestOutsideSettlement_RejectsRemoteWithdrawal "
                        + "проверь StockReader.containerPositions.");
    }

    @Test
    void remoteTransfer_OnlyWithdrawBypassesApproach_DepositKeepsPhysicalAccess() throws IOException {
        String withdraw =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/inventory/WithdrawScript.java"));
        String deposit =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/inventory/DepositScript.java"));
        String task =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/inventory/ContainerTask.java"));

        assertTrue(
                withdraw.contains("ChestWithdrawal.allowed(spirit, pos)"),
                "Только withdraw разрешает дистанционный перенос: иначе получение из сундука снова требует "
                        + "пути. При смене remoteTransfer_OnlyWithdrawBypassesApproach_DepositKeepsPhysicalAccess проверь WithdrawScript.");
        assertFalse(
                deposit.contains("canTransferRemotely"),
                "Deposit не обходит подход: иначе выгрузка станет запрещённой дистанционной операцией. При "
                        + "смене remoteTransfer_OnlyWithdrawBypassesApproach_DepositKeepsPhysicalAccess проверь DepositScript и ActivityPlans.moveItem.");
        assertTrue(
                task.contains("protected boolean canTransferRemotely(BlockPos pos)") && task.contains("return false;"),
                "Базовый контейнерный скрипт остаётся физическим по умолчанию: иначе новый тип задания случайно "
                        + "получит удалённый доступ. При смене remoteTransfer_OnlyWithdrawBypassesApproach_DepositKeepsPhysicalAccess "
                        + "проверь ContainerTask.");
    }
}
