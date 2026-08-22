package com.claySoftware.MathExpAssistant.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FormulaSourceSnapshot {
    private String formulaId;
    private String name;
    private String equation;
    private List<String> parameters;
}
