package com.actilazion.ariesreportingproject.controller;

import com.actilazion.ariesreportingproject.dto.response.ApiResponse;
import com.actilazion.ariesreportingproject.service.sync.BackfillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/admin/backfill")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Backfill", description = "Manual data sync operations")
@SecurityRequirement(name = "bearerAuth")
public class BackfillController {
    private final BackfillService backfillService;

    @PostMapping
    @Operation(summary = "Manually trigger a backfill operation")
    public ResponseEntity<ApiResponse<Long>> triggerBackfill(
            @RequestParam(defaultValue = "2020-01-01T00:00:00Z")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime since
    ) {
        long synced = backfillService.backfill(since);
        return ResponseEntity.ok(ApiResponse.ok("Backfill completed. Records synced: " + synced, synced));
    }
}
