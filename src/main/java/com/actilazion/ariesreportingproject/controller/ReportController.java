package com.actilazion.ariesreportingproject.controller;

import com.actilazion.ariesreportingproject.dto.response.*;
import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;
import com.actilazion.ariesreportingproject.service.reporting.AdminReportService;
import com.actilazion.ariesreportingproject.service.reporting.SpendingPatternService;
import com.actilazion.ariesreportingproject.service.reporting.StatementService;
import com.actilazion.ariesreportingproject.service.security.UserAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Financial reporting endpoints")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {
    private final StatementService statementService;
    private final AdminReportService adminReportService;
    private final SpendingPatternService spendingPatternService;
    private final UserAccessService userAccessService;

    // F1 - Account Statement
    @GetMapping("/statement")
    @Operation(summary = "Get account statement for a period")
    public ResponseEntity<ApiResponse<AccountStatementResponse>> getStatement(
            @RequestParam UUID accountId,
            @RequestParam String from,
            @RequestParam String to,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        userAccessService.requireAccountAccess(userDetails, accountId);
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
            @RequestParam int  month,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        userAccessService.requireAccountAccess(userDetails, accountId);
        return ResponseEntity.ok(ApiResponse.ok(statementService.getMonthlySummary(accountId, year, month)));
    }

    // F3 - Spending Pattern
    @GetMapping("/spending-pattern")
    @Operation(summary = "Hourly spending pattern — uses pre-computed hour_of_day")
    public ResponseEntity<ApiResponse<SpendingPatternResponse>> getSpendingPattern(
            @RequestParam UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        userAccessService.requireAccountAccess(userDetails, accountId);
        return ResponseEntity.ok(ApiResponse.ok(
                spendingPatternService.getSpendingPattern(accountId, from, to)));
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

    // F5 - Top Transactions
    @GetMapping("/top-transactions")
    @Operation(summary = "Top N largest transactions in a period")
    public ResponseEntity<ApiResponse<List<ReportingTransaction>>> getTopTransactions(
            @RequestParam UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            @RequestParam(defaultValue = "5") int limit,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        userAccessService.requireAccountAccess(userDetails, accountId);
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.getTopTransactions(accountId, from, to, limit)));
    }
}
