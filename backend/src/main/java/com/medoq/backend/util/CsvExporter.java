package com.medoq.backend.util;

import com.medoq.backend.dto.admin.AdminTransactionDto;
import com.medoq.backend.dto.admin.CommissionReportDto;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds RFC 4180-compliant CSV strings for admin financial exports.
 *
 * Usage:
 *   String csv = CsvExporter.transactions(list);
 *   response.setContentType("text/csv; charset=UTF-8");
 *   response.setHeader("Content-Disposition", "attachment; filename=transactions.csv");
 */
public final class CsvExporter {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");
    private static final DateTimeFormatter DT_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(DAKAR);

    private CsvExporter() {}

    // ── Transactions ──────────────────────────────────────────────

    public static String transactions(List<AdminTransactionDto> rows) {
        var sb = new StringBuilder();
        sb.append("ID,Référence transaction,Référence réservation,")
          .append("Téléphone client,Pharmacie,Montant (FCFA),")
          .append("Commission (FCFA),Net (FCFA),Méthode,Statut,Payé le,Créé le\n");

        for (AdminTransactionDto r : rows) {
            sb.append(esc(r.id()))
              .append(',').append(esc(r.transactionRef()))
              .append(',').append(esc(r.reservationRef()))
              .append(',').append(esc(r.customerPhone()))
              .append(',').append(esc(r.pharmacyName()))
              .append(',').append(fmt(r.amount()))
              .append(',').append(fmt(r.commissionAmount()))
              .append(',').append(fmt(r.netAmount()))
              .append(',').append(esc(r.method()))
              .append(',').append(esc(r.status()))
              .append(',').append(r.paidAt()    != null ? DT_FMT.format(r.paidAt())    : "")
              .append(',').append(r.createdAt() != null ? DT_FMT.format(r.createdAt()) : "")
              .append('\n');
        }
        return sb.toString();
    }

    // ── Commission report ─────────────────────────────────────────

    public static String commissions(CommissionReportDto report) {
        var sb = new StringBuilder();

        // Summary header
        sb.append("Période,Du,Au\n");
        sb.append(',')
          .append(report.from() != null ? DT_FMT.format(report.from()) : "")
          .append(',')
          .append(report.to()   != null ? DT_FMT.format(report.to())   : "")
          .append('\n').append('\n');

        // Totals
        sb.append("Total transactions,Total brut (FCFA),Total commission (FCFA),Total net (FCFA)\n");
        sb.append(report.totalTransactions())
          .append(',').append(fmt(report.totalGross()))
          .append(',').append(fmt(report.totalCommission()))
          .append(',').append(fmt(report.totalNet()))
          .append('\n').append('\n');

        // Per-pharmacy rows
        sb.append("Pharmacie,Transactions,Brut (FCFA),Commission (FCFA),Net (FCFA)\n");
        for (CommissionReportDto.PharmacyRow row : report.rows()) {
            sb.append(esc(row.pharmacyName()))
              .append(',').append(row.transactionCount())
              .append(',').append(fmt(row.grossRevenue()))
              .append(',').append(fmt(row.commissionAmount()))
              .append(',').append(fmt(row.netAmount()))
              .append('\n');
        }
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────

    /** Escape a value for CSV — wrap in quotes if it contains comma/quote/newline. */
    private static String esc(Object val) {
        if (val == null) return "";
        String s = val.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String fmt(BigDecimal val) {
        return val == null ? "0" : val.toPlainString();
    }
}
