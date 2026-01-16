package com.claySoftware.MathExpAssistant.entities;

import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.models.FormulaGroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

    @NotBlank(message = "name is mandatory")
    private String name;

    private FormulaGroup group;

    @NotBlank(message = "equation is mandatory")
    private String equation;

    @NotEmpty(message = "parameters is mandatory")
    private List<String> parameters;

    private FormulaStatus status;

}
