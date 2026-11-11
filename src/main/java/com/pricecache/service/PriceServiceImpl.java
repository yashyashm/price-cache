package com.pricecache.service;

import com.pricecache.domain.Price;
import com.pricecache.repository.PriceRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PriceServiceImpl implements PriceService {
    private final PriceRepository repository;
    private final PriceSubscriptions subscriptions;
    private final Clock clock;
    private final Duration retention;

    public PriceServiceImpl(PriceRepository repository, PriceSubscriptions subscriptions, Clock clock,
                            Duration retention) {
        this.repository = Objects.requireNonNull(repository);
        this.subscriptions = Objects.requireNonNull(subscriptions);
        this.clock = Objects.requireNonNull(clock);
        this.retention = Objects.requireNonNull(retention, "retention is required");
        if (retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("retention must be positive");
        }
    }

    @Override
    public Price publish(Price price) {
        Objects.requireNonNull(price, "price is required");
        Instant now = clock.instant();
        if (price.observedAt().isAfter(now)) {
            throw new IllegalArgumentException("observedAt must not be in the future");
        }
        if (price.observedAt().isBefore(now.minus(retention))) {
            throw new IllegalArgumentException("Price is older than the retention window of " + retention.toDays() + " days");
        }
        if (repository.save(price)) {
            subscriptions.distribute(price);
        }
        return price;
    }

    @Override
    public List<Price> priceByVendor(String vendor, Optional<Instant> asOf) {
        Instant now = clock.instant();
        return repository.findByVendor(Price.requireIdentifier(vendor, "vendor"),
                effectiveAsOf(asOf, now), now.minus(retention));
    }

    @Override
    public List<Price> priceByInstrument(String instrument, Optional<Instant> asOf) {
        Instant now = clock.instant();
        return repository.findByInstrument(Price.requireIdentifier(instrument, "instrument"),
                effectiveAsOf(asOf, now), now.minus(retention));
    }

    @Override
    public int removeExpired() {
        return repository.deleteOlderThan(cutoff());
    }

    private static Instant effectiveAsOf(Optional<Instant> asOf, Instant now) {
        return Objects.requireNonNull(asOf, "asOf is required").orElse(now);
    }

    private Instant cutoff() {
        return clock.instant().minus(retention);
    }
}
