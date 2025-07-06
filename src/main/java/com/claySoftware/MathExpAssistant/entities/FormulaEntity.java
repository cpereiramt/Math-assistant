package com.claySoftware.MathExpAssistant.entities;

import com.claySoftware.MathExpAssistant.models.FormulaGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.mapping.Formula;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
@Document(collection = "formulas")
@CompoundIndex(name = "unique_name_group", def = "{'name': 1, 'group': 1}", unique = true)
public class FormulaEntity {
    @Id
    private String id;

    @NotBlank(message = "name is mandatory")
    private String name;

    @Pattern(regexp = "ARITHMETIC|TRIGONOMETRY|ALGEBRA", message = "Invalid formula group")
    private String group;

    @NotBlank(message = "equation is mandatory")
    private String equation;

    @NotEmpty(message = "parameters is mandatory")
    private List<String> parameters;


    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public FormulaGroup getGroup() { return FormulaGroup.valueOf(group); }
    public void setGroup(String group) { this.group = group; }

    public String getEquation() { return equation; }
    public void setEquation(String equation) { this.equation = equation; }

    public List<String> getParameters() { return parameters; }
    public void setParameters(List<String> parameters) { this.parameters = parameters; }
}
