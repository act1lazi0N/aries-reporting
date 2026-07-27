package com.actilazion.ariesreportingproject.service.export;

import com.actilazion.ariesreportingproject.config.AppProperties;
import com.actilazion.ariesreportingproject.dto.response.AccountStatementResponse;
import com.actilazion.ariesreportingproject.entity.reporting.ReportingTransaction;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties appProperties;

    public void sendMonthlyStatement(String recipientEmail, String fullName, String billingMonth, AccountStatementResponse statement, List<ReportingTransaction> topTransactions) {
        try {
            String html = buildHtml(
                    fullName, billingMonth, statement, topTransactions);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(appProperties.getMailFrom());
            helper.setTo(recipientEmail);
            helper.setSubject(
                    "[Aries] Monthly Statement — " + billingMonth);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("[EMAIL] Sent monthly statement recipientRef={} month={}",
                    recipientRef(recipientEmail), billingMonth);
        } catch (MessagingException e) {
            log.error("[EMAIL] Failed to send monthly statement recipientRef={} month={}: {}",
                    recipientRef(recipientEmail), billingMonth, e.getMessage(), e);
            throw new RuntimeException("Email send failed", e);
        }
    }

    private String buildHtml(
            String fullName,
            String billingMonth,
            AccountStatementResponse statement,
            List<ReportingTransaction> topTransactions) {

        Context ctx = new Context(Locale.getDefault());

        ctx.setVariable("fullName", fullName);
        ctx.setVariable("billingMonth", billingMonth);
        ctx.setVariable("totalDebit", statement.totalDebit());
        ctx.setVariable("totalCredit", statement.totalCredit());
        ctx.setVariable("netFlow", statement.netFlow());
        ctx.setVariable("topTransactions", topTransactions);
        String publicBaseUrl = normalizeBaseUrl(appProperties.getPublicBaseUrl());
        ctx.setVariable("dashboardUrl", publicBaseUrl + "/dashboard");
        ctx.setVariable("unsubscribeUrl", publicBaseUrl + "/unsubscribe");

        return templateEngine.process("email/monthly-statement", ctx);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:3000";
        }
        return baseUrl.replaceAll("/+$", "");
    }

    private String recipientRef(String recipientEmail) {
        return UUID.nameUUIDFromBytes(
                        recipientEmail.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
