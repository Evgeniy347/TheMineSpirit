package com.theminespirit.core.mining;

import java.util.Objects;
import java.util.Set;

/**
 * Камень и булыжник поселение добывает только в шахте (решение владельца 21.10 и 23.10): дух поселения не берёт их с
 * поверхности — ни с открытых склонов, ни ямами и колодцами у дома. Блок под открытым небом — поверхность; под
 * землёй — шахта. Дух без поселения (сцена, команда игрока) копает где велено.
 */
public final class StoneSource {
    public static final String REFUSAL = "камень и булыжник поселение добывает только в шахте: под открытым небом "
            + "их не копают (сначала шахта по схеме)";
    private static final Set<String> STONE = Set.of("stone", "cobblestone");

    private StoneSource() {}

    public static boolean allowed(String blockId, boolean openToSky, boolean settled) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: по нему решается, камень ли это");
        return !(settled && openToSky && STONE.contains(path(blockId)));
    }

    private static String path(String blockId) {
        int colon = blockId.indexOf(':');
        return colon < 0 ? blockId : blockId.substring(colon + 1);
    }
}
