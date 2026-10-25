package com.theminespirit.core.intellect;

import com.theminespirit.core.coordinator.SpiritSnapshot;

public final class WorkRoles {
    private WorkRoles() {}

    public static boolean canWork(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Пригодность к работе без снимка духа или поселения не проверяется");
        }
        return switch (spirit.role()) {
            case WORKER, IDLE -> true;
            case DEFENDER -> view.threats() == 0 && spirit.threatsNear() == 0 && !spirit.attacked();
            case BUILDING -> false;
        };
    }
}
