package com.claySoftware.MathExpAssistant.controllers;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.services.FormulaService;
import org.springframework.web.bind.annotation.*;

import javax.script.ScriptException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/formulas")
public class FormulaController {

    private final FormulaService formulaService;
    private final FormulaRepository formulaRepository;

    public FormulaController(FormulaService formulaService, FormulaRepository formulaRepository) {
       this.formulaRepository = formulaRepository;
       this.formulaService = formulaService;
    }
    @PostMapping("/execute")
    public double executeFormula(
            @RequestParam String formulaName,
            @RequestBody Map<String, Double> variables) throws ScriptException {
        //TODO : Implement the logic to use some ai mathematics model when the formula is not found on database
        return formulaService.executeFormula(formulaName, variables);
    }
    //TODO : later implement some validation for FormulaEntity and on FormulaService
    @PostMapping("/insert")
    public String createNewFormula(@RequestBody  FormulaEntity formulaEntity) {


        FormulaEntity isFormulaDifferentOfNull =  formulaRepository.save(formulaEntity);
        if(isFormulaDifferentOfNull != null) {
            return "new formula successful saved";
        }
        return "Error when trying to save";
    }
    @DeleteMapping("/{id}")
    public String deleteFormula(@PathVariable String id) {
        Optional<FormulaEntity> formulaToDelete = formulaRepository.findById(id);
        if(formulaToDelete.isPresent()) {
            formulaRepository.delete(formulaToDelete.get());
            return "formula deleted with success !";
        }
        return "formula not found !" ;

    }

    @GetMapping("/getAll")
    public List<FormulaEntity> getAllFormula() {
        return formulaRepository.findAll();
    }

    @GetMapping("/{name}")
    public Optional<FormulaEntity> getFormulaByName(@PathVariable String name) {
        return formulaRepository.findByName(name);
   }
}
