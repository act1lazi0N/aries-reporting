package com.actilazion.ariesreportingproject.entity.reporting;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "reporting_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reporting_original_tx_id",
                columnNames = "original_tx_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportingTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "original_tx_id", nullable = false, updatable = false)
    private UUID originalTxId;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private UUID toAccountId;

    @Column(name = "from_owner_name", nullable = false, length = 100)
    private String fromOwnerName;

    @Column(name = "to_owner_name", nullable = false, length = 100)
    private String toOwnerName;

    @Column(name = "from_account_number", nullable = false, length = 20)
    private String fromAccountNumber;

    @Column(name = "to_account_number", nullable = false, length = 20)
    private String toAccountNumber;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    @Builder.Default
    private String currency = "VND";

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 255)
    private String description;

    @Column(name = "day_of_week", nullable = false)
    private Short dayOfWeek;

    @Column(name = "hour_of_day", nullable = false)
    private Short hourOfDay;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @CreationTimestamp
    @Column(name = "synced_at", nullable = false, updatable = false)
    private OffsetDateTime syncedAt;
}
