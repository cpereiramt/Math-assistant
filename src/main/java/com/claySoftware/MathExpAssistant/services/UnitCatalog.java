package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.models.MeasurementUnit;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class UnitCatalog {
    private static final Map<String, MeasurementUnit> DEFAULTS = List.of(
            unit("kg", "Quilograma", "MASS", "kg", "1"),
            unit("g", "Grama", "MASS", "kg", "0.001"),
            unit("m", "Metro", "LENGTH", "m", "1"),
            unit("km", "Quilômetro", "LENGTH", "m", "1000"),
            unit("s", "Segundo", "TIME", "s", "1"),
            unit("BRL", "Real brasileiro", "CURRENCY", "BRL", "1"),
            unit("USD", "Dólar americano", "CURRENCY", "USD", "1"),
            unit("m2", "Metro quadrado", "LENGTH^2", "m2", "1"),
            unit("mps", "Metro por segundo", "LENGTH,TIME^-1", "mps", "1"),
            unit("BRL_per_kg", "Real por quilograma", "CURRENCY_BRL,MASS^-1", "BRL_per_kg", "1"),
            unit("USD_per_kg", "Dólar por quilograma", "CURRENCY_USD,MASS^-1", "USD_per_kg", "1"))
            .stream().collect(java.util.stream.Collectors.toUnmodifiableMap(MeasurementUnit::code, unit -> unit));

    private final MongoTemplate mongo;

    public UnitCatalog(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public List<Entry> list(boolean includeInactive) {
        Map<String, Entry> entries = new java.util.HashMap<>();
        DEFAULTS.forEach((code, unit) -> entries.put(code, new Entry(code, unit, true, null)));
        mongo.findAll(Entry.class).forEach(entry -> entries.put(entry.code(), entry));
        return entries.values().stream()
                .filter(entry -> includeInactive || entry.active())
                .sorted(Comparator.comparing(Entry::code))
                .toList();
    }

    public MeasurementUnit requireActive(String code) {
        Entry saved = mongo.findById(code, Entry.class);
        MeasurementUnit unit = saved == null ? DEFAULTS.get(code) : saved.active() ? saved.unit() : null;
        if (unit == null) {
            throw new IllegalArgumentException("Unavailable unit: " + code);
        }
        return unit;
    }

    public Entry save(String code, Update update) {
        if (!code.matches("[A-Za-z][A-Za-z0-9_]{0,15}") || update.name() == null
                || update.name().isBlank() || update.name().length() > 80 || update.reference() == null) {
            throw new IllegalArgumentException("Provide a valid code, name and reference unit");
        }
        Entry saved = mongo.findById(code, Entry.class);
        MeasurementUnit existing = saved == null ? DEFAULTS.get(code) : saved.unit();
        if (saved != null && !saved.version().equals(update.version())) {
            throw new org.springframework.dao.OptimisticLockingFailureException("Unit changed concurrently");
        }
        BigDecimal factor = update.factor();
        if (factor == null || factor.signum() <= 0 || factor.precision() > 34
                || Math.abs((long) factor.scale()) > 30) {
            throw new IllegalArgumentException("Unit factor must be a positive decimal within the supported range");
        }
        MeasurementUnit unit;
        if (existing != null) {
            if (!existing.reference().equals(update.reference())) {
                throw new IllegalArgumentException("An existing unit keeps its reference unit");
            }
            unit = new MeasurementUnit(code, update.name().trim(), existing.dimension(), existing.reference(), factor);
        } else if (code.equals(update.reference()) && update.dimension() != null
                && update.dimension().matches("[\\p{L}][\\p{L}0-9 _-]{0,39}")) {
            unit = new MeasurementUnit(code, update.name().trim(), "CUSTOM:" + update.dimension(), code, factor);
        } else if (code.equals(update.reference()) && code.matches("[A-Z]{3}") && update.dimension() == null) {
            unit = new MeasurementUnit(code, update.name().trim(), "CURRENCY", code, factor);
        } else {
            MeasurementUnit reference = requireActive(update.reference());
            if ("CURRENCY".equals(reference.dimension()) || !reference.code().equals(reference.reference())) {
                throw new IllegalArgumentException("Choose the base unit of a physical dimension");
            }
            unit = new MeasurementUnit(code, update.name().trim(), reference.dimension(), reference.code(), factor);
        }
        if (unit.code().equals(unit.reference()) && factor.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("A base unit or currency must have factor 1");
        }
        return mongo.save(new Entry(code, unit, update.active(), saved == null ? null : saved.version()));
    }

    private static MeasurementUnit unit(String code, String name, String dimension, String reference, String factor) {
        return new MeasurementUnit(code, name, dimension, reference, new BigDecimal(factor));
    }

    public record Update(String name, String reference, BigDecimal factor, boolean active, Long version, String dimension) {}

    @Document("measurement_units")
    public record Entry(@Id String code, MeasurementUnit unit, boolean active, @Version Long version) {}
}
