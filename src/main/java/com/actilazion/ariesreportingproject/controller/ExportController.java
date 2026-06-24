package com.actilazion.ariesreportingproject.controller;

import com.actilazion.ariesreportingproject.dto.request.ExportRequest;
import com.actilazion.ariesreportingproject.dto.response.ApiResponse;
import com.actilazion.ariesreportingproject.dto.response.ReportJobResponse;
import com.actilazion.ariesreportingproject.enums.ReportJobStatus;
import com.actilazion.ariesreportingproject.service.export.ExportOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/export")
@RequiredArgsConstructor
@Tag(name = "Export", description = "Async report export endpoints")
@SecurityRequirement(name = "bearerAuth")
public class ExportController {
    private final ExportOrchestrator exportOrchestrator;

    @PostMapping("/request")
    @Operation(summary = "Request async export — returns jobId immediately")
    public ResponseEntity<ApiResponse<ReportJobResponse>> requestExport(
            @Valid @RequestBody ExportRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        // Extract user from IP - requiring UserDetailsService return UUID
        UUID requestedBy = extractUserId(userDetails);
        String baseUrl = getBaseUrl(httpRequest);

        ReportJobResponse job = exportOrchestrator.createJob(request, requestedBy, baseUrl);

        // Accepted
        return ResponseEntity.accepted()
                .body(ApiResponse.ok("Export job created. Poll status endpoint.", job));
    }

    @GetMapping("/{jobId}/status")
    @Operation(summary = "Poll export job status")
    public ResponseEntity<ApiResponse<ReportJobResponse>> getStatus(
            @PathVariable UUID jobId,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                exportOrchestrator.getStatus(jobId, getBaseUrl(httpRequest))));
    }

    @GetMapping("/{jobId}/download")
    @Operation(summary = "Download export job result")
    public ResponseEntity<Resource> download(
            @PathVariable UUID jobId
    ) {
        Path filePath = exportOrchestrator.getFileForDownload(
                jobId
        );
        Resource resource = new FileSystemResource(filePath.toFile());

        // Detect content type from extention
        String filename = filePath.getFileName().toString();
        MediaType mediaType = filename.endsWith(".pdf")
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument" +
                ".spreadsheetml.sheet");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename)
                                .build()
                                .toString())
                .body(resource);
    }

    // Helpers
    private UUID extractUserId(UserDetails userDetails) {
        // Temporary using random UUID — load from DB following with email in fact
        // Refactoring when add UserService
        return UUID.nameUUIDFromBytes(
                userDetails.getUsername().getBytes());
    }
    private String getBaseUrl(HttpServletRequest request) {
        return request.getScheme() + "://"
                + request.getServerName() + ":"
                + request.getServerPort();
    }
}
