package com.pricecache.repository;

import com.pricecache.domain.Price;
import com.pricecache.domain.PriceKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class InMemoryPriceRepository implements PriceRepository {
    private static final int BY_VENDOR = 0;
    private static final int BY_INSTRUMENT = 1;

    private final Map<PriceKey, NavigableMap<Instant, Price>> history = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public boolean save(Price price) {
        lock.writeLock().lock();
        try {
            NavigableMap<Instant, Price> series = history.computeIfAbsent(price.key(), k -> new TreeMap<>());
            Price existing = series.get(price.observedAt());
            if (existing != null
                    && price.bid().compareTo(existing.bid()) == 0
                    && price.ask().compareTo(existing.ask()) == 0
                    && price.currency().equals(existing.currency())) {
                return false;
            }
            series.put(price.observedAt(), price);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Price> findByVendor(String vendor, Instant asOfTime, Instant oldestAllowed) {
        return findAsOf(BY_VENDOR, vendor, asOfTime, oldestAllowed);
    }

    @Override
    public List<Price> findByInstrument(String instrument, Instant asOf, Instant oldestAllowed) {
        return findAsOf(BY_INSTRUMENT, instrument, asOf, oldestAllowed);
    }

    private List<Price> findAsOf(int switchField, String id, Instant asOfTime, Instant oldestAllowed) {
        List<Price> matches = new ArrayList<>();
        lock.readLock().lock();
        try {
            for (Map.Entry<PriceKey, NavigableMap<Instant, Price>> entry : history.entrySet()) {
                PriceKey key = entry.getKey();
                String field = switchField == BY_VENDOR ? key.vendor() : key.instrument();
                if (!field.equals(id)) {
                    continue;
                }
                Map.Entry<Instant, Price> point = entry.getValue().floorEntry(asOfTime);
                if (point != null && !point.getKey().isBefore(oldestAllowed)) {
                    matches.add(point.getValue());
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        matches.sort(InMemoryPriceRepository::compareByVendorThenInstrument);
        return Collections.unmodifiableList(matches);
    }

    private static int compareByVendorThenInstrument(Price leftPrice, Price rightPrice) {
        int byVendor = leftPrice.vendor().compareTo(rightPrice.vendor());
        return byVendor != 0 ? byVendor : leftPrice.instrument().compareTo(rightPrice.instrument());
    }

    @Override
    public int deleteOlderThan(Instant cutoffTime) {
        int removed = 0;
        lock.writeLock().lock();
        try {
            Iterator<NavigableMap<Instant, Price>> iterator = history.values().iterator();
            while (iterator.hasNext()) {
                NavigableMap<Instant, Price> series = iterator.next();
                NavigableMap<Instant, Price> expired = series.headMap(cutoffTime, false);
                removed += expired.size();
                expired.clear();
                if (series.isEmpty()) {
                    iterator.remove();
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
        return removed;
    }
}
