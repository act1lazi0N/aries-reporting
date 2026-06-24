package com.actilazion.ariesreportingproject.dto.request;

import com.actilazion.ariesreportingproject.enums.ReportFormat;
import com.actilazion.ariesreportingproject.enums.ReportJobType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record ExportRequest(
        @NotNull(message = "accountId is required")
        UUID accountId,
        @NotNull(message = "jobType is required")
        ReportJobType jobType,
        @NotNull(message = "format is required")
        ReportFormat format,
        @NotNull
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$",
                message = "from must be in format yyyy-MM")
        String from,
        @NotNull
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$",
                message = "to must be in format yyyy-MM")
        String to
) { }
