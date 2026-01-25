package com.claySoftware.MathExpAssistant.models;

import java.util.List;
import java.util.Map;

public class ExecuteFormulaRequest {
    private String formulaName;

    // Para fórmula fixa
    private Map<String, Double> variables;

    // Para fórmula variádica
    private List<Double> values;

    public String getFormulaName() {
        return formulaName;
    }

    public void setFormulaName(String formulaName) {
        this.formulaName = formulaName;
    }

    public Map<String, Double> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Double> variables) {
        this.variables = variables;
    }

    public List<Double> getValues() {
        return values;
    }

    public void setValues(List<Double> values) {
        this.values = values;
    }
}
