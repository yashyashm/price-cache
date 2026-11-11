package com.pricecache.domain;


import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriceTest {
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final BigDecimal BID = new BigDecimal("100.00");
    private static final BigDecimal ASK = new BigDecimal("100.10");

    @Test
    void trimsIdentifiers() {
        Price price = new Price("  V1  ", "  AAPL  ", BID, ASK, "USD", NOW);
        assertEquals("V1", price.vendor());
        assertEquals("AAPL", price.instrument());
    }

    @Test
    void currencyWhitespaceIsTrimmedBeforeValidation() {
        Price price = new Price("V1", "AAPL", BID, ASK, "  USD  ", NOW);
        assertEquals("USD", price.currency());
    }

    @Test
    void keyExposesVendorInstrumentPair() {
        Price price = new Price("V1", "AAPL", BID, ASK, "USD", NOW);
        assertEquals(new PriceKey("V1", "AAPL"), price.key());
    }

    @Test
    void blankIdentifiersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Price(" ", "AAPL", BID, ASK, "USD", NOW));
        assertThrows(IllegalArgumentException.class, () -> new Price("V1", "   ", BID, ASK, "USD", NOW));
        assertThrows(IllegalArgumentException.class, () -> new Price(null, "AAPL", BID, ASK, "USD", NOW));
    }

    @Test
    void unknownCurrencyCodeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Price("V1", "AAPL", BID, ASK, "INVALID", NOW));
    }

    @Test
    void nullFieldsAndTimestampAreRejected() {
        assertThrows(NullPointerException.class, () -> new Price("V1", "AAPL", null, ASK, "USD", NOW));
        assertThrows(NullPointerException.class, () -> new Price("V1", "AAPL", BID, null, "USD", NOW));
        assertThrows(NullPointerException.class, () -> new Price("V1", "AAPL", BID, ASK, "USD", null));
    }

    @Test
    void requireIdentifierTrimsAndRejectsBlanks() {
        assertEquals("abc", Price.requireIdentifier("  abc  ", "field"));
        assertThrows(IllegalArgumentException.class, () -> Price.requireIdentifier(" ", "field"));
        assertThrows(IllegalArgumentException.class, () -> Price.requireIdentifier(null, "field"));
    }
}