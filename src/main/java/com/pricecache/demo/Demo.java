package com.pricecache.demo;

import com.pricecache.domain.Price;
import com.pricecache.repository.InMemoryPriceRepository;
import com.pricecache.repository.PriceRepository;
import com.pricecache.service.InMemoryPriceSubscriptions;
import com.pricecache.service.PriceService;
import com.pricecache.service.PriceServiceImpl;
import com.pricecache.service.PriceSubscriptions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

public class Demo {
    public static void main(String[] args) throws Exception {
        Instant now = Instant.parse("2026-11-09T12:00:00Z");
        Instant earlier = now.minus(Duration.ofDays(1));

        PriceRepository repository = new InMemoryPriceRepository();
        PriceSubscriptions subscriptions = new InMemoryPriceSubscriptions(Runnable::run, Duration.ofSeconds(5), 3);
        PriceService service = new PriceServiceImpl(repository, subscriptions, Clock.fixed(now, ZoneOffset.UTC), Duration.ofDays(30));

        try (AutoCloseable ignored = subscriptions.forInstrument("AAPL", price -> System.out.println("DOWNSTREAM AAPL: " + price))) {
            service.publish(quote("REUTERS", "AAPL", "220.00", earlier));
            service.publish(quote("REUTERS", "AAPL", "225.10", now));
            service.publish(quote("BLOOMBERG", "AAPL", "225.15", now));
            service.publish(quote("REUTERS", "MSFT", "420.50", now));

            System.out.println("LATEST REUTERS: " + service.priceByVendor("REUTERS", Optional.empty()));
            System.out.println("LATEST AAPL: " + service.priceByInstrument("AAPL", Optional.empty()));
            System.out.println("AAPL AS-OF " + earlier + ": " + service.priceByInstrument("AAPL", Optional.of(earlier)));
        }

        PriceService later = new PriceServiceImpl(repository, subscriptions, Clock.fixed(now.plus(Duration.ofDays(31)), ZoneOffset.UTC), Duration.ofDays(30));

        System.out.println("AFTER 31 DAYS, VISIBLE AAPL: " + later.priceByInstrument("AAPL", Optional.empty()));
        System.out.println("EXPIRED QUOTES REMOVED: " + later.removeExpired());
    }

    private static Price quote(String vendor, String instrument, String mid, Instant observedAt) {
        BigDecimal midValue = new BigDecimal(mid);
        BigDecimal spread = new BigDecimal("0.05");
        return new Price(vendor, instrument, midValue.subtract(spread), midValue.add(spread), "USD", observedAt);
    }
}