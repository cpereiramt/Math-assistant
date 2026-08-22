package com.claySoftware.MathExpAssistant.models;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class FormulaPreviewRequest {
    private FormulaEntity formula;
    private Map<String, Double> variables;
}
