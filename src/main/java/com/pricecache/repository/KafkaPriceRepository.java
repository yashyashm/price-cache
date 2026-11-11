package com.pricecache.repository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.pricecache.domain.Price;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dummy {@link PriceRepository} that stands in for publishing accepted quotes to
 * a Kafka topic. It is intended to run as a <em>secondary</em> behind
 * {@link CompositePriceRepository}: writes are "published" (here, just logged)
 * and reads are not served from Kafka, so the query methods return empty lists.
 *
 * <p>Replace the method bodies with a real {@code KafkaTemplate} producer when a
 * broker is available; the composite and service layers need no changes.
 */
public class KafkaPriceRepository implements PriceRepository {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaPriceRepository.class);

    @Override
    public boolean save(Price price) {
        LOG.info("Publishing price to Kafka: vendor={} instrument={} bid={} ask={} currency={} observedAt={}",
                price.vendor(), price.instrument(), price.bid(), price.ask(),
                price.currency(), price.observedAt());
        return true;
    }

    @Override
    public List<Price> findByVendor(String vendor, Instant asOf, Instant oldestAllowed) {
        return Collections.emptyList();
    }

    @Override
    public List<Price> findByInstrument(String instrument, Instant asOf, Instant oldestAllowed) {
        return Collections.emptyList();
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        LOG.debug("Kafka retention is handled by the topic's retention policy; cutoff={} ignored", cutoff);
        return 0;
    }
}
