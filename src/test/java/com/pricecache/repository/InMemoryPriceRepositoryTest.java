package com.pricecache.repository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.pricecache.domain.Price;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryPriceRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final Instant WINDOW_START = NOW.minus(Duration.ofDays(30));
    private InMemoryPriceRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPriceRepository();
    }

    @Test
    void saveIsIdempotentForIdenticalValueAtSameObservationTime() {
        assertTrue(repository.save(quote("V1", "AAPL", "100.00", "100.10", NOW)));
        assertFalse(repository.save(quote("V1", "AAPL", "100.000", "100.100", NOW)),
                "same numeric value and currency at the same instant is not a change");
    }

    @Test
    void changedValueOrCurrencyAtSameTimeOverwritesAndCountsAsChange() {
        repository.save(quote("V1", "AAPL", "100.00", "100.10", NOW));
        assertTrue(repository.save(quote("V1", "AAPL", "101.00", "101.10", NOW)));
        assertEquals(new BigDecimal("101.00"), repository.findByInstrument("AAPL", NOW, WINDOW_START).get(0).bid());

        Price otherCurrency = new Price("V1", "AAPL", new BigDecimal("101.00"), new BigDecimal("101.10"), "EUR", NOW);
        assertTrue(repository.save(otherCurrency));
    }

    @Test
    void asOfReturnsNearestAtOrBeforeAndRespectsWindow() {
        Price older = quote("V1", "AAPL", "100.00", "100.10", NOW.minusSeconds(120));
        Price newer = quote("V1", "AAPL", "110.00", "110.10", NOW.minusSeconds(30));
        repository.save(older);
        repository.save(newer);

        assertEquals(List.of(newer), repository.findByInstrument("AAPL", NOW, WINDOW_START));
        assertEquals(List.of(older), repository.findByInstrument("AAPL", NOW.minusSeconds(60), WINDOW_START));
        assertTrue(repository.findByInstrument("AAPL", NOW.minusSeconds(200), WINDOW_START).isEmpty());
    }

    @Test
    void entriesOlderThanWindowAreExcludedFromReads() {
        Price expired = quote("V1", "AAPL", "100.00", "100.10", WINDOW_START.minusNanos(1));
        repository.save(expired);
        assertTrue(repository.findByVendor("V1", NOW, WINDOW_START).isEmpty());
        assertTrue(repository.findByInstrument("AAPL", NOW, WINDOW_START).isEmpty());
    }

    @Test
    void resultListsAreSortedByVendorThenInstrumentAndImmutable() {
        repository.save(quote("V2", "AAPL", "1", "2", NOW));
        repository.save(quote("V1", "MSFT", "1", "2", NOW));
        repository.save(quote("V1", "AAPL", "1", "2", NOW));

        List<Price> byInstrument = repository.findByInstrument("AAPL", NOW, WINDOW_START);
        assertEquals(List.of("V1", "V2"), List.of(byInstrument.get(0).vendor(), byInstrument.get(1).vendor()));

        List<Price> byVendor = repository.findByVendor("V1", NOW, WINDOW_START);
        assertEquals(List.of("AAPL", "MSFT"), List.of(byVendor.get(0).instrument(), byVendor.get(1).instrument()));
        assertThrows(UnsupportedOperationException.class, () -> byVendor.clear());
    }

    @Test
    void deleteOlderThanRemovesStrictlyOlderKeepsBoundaryAndReturnsCount() {
        repository.save(quote("V1", "AAPL", "1", "2", NOW.minus(Duration.ofDays(40))));
        repository.save(quote("V2", "AAPL", "1", "2", NOW.minus(Duration.ofDays(31))));
        Price boundary = quote("V3", "AAPL", "1", "2", WINDOW_START);
        repository.save(boundary);
        repository.save(quote("V4", "AAPL", "1", "2", NOW));

        assertEquals(2, repository.deleteOlderThan(WINDOW_START));
        assertEquals(0, repository.deleteOlderThan(WINDOW_START));
    }

    private static Price quote(String vendor, String instrument, String bid, String ask, Instant time) {
        return new Price(vendor, instrument, new BigDecimal(bid), new BigDecimal(ask), "USD", time);
    }
}