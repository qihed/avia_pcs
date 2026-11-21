package ru.mirea.avia.controller;

import org.springframework.web.bind.annotation.*;
import ru.mirea.avia.dto.AuditEventResponse;
import ru.mirea.avia.service.AuditService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {
    private final AuditService service;
    public AuditController(AuditService service) { this.service = service; }
    @GetMapping public List<AuditEventResponse> list(@RequestParam(defaultValue="") String q,
                                                     @RequestParam(defaultValue="") String eventType,
                                                     @RequestParam(defaultValue="100") int limit) {
        return service.find(q, eventType, limit);
    }
}
