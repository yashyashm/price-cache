package com.pricecache.web;

import com.pricecache.domain.Price;
import com.pricecache.service.PriceSubscriptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;


/**
 * Streams live price updates to remote downstream systems using Server-Sent
 * Events (SSE). A client opens a long-lived HTTP connection; every matching
 * quote is pushed as an event until the client disconnects, at which point the
 * in-process subscription is closed via its {@link AutoCloseable} handle.
 */
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionController.class);

    private final PriceSubscriptions subscriptions;
    private final long streamTimeoutMs;

    public SubscriptionController(PriceSubscriptions subscriptions,
                                  @Value("${prices.distribution.stream-timeout-ms:1800000}") long streamTimeoutMs) {
        this.subscriptions = subscriptions;
        this.streamTimeoutMs = streamTimeoutMs;
    }

    @GetMapping(value = "/vendors/{vendor}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamByVendor(@PathVariable String vendor) {
        return stream(consumer -> subscriptions.forVendor(vendor, consumer));
    }

    @GetMapping(value = "/instruments/{instrument}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamByInstrument(@PathVariable String instrument) {
        return stream(consumer -> subscriptions.forInstrument(instrument, consumer));
    }

    private SseEmitter stream(Function<Consumer<Price>, AutoCloseable> register) {
        SseEmitter emitter = new SseEmitter(streamTimeoutMs);
        AutoCloseable handle = register.apply(price -> send(emitter, price));
        emitter.onCompletion(() -> unsubscribe(handle));
        emitter.onTimeout(() -> {
            emitter.complete();
            unsubscribe(handle);
        });
        emitter.onError(throwable -> unsubscribe(handle));
        return emitter;
    }

    private void send(SseEmitter emitter, Price price) {
        try {
            emitter.send(SseEmitter.event().name("price").data(price, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException exception) {
            // Client has gone away; completing with the error triggers onError -> unsubscribe,
            // and rethrowing warns this delivery as failed for the eviction policy.
            emitter.completeWithError(exception);
            throw new IllegalStateException("SSE client disconnected", exception);
        }
    }

    private void unsubscribe(AutoCloseable handle) {
        try {
            handle.close();
        } catch (Exception exception) {
            LOG.warn("Failed to close price subscription", exception);
        }
    }
}