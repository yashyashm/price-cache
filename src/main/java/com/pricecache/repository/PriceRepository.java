package com.pricecache.repository;

import com.pricecache.domain.Price;

import java.time.Instant;
import java.util.List;

public interface PriceRepository {
    /**
     * Stores an observation in the per-pair time series. Inserts a new point or
     * overwrites the existing point at the same observation time (last write
     * wins). Returns true when the stored value changed (new point or a
     * different value/currency at the same time) so callers can re-distribute,
     * and false for an identical retry (same key, time, value and currency).
     */
    boolean save(Price price);

    /**
     * For each matching pair, the price as of {@code asOf} (most recent
     * observation at or before {@code asOf}), restricted to observations no
     * older than {@code oldestAllowed}. Passing the current time as
     * {@code asOf} yields the latest price per pair.
     */
    List<Price> findByVendor(String vendor, Instant asOf, Instant oldestAllowed);

    List<Price> findByInstrument(String instrument, Instant asOf, Instant oldestAllowed);

    int deleteOlderThan(Instant cutoff);
}