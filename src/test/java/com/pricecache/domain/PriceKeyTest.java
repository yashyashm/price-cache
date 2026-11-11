package com.pricecache.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PriceKeyTest {
    @Test
    void exposesVendorAndInstrumentComponents() {
        PriceKey key = new PriceKey("V1", "AAPL");
        assertEquals("V1", key.vendor());
        assertEquals("AAPL", key.instrument());
    }

    @Test
    void valueEqualityIsByVendorAndInstrument() {
        assertEquals(new PriceKey("V1", "AAPL"), new PriceKey("V1", "AAPL"));
        assertEquals(new PriceKey("V1", "AAPL").hashCode(), new PriceKey("V1", "AAPL").hashCode());
        assertNotEquals(new PriceKey("V1", "AAPL"), new PriceKey("V1", "MSFT"));
        assertNotEquals(new PriceKey("V1", "AAPL"), new PriceKey("V2", "AAPL"));
    }
}