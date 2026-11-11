package com.pricecache.service;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.pricecache.domain.Price;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class InMemoryPriceSubscriptions implements PriceSubscriptions {
    private static final Logger LOG = LoggerFactory.getLogger(InMemoryPriceSubscriptions.class);

    private final ConcurrentMap<String, Set<Subscription>> vendorSubscribers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<Subscription>> instrumentSubscribers = new ConcurrentHashMap<>();
    private final Executor executor;
    private final Duration slowConsumerTimeout;

    private final int maxConsecutiveFailures;

    public InMemoryPriceSubscriptions(Executor executor, Duration slowConsumerTimeout, int maxConsecutiveFailures) {
        this.executor = Objects.requireNonNull(executor, "executor is required");
        this.slowConsumerTimeout = Objects.requireNonNull(slowConsumerTimeout, "slowConsumerTimeout is required");
        if (slowConsumerTimeout.isZero() || slowConsumerTimeout.isNegative()) {
            throw new IllegalArgumentException("slowConsumerTimeout must be positive");
        }
        if (maxConsecutiveFailures <= 0) {
            throw new IllegalArgumentException("maxConsecutiveFailures must be positive");
        }
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    @Override
    public AutoCloseable forVendor(String vendor, Consumer<Price> consumer) {
        String id = Price.requireIdentifier(vendor, "vendor");
        return subscribe(vendorSubscribers, id, consumer);
    }

    @Override
    public AutoCloseable forInstrument(String instrument, Consumer<Price> consumer) {
        String id = Price.requireIdentifier(instrument, "instrument");
        return subscribe(instrumentSubscribers, id, consumer);
    }

    private AutoCloseable subscribe(ConcurrentMap<String, Set<Subscription>> map, String key,
                                    Consumer<Price> consumer) {
        Objects.requireNonNull(consumer, "consumer is required");
        Subscription subscription = new Subscription(map, key, consumer);
        map.compute(key, (String k, Set<Subscription> existing) -> {
            Set<Subscription> subscribers = existing != null ? existing : ConcurrentHashMap.newKeySet();
            subscribers.add(subscription);
            return subscribers;
        });
        return subscription::unsubscribe;
    }

    @Override
    public void distribute(Price price) {
        dispatchAll(vendorSubscribers.get(price.vendor()), price);
        dispatchAll(instrumentSubscribers.get(price.instrument()), price);
    }

    private void dispatchAll(Collection<Subscription> matches, Price price) {
        if (matches == null) {
            return;
        }
        for (Subscription subscription : matches) {
            dispatch(subscription, price);
        }
    }

    private void dispatch(Subscription subscription, Price price) {
        CompletableFuture
                .runAsync(() -> subscription.deliver(price), executor)
                .orTimeout(slowConsumerTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((Void ignored, Throwable throwable) -> onDeliveryComplete(subscription, price, throwable));
    }

    private void onDeliveryComplete(Subscription subscription, Price price, Throwable throwable) {
        if (throwable == null) {
            subscription.onDeliverySucceeded();
            return;
        }
        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause() : throwable;
        if (cause instanceof TimeoutException) {
            LOG.warn("Downstream consumer not responding within {} for vendor {} instrument {}:"
                            + " continuing to distribute to other subscribers",
                    slowConsumerTimeout, price.vendor(), price.instrument());
        } else {
            LOG.error("Downstream delivery failed for vendor {} instrument {}; quote remains stored",
                    price.vendor(), price.instrument(), cause);
        }
        int failures = subscription.onDeliveryFailed();
        if (failures >= maxConsecutiveFailures) {
            subscription.unsubscribe();
            LOG.warn("Evicted unresponsive subscriber for vendor {} instrument {} after {} consecutive failures",
                    price.vendor(), price.instrument(), failures);
        }
    }

    private static final class Subscription {
        private final ConcurrentMap<String, Set<Subscription>> map;
        private final String key;
        private final Consumer<Price> consumer;
        private final AtomicInteger consecutiveFailures = new AtomicInteger();

        Subscription(ConcurrentMap<String, Set<Subscription>> map, String key, Consumer<Price> consumer) {
            this.map = map;
            this.key = key;
            this.consumer = consumer;
        }

        void deliver(Price price) {
            consumer.accept(price);
        }

        void onDeliverySucceeded() {
            consecutiveFailures.set(0);
        }

        int onDeliveryFailed() {
            return consecutiveFailures.incrementAndGet();
        }

        void unsubscribe() {
            map.computeIfPresent(key, (String k, Set<Subscription> subscribers) -> {
                subscribers.remove(this);
                return subscribers.isEmpty() ? null : subscribers;
            });
        }
    }
}