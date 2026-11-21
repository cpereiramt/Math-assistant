package com.claySoftware.MathExpAssistant.models;

public record PlanUsage(Consumption privateFormulas, Consumption dailyExecutions, Consumption dailyBuilderPreviews) {
    public record Consumption(long used, int limit) {}
}
