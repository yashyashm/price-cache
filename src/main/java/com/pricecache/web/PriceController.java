package com.pricecache.web;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import com.pricecache.domain.Price;
import com.pricecache.service.PriceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/prices")
public class PriceController {
    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @PostMapping
    public Price publish(@Valid @RequestBody Price price) {
        return priceService.publish(price);
    }

    @GetMapping("/vendors/{vendor}")
    public List<Price> byVendor(@PathVariable String vendor, @RequestParam(required = false) String asOf) {
        return priceService.priceByVendor(vendor, parseAsOf(asOf));
    }

    @GetMapping("/instruments/{instrument}")
    public List<Price> byInstrument(@PathVariable String instrument, @RequestParam(required = false) String asOf) {
        return priceService.priceByInstrument(instrument, parseAsOf(asOf));
    }

    private static Optional<Instant> parseAsOf(String asOf) {
        if (asOf == null || asOf.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(asOf));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "asOf must be an ISO 8601 instant, e.g. 2026-11-09T12:00:00Z");
        }
    }
}