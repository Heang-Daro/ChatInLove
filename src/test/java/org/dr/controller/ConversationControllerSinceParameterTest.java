package org.dr.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code since} parameter of the chat list is sent by the clients as an
 * ISO-8601 timestamp with a zone. It has to be understood in every shape the
 * clients may send, and an unparsable value must never break the request.
 */
class ConversationControllerSinceParameterTest {

    @Test
    @DisplayName("An instant with a Z suffix is accepted (what the clients send)")
    void parsesInstantWithZulu() {
        LocalDateTime parsed = ConversationController.parseSince("2026-11-16T07:00:00.000Z");
        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 11, 16, 7, 0, 0));
    }

    @Test
    @DisplayName("An offset date time is converted to UTC")
    void parsesOffsetDateTime() {
        LocalDateTime parsed = ConversationController.parseSince("2026-11-16T09:30:00+02:00");
        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 11, 16, 7, 30, 0));
    }

    @Test
    @DisplayName("A local date time is accepted as UTC")
    void parsesLocalDateTime() {
        LocalDateTime parsed = ConversationController.parseSince("2026-11-16T07:00:00");
        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 11, 16, 7, 0, 0));
    }

    @Test
    @DisplayName("A missing or broken value disables the filter instead of failing")
    void ignoresBrokenValues() {
        assertThat(ConversationController.parseSince(null)).isNull();
        assertThat(ConversationController.parseSince("")).isNull();
        assertThat(ConversationController.parseSince("not-a-date")).isNull();
    }

    @Test
    @DisplayName("The parsed value is the UTC wall clock time")
    void isUtc() {
        LocalDateTime parsed = ConversationController.parseSince("2026-11-16T07:00:00.000Z");
        assertThat(parsed.atZone(ZoneOffset.UTC).toInstant())
                .isEqualTo(java.time.Instant.parse("2026-11-16T07:00:00.000Z"));
    }
}
