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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties appProperties;

    @Async("exportTaskExecutor")
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
            log.info("[EMAIL] Sent monthly statement to={} month={}",
                    recipientEmail, billingMonth);
        } catch (MessagingException e) {
            log.error("[EMAIL] Failed to send monthly statement to={} month={}: {}",
                    recipientEmail, billingMonth, e.getMessage(), e);
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
        ctx.setVariable("dashboardUrl", "http://localhost:3000/dashboard");
        ctx.setVariable("unsubscribeUrl", "http://localhost:3000/unsubscribe");

        return templateEngine.process("email/monthly-statement", ctx);
    }
}
