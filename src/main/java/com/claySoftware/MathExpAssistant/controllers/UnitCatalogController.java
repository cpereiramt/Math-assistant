package com.claySoftware.MathExpAssistant.controllers;

import com.claySoftware.MathExpAssistant.services.UnitCatalog;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/formulas")
public class UnitCatalogController {
    private final UnitCatalog catalog;

    public UnitCatalogController(UnitCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/units")
    public List<UnitCatalog.Entry> units() {
        return catalog.list(false);
    }

    @GetMapping("/admin/units")
    public List<UnitCatalog.Entry> allUnits() {
        return catalog.list(true);
    }

    @PutMapping("/admin/units/{code}")
    public UnitCatalog.Entry save(@PathVariable String code, @RequestBody UnitCatalog.Update update) {
        return catalog.save(code, update);
    }
}
