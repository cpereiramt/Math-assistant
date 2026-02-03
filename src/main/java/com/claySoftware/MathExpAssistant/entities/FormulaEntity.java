package com.claySoftware.MathExpAssistant.entities;

import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.models.FormulaGroup;

import lombok.Getter;
import lombok.Setter;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Document(collection = "formulas")
@CompoundIndex(name = "unique_name_group", def = "{'name': 1, 'group': 1}", unique = true)
@Getter
@Setter
public class FormulaEntity {
    @Id
    private String id;

    private String name;

    private FormulaGroup group;

    private String equation;

    private List<String> parameters;

    private FormulaStatus status;
    private boolean variable;
    private int minParameters;
    private int maxParameters;
    private String operator;

}
