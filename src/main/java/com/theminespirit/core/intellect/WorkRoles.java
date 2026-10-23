package com.theminespirit.core.intellect;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;

/**
 * Кто годен для работы поселения. Свободный дух без плана получает роль {@link SpiritRole#DEFENDER}
 * ({@code SpiritRoles.classify}) — это ярлык простоя для координатора обороны; пока рядом нет угроз, такой дух
 * обязан получать работу, иначе ему годен только отдых и он стоит в {@code wait} (живой прогон 21.10).
 */
public final class WorkRoles {
    private WorkRoles() {}

    // Строитель стройку не прерывает; защитник при угрозе в поселении держит пост.
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
