package com.theminespirit.neoforge.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class ConstructionAuditCodecTests {
    @Test
    void writeRead_ThreeFilledLists_KeepsEveryPosition() {
        ConstructionAuditChannel.Reply reply = new ConstructionAuditChannel.Reply(
                List.of(new BlockPos(-30, 64, 12), new BlockPos(-31, 64, 12)),
                List.of(new BlockPos(44, 64, -7)),
                List.of(new BlockPos(3, 64, 99), new BlockPos(4, 64, 99), new BlockPos(5, 64, 99)),
                2,
                true);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ConstructionAuditChannel.Reply.STREAM_CODEC.encode(buf, reply);
            ConstructionAuditChannel.Reply read = ConstructionAuditChannel.Reply.STREAM_CODEC.decode(buf);

            assertEquals(
                    reply.demolish(),
                    read.demolish(),
                    "Клетки на снос обязаны пережить пакет: иначе подсветка потеряет красные контуры, и игрок "
                            + "разберёт не те блоки, которые нашёл аудит поселения; при смене writeReply проверь, "
                            + "что первый список пишется до чтения номера пачки");
            assertEquals(
                    reply.place(),
                    read.place(),
                    "Клетки к постановке обязаны пережить пакет: иначе оранжевые контуры пропадут, и игрок решит, "
                            + "что недостроенная стена уже готова; при смене writeReply проверь, что порядок "
                            + "списков на проводе совпадает с порядком в записи");
            assertEquals(
                    reply.streetFlaws(),
                    read.streetFlaws(),
                    "Нарушения улиц обязаны пережить пакет отдельным списком: иначе серые рамки тротуара "
                            + "растворятся, а именно они и есть ответ на вопрос, где улица не совпадает со схемой; "
                            + "при смене readReply проверь, что третий список читается до номера пачки");
            assertEquals(
                    2,
                    read.batch(),
                    "Номер пачки обязан пережить пакет: иначе клиент запросит ту же пачку заново и круг аудита "
                            + "никогда не соберётся, подсветка останется неполной; при смене writeReply проверь, "
                            + "что varInt стоит после трёх списков координат");
            assertTrue(
                    read.hasMore(),
                    "Признак продолжения обязан пережить пакет: иначе клиент остановится на первой пачке и "
                            + "остаток поселения не подсветится вовсе; при смене writeReply проверь, что boolean "
                            + "пишется последним аргументом");
        } finally {
            buf.release();
        }
    }

    @Test
    void writeRead_EmptyStreetList_KeepsLengthOfOthers() {
        ConstructionAuditChannel.Reply reply = new ConstructionAuditChannel.Reply(
                List.of(new BlockPos(1, 64, 1)), List.of(new BlockPos(2, 64, 2)), List.of(), 0, false);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ConstructionAuditChannel.Reply.STREAM_CODEC.encode(buf, reply);
            ConstructionAuditChannel.Reply read = ConstructionAuditChannel.Reply.STREAM_CODEC.decode(buf);

            assertEquals(
                    List.of(),
                    read.streetFlaws(),
                    "Отсутствие нарушений улиц обязано приходить пустым списком, а не сдвигом байтов: иначе "
                            + "старый протокол разберёт номер пачки как координату и клиент упадёт на первом же "
                            + "кадре подсветки; при смене writePositions проверь, что пустой список пишет нулевую длину");
            assertEquals(
                    List.of(new BlockPos(1, 64, 1)),
                    read.demolish(),
                    "Пустой третий список не обязан сдвигать первые: иначе на поселениях без дефектов тротуара "
                            + "пропадёт подсветка зданий, и их останется чинить вслепую; при смене readReply "
                            + "проверь, что чтение идёт строго в порядке записи");
            assertFalse(
                    read.hasMore(),
                    "Признак конца круга обязан доходить корректно: иначе клиент будет вечно просить новую пачку "
                            + "и зря нагружает сервер аудитом каждый тик; при смене writeReply проверь, что false "
                            + "не превращается в true после трёх списков");
        } finally {
            buf.release();
        }
    }

    @Test
    void reply_NullStreetList_Throws() {
        assertThrows(
                NullPointerException.class,
                () -> new ConstructionAuditChannel.Reply(
                        List.of(new BlockPos(1, 64, 1)), List.of(new BlockPos(2, 64, 2)), null, 0, false),
                "Отсутствие списка улиц как null недопустимо: иначе клиентский приём пакета утонет в "
                        + "NullPointerException на пустом поселении вместо того чтобы показать чистые улицы; "
                        + "при смене конструктора Reply проверь, что третий набор проходит requireNonNull");
    }

    @Test
    void reply_NegativeBatch_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConstructionAuditChannel.Reply(List.of(), List.of(), List.of(), -1, false),
                "Отрицательный номер пачки не описывает круг аудита: иначе сервер ответит на запрос, которого "
                        + "клиент не слал, и накопление pending-наборов собьётся; при смене конструктора Reply "
                        + "проверь, что проверка batch осталась после копирования списков");
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConstructionAuditChannel.Request(new BlockPos(0, 64, 0), -1),
                "Запрос с отрицательной пачкой не имеет смысла на проводе: иначе сервер начнёт вычитать из "
                        + "нуля и уйдёт в бесконечный цикл за несуществующими хвостами списка; при смене "
                        + "конструктора Request проверь, что batch меньше нуля отвергается");
    }
}
