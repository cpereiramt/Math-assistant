package com.claySoftware.MathExpAssistant.entities;

import com.claySoftware.MathExpAssistant.models.FormulaGroup;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Document(collection = "formulas")
public class FormulaEntity {
    @Id
    private String id;
    private String name;        // Nome da fórmula
    private FormulaGroup group; // Grupo da fórmula
    private String equation;    // Equação como string
    private List<String> parameters; // Lista de parâmetros (ex: ["x", "y"])

    // Getters e Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public FormulaGroup getGroup() { return group; }
    public void setGroup(FormulaGroup group) { this.group = group; }

    public String getEquation() { return equation; }
    public void setEquation(String equation) { this.equation = equation; }

    public List<String> getParameters() { return parameters; }
    public void setParameters(List<String> parameters) { this.parameters = parameters; }
}
