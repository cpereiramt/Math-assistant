package com.claySoftware.MathExpAssistant.models;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record MeasurementUnit(String code, String name, String dimension, String reference,
                              @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal factor) {
    public Map<String, Integer> dimensions() {
        return dimensions(dimensionKey());
    }

    public static Map<String, Integer> dimensions(String key) {
        if ("SCALAR".equals(key)) return Map.of();
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        for (String term : key.split(",")) {
            String[] exponent = term.split("\\^", 2);
            dimensions.put(exponent[0], exponent.length == 1 ? 1 : Integer.parseInt(exponent[1]));
        }
        return Map.copyOf(dimensions);
    }

    public String dimensionKey() {
        if ("CURRENCY".equals(dimension)) return "CURRENCY_" + code;
        return dimension.startsWith("CUSTOM:") ? "CUSTOM_" + reference : dimension;
    }
}
