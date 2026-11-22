package com.claySoftware.MathExpAssistant.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FormulaSourceSnapshot {
    private String formulaId;
    private String name;
    private String equation;
    private List<String> parameters;
    private Integer version;
    private Map<String, MeasurementUnit> unitSnapshots;

    public FormulaSourceSnapshot(String formulaId, String name, String equation, List<String> parameters, Integer version) {
        this(formulaId, name, equation, parameters, version, null);
    }

    public FormulaSourceSnapshot(String formulaId, String name, String equation, List<String> parameters) {
        this(formulaId, name, equation, parameters, null);
    }
}
