package com.pricecache.service;

import com.pricecache.domain.Price;

import java.util.function.Consumer;

/**
 * Registry of downstream consumers and in-process fan-out of accepted quotes.
 * The default implementation is {@link InMemoryPriceSubscriptions}.
 */
public interface PriceSubscriptions {
    /**
     * Subscribes to all quotes for the given vendor. Returns an AutoCloseable
     * that can be used to unsubscribe.
     */
    AutoCloseable forVendor(String vendor, Consumer<Price> consumer) ;

    /**
     * Subscribes to all quotes for the given instrument. Returns an AutoCloseable
     * that can be used to unsubscribe.
     */
    AutoCloseable forInstrument(String instrument, Consumer<Price> consumer) ;

    void distribute(Price price);
}
