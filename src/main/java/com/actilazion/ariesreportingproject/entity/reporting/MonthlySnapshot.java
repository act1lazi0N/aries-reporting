package com.actilazion.ariesreportingproject.entity.reporting;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "monthly_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_monthly_account_month",
                columnNames = {"account_id", "\"year\"", "\"month\""}
        )
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MonthlySnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "\"year\"", nullable = false)
    private Short year;

    @Column(name = "\"month\"", nullable = false)
    private Short month;

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

    @Column(name = "is_finalised", nullable = false)
    @Builder.Default
    private Boolean isFinalised = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
