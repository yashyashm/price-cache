package com.pricecache.service;

import com.pricecache.domain.Price;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Business rules for caching vendor quotes and serving the two lookup shapes.
 * The default implementation is {@link PriceServiceImpl}.
 */
public interface PriceService {
    /**
     * Validates and stores a quote, re-distributing it to matching subscribers
     * when the stored value changed. Rejects future quotes and quotes older than
     * the retention window.
     */
    Price publish(Price price);

    /**
     * One price per instrument for the given vendor. Returns the latest price
     * when {@code asOf} is empty, otherwise the as-of price at that instant.
     */
    List<Price> priceByVendor(String vendor, Optional<Instant> asOf);

    /**
     * One price per vendor for the given instrument. Returns the latest price
     * when {@code asOf} is empty, otherwise the as-of price at that instant.
     */
    List<Price> priceByInstrument(String instrument, Optional<Instant> asOf);

    /** Physically removes observations older than the retention window. */
    int removeExpired();
}