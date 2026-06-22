package com.actilazion.ariesreportingproject.controller;

import com.actilazion.ariesreportingproject.dto.response.AccountStatementResponse;
import com.actilazion.ariesreportingproject.dto.response.AdminOverviewResponse;
import com.actilazion.ariesreportingproject.dto.response.ApiResponse;
import com.actilazion.ariesreportingproject.dto.response.TransactionSummaryResponse;
import com.actilazion.ariesreportingproject.service.reporting.AdminReportService;
import com.actilazion.ariesreportingproject.service.reporting.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Financial reporting endpoints")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {
    private final StatementService statementService;
    private final AdminReportService adminReportService;

    // F1 - Account Statement
    @GetMapping("/statement")
    @Operation(summary = "Get account statement for a period")
    public ResponseEntity<ApiResponse<AccountStatementResponse>> getStatement(
            @RequestParam UUID accountId,
            @RequestParam String from,
            @RequestParam String to,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        AccountStatementResponse res = statementService.getStatement(
                accountId,
                YearMonth.parse(from),
                YearMonth.parse(to),
                pageable
        );
        return ResponseEntity.ok(ApiResponse.ok(res));
    }

    // F2 - Monthly Summary
    @GetMapping("/summary/monthly")
    @Operation(summary = "Get monthly summary for an account")
    public ResponseEntity<ApiResponse<TransactionSummaryResponse>> getMonthlySummary(
            @RequestParam UUID accountId,
            @RequestParam int  year,
            @RequestParam int  month
    ) {
        return ResponseEntity.ok(ApiResponse.ok(statementService.getMonthlySummary(accountId, year, month)));
    }

    // F4 - Admin Overview
    @GetMapping("/admin/overview")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Platform-wide transaction overview (admin only)")
    public ResponseEntity<ApiResponse<AdminOverviewResponse>> getAdminOverview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminReportService.getOverview(from, to)));
    }
}
