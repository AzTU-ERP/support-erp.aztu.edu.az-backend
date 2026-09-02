package com.aztu.support_erp.catalog.controller;

import com.aztu.support_erp.catalog.dto.ModuleConfigResponse;
import com.aztu.support_erp.catalog.service.CatalogService;
import com.aztu.support_erp.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the ticket form's two selects are built from. Readable by any authenticated account. */
@RestController
@RequestMapping("/api/support/config")
public class SupportConfigController {

    private final CatalogService service;

    public SupportConfigController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/modules")
    public ApiResponse<List<ModuleConfigResponse>> modules() {
        return ApiResponse.ok(service.listModules());
    }
}
