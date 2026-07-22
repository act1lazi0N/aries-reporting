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
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Financial reporting endpoints")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {
    private static final int MIN_YEAR = 2020;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_TOP_TRANSACTIONS_LIMIT = 100;
    private static final int MAX_PAGE_SIZE = 200;

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
        validatePageable(pageable);
        AccountStatementResponse res = statementService.getStatement(
                accountId,
                parseYearMonth(from, "from"),
                parseYearMonth(to, "to"),
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
        validateYearMonth(year, month);
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
        validateTimeRange(from, to);
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
        validateTimeRange(from, to);
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
        validateTimeRange(from, to);
        validateLimit(limit);
        return ResponseEntity.ok(ApiResponse.ok(
                statementService.getTopTransactions(accountId, from, to, limit)));
    }

    private YearMonth parseYearMonth(String value, String fieldName) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(fieldName + " must be in format yyyy-MM");
        }
    }

    private void validateYearMonth(int year, int month) {
        if (year < MIN_YEAR) {
            throw new IllegalArgumentException("year must be greater than or equal to " + MIN_YEAR);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }
    }

    private void validateTimeRange(OffsetDateTime from, OffsetDateTime to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
    }

    private void validateLimit(int limit) {
        if (limit < MIN_LIMIT || limit > MAX_TOP_TRANSACTIONS_LIMIT) {
            throw new IllegalArgumentException(
                    "limit must be between " + MIN_LIMIT + " and " + MAX_TOP_TRANSACTIONS_LIMIT);
        }
    }

    private void validatePageable(Pageable pageable) {
        if (pageable.getPageNumber() < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (pageable.getPageSize() < 1 || pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
