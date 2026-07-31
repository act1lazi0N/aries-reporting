package com.actilazion.ariesreportingproject.controller;

import com.actilazion.ariesreportingproject.dto.request.ExportRequest;
import com.actilazion.ariesreportingproject.dto.response.ApiResponse;
import com.actilazion.ariesreportingproject.dto.response.ReportJobResponse;
import com.actilazion.ariesreportingproject.service.export.ExportOrchestrator;
import com.actilazion.ariesreportingproject.service.security.UserAccessService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/export")
@RequiredArgsConstructor
@Tag(name = "Export", description = "Async report export endpoints")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class ExportController {
    private final ExportOrchestrator exportOrchestrator;
    private final UserAccessService userAccessService;

    @PostMapping("/request")
    @Operation(summary = "Request async export and return jobId immediately")
    public ResponseEntity<ApiResponse<ReportJobResponse>> requestExport(
            @Valid @RequestBody ExportRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID requestedBy = userAccessService.requireCurrentUserId(userDetails);
        userAccessService.requireAccountAccess(userDetails, request.accountId());
        String baseUrl = getBaseUrl(httpRequest);

        ReportJobResponse job = exportOrchestrator.createJob(request, requestedBy, baseUrl);

        return ResponseEntity.accepted()
                .body(ApiResponse.ok("Export job created. Poll status endpoint.", job));
    }

    @GetMapping("/{jobId}/status")
    @Operation(summary = "Poll export job status")
    public ResponseEntity<ApiResponse<ReportJobResponse>> getStatus(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID requestedBy = userAccessService.requireCurrentUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.ok(
                exportOrchestrator.getStatus(
                        jobId,
                        requestedBy,
                        userAccessService.isAdmin(userDetails),
                        getBaseUrl(httpRequest))));
    }

    @GetMapping("/{jobId}/download")
    @Operation(summary = "Download export job result")
    public ResponseEntity<Resource> download(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID requestedBy = userAccessService.requireCurrentUserId(userDetails);
        Path filePath = exportOrchestrator.getFileForDownload(
                jobId,
                requestedBy,
                userAccessService.isAdmin(userDetails));
        Resource resource = new FileSystemResource(filePath.toFile());

        String filename = filePath.getFileName().toString();
        MediaType mediaType = filename.endsWith(".pdf")
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument"
                        + ".spreadsheetml.sheet");

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename)
                                .build()
                                .toString())
                .body(resource);
    }

    private String getBaseUrl(HttpServletRequest request) {
        return request.getScheme() + "://"
                + request.getServerName() + ":"
                + request.getServerPort();
    }
}
