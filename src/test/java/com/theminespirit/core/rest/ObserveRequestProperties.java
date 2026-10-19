package com.theminespirit.core.rest;

import java.nio.charset.StandardCharsets;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ObserveRequestProperties {
    @Provide
    Arbitrary<String> jsonLikeText() {
        return Arbitraries.strings()
                .withChars("{}[]\":,. -0123456789truefalsn")
                .withChars("radiusfilterinclude_entities*")
                .ofMaxLength(80);
    }

    @Property
    void parse_AnyBytes_ThrowsNothingButApiException(@ForAll byte[] body) {
        parseOrApiException(body);
    }

    @Property
    void parse_JsonLikeText_ThrowsNothingButApiException(@ForAll("jsonLikeText") String text) {
        parseOrApiException(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void parseOrApiException(byte[] body) {
        try {
            ObserveRequest.parse(body);
        } catch (ApiException expected) {
            if (expected.error().code() != ErrorCode.INVALID_REQUEST) {
                throw new AssertionError(
                        "Разбор тела observe обязан отвечать только invalid_request, а не "
                                + expected.error().code() + ": любое другое исключение превратится в 500 для клиента",
                        expected);
            }
        }
    }
}
