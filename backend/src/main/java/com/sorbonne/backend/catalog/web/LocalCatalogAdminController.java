package com.sorbonne.backend.catalog.web;

import com.sorbonne.backend.catalog.LocalCatalogSyncService;
import com.sorbonne.backend.catalog.LocalCatalogBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/catalog")
public class LocalCatalogAdminController {
    private final LocalCatalogSyncService service;
    private final LocalCatalogBuilder builder;
    public LocalCatalogAdminController(LocalCatalogSyncService service, LocalCatalogBuilder builder) {
        this.service = service;
        this.builder = builder;
    }

    @GetMapping("/status")
    public LocalCatalogSyncService.CatalogStatus status() { return service.status(); }

    /** Generate catalog.csv without changing PostgreSQL. */
    @PostMapping("/generate")
    public LocalCatalogBuilder.GenerationResult generate() { return builder.generate(); }

    @PostMapping("/sync")
    public LocalCatalogSyncService.SyncResult sync(@RequestParam(defaultValue="false") boolean prune) {
        return service.sync(prune);
    }
}
