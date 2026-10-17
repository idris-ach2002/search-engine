package com.sorbonne.backend.ranking.web;

import com.sorbonne.backend.ranking.model.PageRankResponses;
import com.sorbonne.backend.ranking.service.PageRankService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ranking/pagerank")
public class PageRankAdminController {
    private final PageRankService service;

    public PageRankAdminController(PageRankService service) {
        this.service = service;
    }

    @PostMapping("/rebuild")
    public PageRankResponses.Rebuild rebuild() {
        return service.rebuild();
    }

    @GetMapping("/status")
    public PageRankResponses.Status status() {
        return service.status();
    }

    @GetMapping("/top")
    public PageRankResponses.Top top(@RequestParam(defaultValue = "20") int limit) {
        return service.top(limit);
    }
}
