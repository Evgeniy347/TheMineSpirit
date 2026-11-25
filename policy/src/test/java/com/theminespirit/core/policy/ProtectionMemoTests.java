package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class ProtectionMemoTests {
    @Test
    void certify_SameStamp_StaysKnown() {
        ProtectionMemo memo = new ProtectionMemo();
        BlockCoord cell = new BlockCoord(1, 64, 2);
        memo.refresh(7);
        memo.certify(cell);
        memo.refresh(7);

        assertTrue(
                memo.certified(cell),
                "Клетка без защиты не проверяется повторно, пока ключ памятки тот же (окно времени и поколение "
                        + "плана): иначе проверка защиты снова идёт по всей площади расчистки каждый такт и держит "
                        + "серверный тик. При смене certify_SameStamp_StaysKnown проверь ClearBlocked.protectionStamp "
                        + "и ClearSweepRun.pick");
    }

    @Test
    void refresh_NewStamp_ForgetsKnownCells() {
        ProtectionMemo memo = new ProtectionMemo();
        BlockCoord cell = new BlockCoord(1, 64, 2);
        memo.refresh(7);
        memo.certify(cell);
        memo.refresh(8);

        assertFalse(
                memo.certified(cell),
                "Смена ключа памятки сбрасывает проверенные клетки: без этого новый план или истёкшее разрешение "
                        + "не защитили бы клетку, которую дух уже считает свободной, и он снёс бы чужую постройку. "
                        + "При смене refresh_NewStamp_ForgetsKnownCells проверь StageSite.protection и "
                        + "PlotCellStructure.generation");
    }

    @Test
    void certify_LargeSweep_KeepsCertificatesBetweenPicks() {
        ProtectionMemo memo = new ProtectionMemo();
        memo.refresh(11);
        for (int index = 0; index < 50_000; index++) {
            memo.certify(new BlockCoord(index, 64, index % 97));
        }
        memo.refresh(11);
        boolean all = true;
        for (int index = 0; index < 50_000; index++) {
            all &= memo.certified(new BlockCoord(index, 64, index % 97));
        }

        assertTrue(
                all,
                "Памятка большой сессии расчистки обязана пережить следующий такт при том же ключе: иначе "
                        + "проверка защиты повторяет проход по сотням тысяч клеток и сервер замирает на секунды. "
                        + "При смене certify_LargeSweep_KeepsCertificatesBetweenPicks проверь ClearBlocked."
                        + "dropProtected и размер ключа памятки");
    }

    @Test
    void certify_OtherCell_NotKnown() {
        ProtectionMemo memo = new ProtectionMemo();
        memo.refresh(7);
        memo.certify(new BlockCoord(1, 64, 2));

        assertFalse(
                memo.certified(new BlockCoord(5, 64, 6)),
                "Памятка хранит только проверенные клетки: соседняя клетка проверяется заново и может быть защищена"
                        + " Иначе тест certify_OtherCell_NotKnown перестанет ловить эту поломку. При смене certify_OtherCell_NotKnown проверь это утверждение.");
    }
}
