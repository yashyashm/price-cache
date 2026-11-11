package com.pricecache.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record Price(
        @NotBlank String vendor,
        @NotBlank String instrument,
        @NotNull BigDecimal bid,
        @NotNull BigDecimal ask,
        @NotBlank String currency,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) Instant observedAt) {

    public Price {
        vendor = requireIdentifier(vendor, "vendor");
        instrument = requireIdentifier(instrument, "instrument");
        Objects.requireNonNull(bid, "bid is required");
        Objects.requireNonNull(ask, "ask is required");
        currency = Currency.getInstance(requireIdentifier(currency, "currency")).getCurrencyCode();
        Objects.requireNonNull(observedAt, "observedAt is required");
    }

    public static String requireIdentifier(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public PriceKey key() { return new PriceKey(vendor, instrument); }
}