package com.pricecache.repository;

import java.math.BigDecimal;
import java.time.Instant;

import com.pricecache.domain.Price;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaPriceRepositoryTest {
    private final KafkaPriceRepository repository = new KafkaPriceRepository();

    @Test
    void savePublishesAndReportsAccepted() {
        Price quote = new Price("V1", "AAPL", new BigDecimal("100.00"), new BigDecimal("100.10"), "USD", Instant.now());
        assertTrue(repository.save(quote));
    }

    @Test
    void readsAreNotServedFromKafka() {
        assertTrue(repository.findByVendor("V1", Instant.now(), Instant.EPOCH).isEmpty());
        assertTrue(repository.findByInstrument("AAPL", Instant.now(), Instant.EPOCH).isEmpty());
    }

    @Test
    void retentionIsDelegatedToTheTopicAndReportsNothingRemoved() {
        assertEquals(0, repository.deleteOlderThan(Instant.now()));
    }
}