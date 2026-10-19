package com.sklum.backend.web;

import com.sklum.backend.model.PdpResponse;
import com.sklum.backend.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "PDP", description = "Product detail + the coarse, precomputed delivery promise")
public class PdpController {

    private final CatalogService catalogService;

    public PdpController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Operation(summary = "Product detail with a zone-level (coarse) delivery promise")
    @GetMapping("/pdp/{sku}")
    public PdpResponse pdp(@PathVariable String sku,
                            @Parameter(description = "Optional — defaults to a representative demo postal code if omitted")
                            @RequestParam(required = false) String postalCode) {
        return catalogService.pdp(sku, postalCode);
    }
}
