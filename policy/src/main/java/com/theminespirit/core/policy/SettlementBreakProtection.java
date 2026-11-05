package com.theminespirit.core.policy;

import java.util.Objects;

public final class SettlementBreakProtection {
    private SettlementBreakProtection() {}

    public static BlockProtection.Decision own(BlockProtection protection, String blockId, boolean inProtectedZone) {
        Objects.requireNonNull(protection, "Защита блоков не может быть null: без неё не проверить свой блок");
        Objects.requireNonNull(blockId, "Id блока не может быть null: иначе неизвестно, что ломают");
        return protection.checkOwn(blockId, inProtectedZone, true);
    }
}
