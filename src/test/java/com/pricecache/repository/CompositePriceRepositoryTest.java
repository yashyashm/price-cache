package com.pricecache.repository;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.pricecache.domain.Price;
import org.junit.jupiter.api.Test;

class CompositePriceRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final Instant WINDOW_START = NOW.minus(Duration.ofDays(30));

    @Test
    void saveFansOutToEveryBackingStore() {
        InMemoryPriceRepository primary = new InMemoryPriceRepository();
        InMemoryPriceRepository secondary = new InMemoryPriceRepository();
        CompositePriceRepository composite = new CompositePriceRepository(primary, List.of(secondary));

        Price quote = quote("V1", "AAPL", NOW);
        assertTrue(composite.save(quote));
        assertEquals(List.of(quote), primary.findByInstrument("AAPL", NOW, WINDOW_START));
        assertEquals(List.of(quote), secondary.findByInstrument("AAPL", NOW, WINDOW_START));
    }

    @Test
    void readsComeOnlyFromThePrimaryStore() {
        InMemoryPriceRepository primary = new InMemoryPriceRepository();
        InMemoryPriceRepository secondary = new InMemoryPriceRepository();
        CompositePriceRepository composite = new CompositePriceRepository(primary, List.of(secondary));

        secondary.save(quote("V1", "AAPL", NOW));
        assertTrue(composite.findByInstrument("AAPL", NOW, WINDOW_START).isEmpty());
        assertTrue(composite.findByVendor("V1", NOW, WINDOW_START).isEmpty());
    }

    @Test
    void failingSecondaryIsIsolatedFromThePrimary() {
        InMemoryPriceRepository primary = new InMemoryPriceRepository();
        PriceRepository failing = new FailingRepository();
        CompositePriceRepository composite = new CompositePriceRepository(primary, List.of(failing));

        Price quote = quote("V1", "AAPL", NOW);
        assertTrue(composite.save(quote));
        assertEquals(List.of(quote), primary.findByInstrument("AAPL", NOW, WINDOW_START));
        assertDoesNotThrow(() -> composite.deleteOlderThan(NOW));
    }

    @Test
    void deleteReturnsThePrimaryCountAndEvictsEveryStore() {
        InMemoryPriceRepository primary = new InMemoryPriceRepository();
        InMemoryPriceRepository secondary = new InMemoryPriceRepository();
        CompositePriceRepository composite = new CompositePriceRepository(primary, List.of(secondary));

        composite.save(quote("V1", "AAPL", NOW.minus(Duration.ofDays(40))));
        composite.save(quote("V2", "AAPL", NOW.minus(Duration.ofDays(5))));
        assertEquals(2, composite.deleteOlderThan(NOW));
        assertTrue(primary.findByInstrument("AAPL", NOW, WINDOW_START).isEmpty());
        assertTrue(secondary.findByInstrument("AAPL", NOW, WINDOW_START).isEmpty());
    }

    private static Price quote(String vendor, String instrument, Instant time) {
        BigDecimal value = new BigDecimal("100.00");
        return new Price(vendor, instrument, value, value, "USD", time);
    }

    private static final class FailingRepository implements PriceRepository {
        @Override
        public boolean save(Price price) {
            throw new IllegalStateException("secondary store unavailable");
        }

        @Override
        public List<Price> findByVendor(String vendor, Instant asOf, Instant oldestAllowed) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Price> findByInstrument(String instrument, Instant asOf, Instant oldestAllowed) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteOlderThan(Instant cutoff) {
            throw new IllegalStateException("secondary store unavailable");
        }
    }
}