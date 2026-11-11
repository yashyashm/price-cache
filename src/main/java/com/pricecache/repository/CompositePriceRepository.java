package com.pricecache.repository;

import com.pricecache.domain.Price;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Fans writes out to several backing stores (for example a fast in-memory cache
 * plus a durable database) behind a single {@link PriceRepository}.
 *
 * <p>The first repository is the <em>primary</em>: it is authoritative, serves
 * all reads and provides the return value, so a failure there propagates. The
 * remaining repositories are best-effort <em>secondaries</em>: each write is
 * isolated so that one failing or slow store neither blocks the others nor the
 * primary; failures are logged, not propagated. Reads are never fanned out,
 * which keeps the low-latency query path on the fast primary store.
 */
public class CompositePriceRepository implements PriceRepository {
    private static final Logger LOG = LoggerFactory.getLogger(CompositePriceRepository.class);

    private final PriceRepository primary;
    private final List<PriceRepository> secondaries;

    public CompositePriceRepository(PriceRepository primary, List<PriceRepository> secondaries) {
        this.primary = Objects.requireNonNull(primary, "primary repository is required");
        this.secondaries = List.copyOf(Objects.requireNonNull(secondaries, "secondaries are required"));
    }

    @Override
    public boolean save(Price price) {
        boolean changed = primary.save(price);
        for (PriceRepository secondary : secondaries) {
            save(secondary, price);
        }
        return changed;
    }

    private void save(PriceRepository secondary, Price price) {
        try {
            secondary.save(price);
        } catch (RuntimeException exception) {
            LOG.error("Secondary repository {} failed to save price for vendor {} instrument {}",
                    secondary.getClass().getSimpleName(), price.vendor(), price.instrument(), exception);
        }
    }

    @Override
    public List<Price> findByVendor(String vendor, Instant asOf, Instant oldestAllowed) {
        return primary.findByVendor(vendor, asOf, oldestAllowed);
    }

    @Override
    public List<Price> findByInstrument(String instrument, Instant asOf, Instant oldestAllowed) {
        return primary.findByInstrument(instrument, asOf, oldestAllowed);
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        int removed = primary.deleteOlderThan(cutoff);
        for (PriceRepository secondary : secondaries) {
            deleteOlderThan(secondary, cutoff);
        }
        return removed;
    }

    private void deleteOlderThan(PriceRepository secondary, Instant cutoff) {
        try {
            secondary.deleteOlderThan(cutoff);
        } catch (RuntimeException exception) {
            LOG.error("Secondary repository {} failed to evict prices older than {}",
                    secondary.getClass().getSimpleName(), cutoff, exception);
        }
    }
}