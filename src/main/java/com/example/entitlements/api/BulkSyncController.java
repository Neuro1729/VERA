package com.example.entitlements.api;

import com.example.entitlements.request.BulkSyncPreview;
import com.example.entitlements.request.BulkSyncRequest;
import com.example.entitlements.request.GrantsSyncInput;
import com.example.entitlements.request.OrganizationSyncInput;
import com.example.entitlements.request.ResourcesSyncInput;
import com.example.entitlements.service.BulkSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/sync")
public class BulkSyncController {
    private final BulkSyncService bulkSyncService;

    public BulkSyncController(BulkSyncService bulkSyncService) {
        this.bulkSyncService = bulkSyncService;
    }

    @PostMapping("/preview")
    public BulkSyncPreview preview(@PathVariable String tenantId, @RequestBody BulkSyncRequest request) {
        return bulkSyncService.preview(tenantId, request);
    }

    @PostMapping
    public ResponseEntity<BulkSyncPreview> apply(@PathVariable String tenantId, @RequestBody BulkSyncRequest request) {
        return response(bulkSyncService.apply(tenantId, request));
    }

    @PostMapping("/organization/preview")
    public BulkSyncPreview previewOrganization(
            @PathVariable String tenantId,
            @RequestBody OrganizationSyncInput request
    ) {
        return bulkSyncService.previewOrganization(tenantId, request);
    }

    @PostMapping("/organization")
    public ResponseEntity<BulkSyncPreview> applyOrganization(
            @PathVariable String tenantId,
            @RequestBody OrganizationSyncInput request
    ) {
        return response(bulkSyncService.applyOrganization(tenantId, request));
    }

    @PostMapping("/resources/preview")
    public BulkSyncPreview previewResources(
            @PathVariable String tenantId,
            @RequestBody ResourcesSyncInput request
    ) {
        return bulkSyncService.previewResources(tenantId, request);
    }

    @PostMapping("/resources")
    public ResponseEntity<BulkSyncPreview> applyResources(
            @PathVariable String tenantId,
            @RequestBody ResourcesSyncInput request
    ) {
        return response(bulkSyncService.applyResources(tenantId, request));
    }

    @PostMapping("/grants/preview")
    public BulkSyncPreview previewGrants(
            @PathVariable String tenantId,
            @RequestBody GrantsSyncInput request
    ) {
        return bulkSyncService.previewGrants(tenantId, request);
    }

    @PostMapping("/grants")
    public ResponseEntity<BulkSyncPreview> applyGrants(
            @PathVariable String tenantId,
            @RequestBody GrantsSyncInput request
    ) {
        return response(bulkSyncService.applyGrants(tenantId, request));
    }

    private static ResponseEntity<BulkSyncPreview> response(BulkSyncPreview preview) {
        HttpStatus status = preview.valid() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(preview);
    }
}
