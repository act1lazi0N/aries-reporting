package com.actilazion.ariesreportingproject.entity.reporting;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "daily_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_account_date",
                columnNames = {"account_id", "snapshot_date"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailySnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "opening_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "total_debit", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(name = "total_credit", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(name = "tx_count", nullable = false)
    @Builder.Default
    private Integer txCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
