package com.ecm.core.integration.mail.service;

import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.model.MailRule;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository.MailAccountAggregateRow;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository.MailRuleAggregateRow;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository.MailTrendAggregateRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MailReportingService {

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 120;

    private final ProcessedMailRepository processedMailRepository;
    private final MailAccountRepository accountRepository;
    private final MailRuleRepository ruleRepository;

    public MailReportResponse getReport(UUID accountId, UUID ruleId, LocalDate from, LocalDate to, Integer days) {
        ReportRange range = resolveRange(from, to, days);
        LocalDateTime start = range.startDate().atStartOfDay();
        LocalDateTime endExclusive = range.endDate().plusDays(1).atStartOfDay();

        Map<UUID, MailAccount> accounts = accountRepository.findAll().stream()
            .collect(Collectors.toMap(MailAccount::getId, account -> account));
        Map<UUID, MailRule> rules = ruleRepository.findAllByOrderByPriorityAsc().stream()
            .collect(Collectors.toMap(MailRule::getId, rule -> rule));

        List<MailReportAccountRow> accountRows = processedMailRepository
            .aggregateByAccount(start, endExclusive, accountId)
            .stream()
            .map(row -> toAccountRow(row, accounts))
            .toList();

        List<MailReportRuleRow> ruleRows = processedMailRepository
            .aggregateByRule(start, endExclusive, accountId, ruleId)
            .stream()
            .map(row -> toRuleRow(row, accounts, rules))
            .toList();

        List<MailReportTrendRow> trendRows = buildTrendRows(
            processedMailRepository.aggregateTrend(start, endExclusive, accountId, ruleId),
            range
        );

        MailReportTotals totals = summarizeTotals(trendRows, accountRows);

        return new MailReportResponse(
            accountId,
            ruleId,
            range.startDate(),
            range.endDate(),
            range.days(),
            totals,
            accountRows,
            ruleRows,
            trendRows
        );
    }

    public String exportReportCsv(MailReportResponse report) {
        StringBuilder csv = new StringBuilder();
        appendCsvRow(csv, "Mail Automation Report");
        appendCsvRow(csv, "GeneratedAt", LocalDateTime.now());
        appendCsvRow(csv, "StartDate", report.startDate());
        appendCsvRow(csv, "EndDate", report.endDate());
        appendCsvRow(csv, "Days", report.days());
        appendCsvRow(csv, "AccountFilter", report.accountId() != null ? report.accountId() : "ALL");
        appendCsvRow(csv, "RuleFilter", report.ruleId() != null ? report.ruleId() : "ALL");
        appendCsvRow(csv, "TotalProcessed", report.totals().processed());
        appendCsvRow(csv, "TotalErrors", report.totals().errors());
        appendCsvRow(csv, "TotalMessages", report.totals().total());
        csv.append("\n");

        appendCsvRow(csv, "Account Summary");
        appendCsvRow(csv, "Account", "Processed", "Errors", "Total", "LastProcessedAt", "LastErrorAt");
        for (MailReportAccountRow row : report.accounts()) {
            appendCsvRow(
                csv,
                row.accountName() != null ? row.accountName() : row.accountId(),
                row.processed(),
                row.errors(),
                row.total(),
                row.lastProcessedAt(),
                row.lastErrorAt()
            );
        }
        csv.append("\n");

        appendCsvRow(csv, "Rule Summary");
        appendCsvRow(csv, "Rule", "Account", "Processed", "Errors", "Total", "LastProcessedAt", "LastErrorAt");
        for (MailReportRuleRow row : report.rules()) {
            appendCsvRow(
                csv,
                row.ruleName() != null ? row.ruleName() : row.ruleId(),
                row.accountName() != null ? row.accountName() : row.accountId(),
                row.processed(),
                row.errors(),
                row.total(),
                row.lastProcessedAt(),
                row.lastErrorAt()
            );
        }
        csv.append("\n");

        appendCsvRow(csv, "Daily Trend");
        appendCsvRow(csv, "Date", "Processed", "Errors", "Total");
        for (MailReportTrendRow row : report.trend()) {
            appendCsvRow(csv, row.date(), row.processed(), row.errors(), row.total());
        }

        return csv.toString();
    }

    private MailReportAccountRow toAccountRow(MailAccountAggregateRow row, Map<UUID, MailAccount> accounts) {
        MailAccount account = accounts.get(row.getAccountId());
        String name = account != null ? account.getName() : null;
        long processed = safeCount(row.getProcessedCount());
        long errors = safeCount(row.getErrorCount());
        return new MailReportAccountRow(
            row.getAccountId(),
            name,
            processed,
            errors,
            processed + errors,
            row.getLastProcessedAt(),
            row.getLastErrorAt()
        );
    }

    private MailReportRuleRow toRuleRow(
        MailRuleAggregateRow row,
        Map<UUID, MailAccount> accounts,
        Map<UUID, MailRule> rules
    ) {
        MailRule rule = rules.get(row.getRuleId());
        UUID accountId = row.getAccountId() != null ? row.getAccountId() : rule != null ? rule.getAccountId() : null;
        MailAccount account = accountId != null ? accounts.get(accountId) : null;
        String accountName = account != null ? account.getName() : null;
        String ruleName = rule != null ? rule.getName() : null;
        long processed = safeCount(row.getProcessedCount());
        long errors = safeCount(row.getErrorCount());
        return new MailReportRuleRow(
            row.getRuleId(),
            ruleName,
            accountId,
            accountName,
            processed,
            errors,
            processed + errors,
            row.getLastProcessedAt(),
            row.getLastErrorAt()
        );
    }

    private List<MailReportTrendRow> buildTrendRows(List<MailTrendAggregateRow> raw, ReportRange range) {
        Map<LocalDate, MailTrendAggregateRow> byDate = new HashMap<>();
        for (MailTrendAggregateRow row : raw) {
            if (row.getDay() != null) {
                byDate.put(row.getDay(), row);
            }
        }
        List<MailReportTrendRow> trend = new ArrayList<>();
        LocalDate cursor = range.startDate();
        while (!cursor.isAfter(range.endDate())) {
            MailTrendAggregateRow row = byDate.get(cursor);
            long processed = row != null ? safeCount(row.getProcessedCount()) : 0L;
            long errors = row != null ? safeCount(row.getErrorCount()) : 0L;
            trend.add(new MailReportTrendRow(cursor, processed, errors, processed + errors));
            cursor = cursor.plusDays(1);
        }
        return trend;
    }

    private MailReportTotals summarizeTotals(List<MailReportTrendRow> trend, List<MailReportAccountRow> accounts) {
        long processed = trend.stream().mapToLong(MailReportTrendRow::processed).sum();
        long errors = trend.stream().mapToLong(MailReportTrendRow::errors).sum();
        if (processed == 0 && errors == 0 && !accounts.isEmpty()) {
            processed = accounts.stream().mapToLong(MailReportAccountRow::processed).sum();
            errors = accounts.stream().mapToLong(MailReportAccountRow::errors).sum();
        }
        return new MailReportTotals(processed, errors, processed + errors);
    }

    private ReportRange resolveRange(LocalDate from, LocalDate to, Integer days) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = to != null ? to : today;
        LocalDate startDate;
        if (from != null) {
            startDate = from;
        } else {
            int effectiveDays = resolveDays(days);
            startDate = endDate.minusDays(effectiveDays - 1L);
        }
        if (startDate.isAfter(endDate)) {
            LocalDate swap = startDate;
            startDate = endDate;
            endDate = swap;
        }
        int resolvedDays = Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);
        return new ReportRange(startDate, endDate, resolvedDays);
    }

    private int resolveDays(Integer days) {
        int requested = days != null ? days : DEFAULT_DAYS;
        return Math.max(1, Math.min(requested, MAX_DAYS));
    }

    private long safeCount(Long value) {
        return value != null ? value : 0L;
    }

    private static void appendCsvRow(StringBuilder target, Object... values) {
        String row = java.util.Arrays.stream(values)
            .map(MailReportingService::csvEscape)
            .collect(Collectors.joining(","));
        target.append(row).append("\n");
    }

    private static String csvEscape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains("\"") || text.contains(",") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    public record MailReportResponse(
        UUID accountId,
        UUID ruleId,
        LocalDate startDate,
        LocalDate endDate,
        int days,
        MailReportTotals totals,
        List<MailReportAccountRow> accounts,
        List<MailReportRuleRow> rules,
        List<MailReportTrendRow> trend
    ) {}

    public record MailReportTotals(long processed, long errors, long total) {}

    public record MailReportAccountRow(
        UUID accountId,
        String accountName,
        long processed,
        long errors,
        long total,
        LocalDateTime lastProcessedAt,
        LocalDateTime lastErrorAt
    ) {}

    public record MailReportRuleRow(
        UUID ruleId,
        String ruleName,
        UUID accountId,
        String accountName,
        long processed,
        long errors,
        long total,
        LocalDateTime lastProcessedAt,
        LocalDateTime lastErrorAt
    ) {}

    public record MailReportTrendRow(LocalDate date, long processed, long errors, long total) {}

    private record ReportRange(LocalDate startDate, LocalDate endDate, int days) {}
}
