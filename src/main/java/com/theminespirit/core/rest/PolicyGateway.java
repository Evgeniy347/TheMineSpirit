package com.theminespirit.core.rest;

import com.theminespirit.core.policy.UnprotectArea;
import java.util.List;

/**
 * Платформенная граница разрешений на снятие защиты построек игрока: список, выдача и снятие.
 * Ядро REST не знает про миры и SavedData — адаптер хранит разрешения в данных мира.
 */
public interface PolicyGateway {

    List<UnprotectArea> areas();

    UnprotectArea grant(UnprotectRequest request);

    UnprotectArea revoke(String id);
}
