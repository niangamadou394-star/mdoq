package com.medoq.backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.medoq.backend.entity.Payment;
import com.medoq.backend.entity.ReservationItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Generates PDF invoices using OpenPDF (LGPL fork of iText 4).
 */
@Service
@Slf4j
public class InvoiceService {

    private static final ZoneId DAKAR_TZ = ZoneId.of("Africa/Dakar");
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(DAKAR_TZ);

    // ── Colors ─────────────────────────────────────────────────────

    private static final Color BRAND_BLUE  = new Color(0x1A, 0x56, 0xDB);
    private static final Color BRAND_LIGHT = new Color(0xEF, 0xF6, 0xFF);
    private static final Color GRAY        = new Color(0x6B, 0x72, 0x80);
    private static final Color DARK        = new Color(0x11, 0x18, 0x27);
    private static final Color WHITE       = Color.WHITE;

    // ── Fonts ──────────────────────────────────────────────────────

    private static final Font FONT_TITLE  = new Font(Font.HELVETICA, 22, Font.BOLD,  BRAND_BLUE);
    private static final Font FONT_H2     = new Font(Font.HELVETICA, 14, Font.BOLD,  DARK);
    private static final Font FONT_LABEL  = new Font(Font.HELVETICA, 10, Font.BOLD,  GRAY);
    private static final Font FONT_VALUE  = new Font(Font.HELVETICA, 10, Font.NORMAL, DARK);
    private static final Font FONT_TH     = new Font(Font.HELVETICA, 10, Font.BOLD,  WHITE);
    private static final Font FONT_TD     = new Font(Font.HELVETICA, 10, Font.NORMAL, DARK);
    private static final Font FONT_TOTAL  = new Font(Font.HELVETICA, 12, Font.BOLD,  BRAND_BLUE);
    private static final Font FONT_FOOTER = new Font(Font.HELVETICA, 8,  Font.ITALIC, GRAY);

    // ── Public API ─────────────────────────────────────────────────

    /**
     * Generates a PDF invoice for a completed payment.
     *
     * @return PDF bytes ready for email attachment or HTTP response
     */
    public byte[] generateInvoice(Payment payment) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 60);
            PdfWriter.getInstance(doc, out);
            doc.open();

            addHeader(doc, payment);
            addSeparator(doc);
            addParties(doc, payment);
            addSeparator(doc);
            addItemsTable(doc, payment);
            addTotals(doc, payment);
            addPaymentInfo(doc, payment);
            addFooter(doc);

            doc.close();
            log.debug("Invoice generated for payment {}", payment.getId());
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Invoice generation failed for payment " + payment.getId(), e);
        }
    }

    /** Returns the invoice filename: Facture_MQ-YYMMDD-NNNNN.pdf */
    public String invoiceFilename(Payment payment) {
        return "Facture_" + payment.getReservation().getReference().replace("/", "-") + ".pdf";
    }

    // ── Sections ──────────────────────────────────────────────────

    private void addHeader(Document doc, Payment payment) throws DocumentException {
        // Logo / Brand name
        Paragraph brand = new Paragraph("💊 MEDOQ", FONT_TITLE);
        brand.setAlignment(Element.ALIGN_LEFT);
        doc.add(brand);

        Paragraph tagline = new Paragraph("Plateforme de pharmacies au Sénégal", FONT_FOOTER);
        tagline.setSpacingBefore(2);
        doc.add(tagline);

        // Invoice title + reference (right-aligned)
        Paragraph title = new Paragraph("FACTURE", FONT_H2);
        title.setAlignment(Element.ALIGN_RIGHT);
        title.setSpacingBefore(10);
        doc.add(title);

        Paragraph ref = new Paragraph("Réf: #" + payment.getReservation().getReference(), FONT_VALUE);
        ref.setAlignment(Element.ALIGN_RIGHT);
        doc.add(ref);

        if (payment.getPaidAt() != null) {
            Paragraph date = new Paragraph(
                "Date: " + DATE_FMT.format(payment.getPaidAt()), FONT_VALUE);
            date.setAlignment(Element.ALIGN_RIGHT);
            doc.add(date);
        }
    }

    private void addParties(Document doc, Payment payment) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(15);

        // Client
        PdfPCell clientCell = new PdfPCell();
        clientCell.setBorder(Rectangle.NO_BORDER);
        clientCell.addElement(new Phrase("CLIENT", FONT_LABEL));
        var customer = payment.getCustomer();
        clientCell.addElement(new Phrase(
            customer.getFirstName() + " " + customer.getLastName(), FONT_VALUE));
        clientCell.addElement(new Phrase(customer.getPhone(), FONT_VALUE));
        if (customer.getEmail() != null) {
            clientCell.addElement(new Phrase(customer.getEmail(), FONT_VALUE));
        }
        table.addCell(clientCell);

        // Pharmacie
        PdfPCell pharmCell = new PdfPCell();
        pharmCell.setBorder(Rectangle.NO_BORDER);
        pharmCell.addElement(new Phrase("PHARMACIE", FONT_LABEL));
        var pharmacy = payment.getReservation().getPharmacy();
        pharmCell.addElement(new Phrase(pharmacy.getName(), FONT_VALUE));
        pharmCell.addElement(new Phrase(pharmacy.getAddress() + ", " + pharmacy.getCity(), FONT_VALUE));
        pharmCell.addElement(new Phrase(pharmacy.getPhone(), FONT_VALUE));
        table.addCell(pharmCell);

        doc.add(table);
    }

    private void addItemsTable(Document doc, Payment payment) throws DocumentException {
        Paragraph medTitle = new Paragraph("Détail de la commande", FONT_H2);
        medTitle.setSpacingBefore(20);
        medTitle.setSpacingAfter(8);
        doc.add(medTitle);

        PdfPTable table = new PdfPTable(new float[]{4, 1, 2, 2});
        table.setWidthPercentage(100);

        // Header row
        for (String header : new String[]{"Médicament", "Qté", "Prix unitaire", "Sous-total"}) {
            PdfPCell cell = new PdfPCell(new Phrase(header, FONT_TH));
            cell.setBackgroundColor(BRAND_BLUE);
            cell.setPadding(8);
            cell.setBorderColor(BRAND_BLUE);
            table.addCell(cell);
        }

        // Item rows
        boolean alt = false;
        for (ReservationItem item : payment.getReservation().getItems()) {
            Color bg = alt ? BRAND_LIGHT : WHITE;

            addTableCell(table, item.getMedication().getName() +
                (item.getMedication().getStrength() != null
                    ? " " + item.getMedication().getStrength() : ""),
                bg, Element.ALIGN_LEFT);

            addTableCell(table, String.valueOf(item.getQuantity()), bg, Element.ALIGN_CENTER);
            addTableCell(table, formatFcfa(item.getUnitPrice()),    bg, Element.ALIGN_RIGHT);
            addTableCell(table, formatFcfa(
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))),
                bg, Element.ALIGN_RIGHT);

            alt = !alt;
        }

        doc.add(table);
    }

    private void addTotals(Document doc, Payment payment) throws DocumentException {
        PdfPTable totals = new PdfPTable(new float[]{6, 2});
        totals.setWidthPercentage(50);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setSpacingBefore(10);

        addTotalRow(totals, "Montant payé",  formatFcfa(payment.getAmount()),           false);
        if (payment.getCommissionAmount() != null) {
            addTotalRow(totals, "Commission Medoq (" +
                payment.getCommissionRate().multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "%)",
                "- " + formatFcfa(payment.getCommissionAmount()), false);
            addTotalRow(totals, "Montant pharmacie", formatFcfa(payment.getNetAmount()), true);
        }

        doc.add(totals);
    }

    private void addPaymentInfo(Document doc, Payment payment) throws DocumentException {
        PdfPTable info = new PdfPTable(1);
        info.setWidthPercentage(100);
        info.setSpacingBefore(20);

        String method = switch (payment.getMethod()) {
            case WAVE         -> "Wave";
            case ORANGE_MONEY -> "Orange Money";
            case FREE_MONEY   -> "Free Money";
            case CARD         -> "Carte bancaire";
            case CASH         -> "Espèces";
        };

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(BRAND_LIGHT);
        cell.setPadding(10);
        cell.setBorderColor(BRAND_BLUE);
        cell.setBorderWidth(0.5f);
        cell.addElement(new Phrase("Paiement: " + method, FONT_LABEL));
        if (payment.getTransactionRef() != null) {
            cell.addElement(new Phrase("Référence transaction: " + payment.getTransactionRef(), FONT_VALUE));
        }
        if (payment.getPaidAt() != null) {
            cell.addElement(new Phrase("Date de paiement: " + DATE_FMT.format(payment.getPaidAt()), FONT_VALUE));
        }
        info.addCell(cell);
        doc.add(info);
    }

    private void addFooter(Document doc) throws DocumentException {
        Paragraph footer = new Paragraph(
            "Medoq — Dakar, Sénégal | noreply@medoq.sn | +221 33 XXX XX XX\n" +
            "Ce document est une facture officielle générée automatiquement.",
            FONT_FOOTER);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(30);
        doc.add(footer);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void addSeparator(Document doc) throws DocumentException {
        Paragraph sep = new Paragraph(" ");
        sep.setSpacingBefore(5);
        sep.setSpacingAfter(5);
        doc.add(sep);
        doc.add(new com.lowagie.text.pdf.draw.LineSeparator(0.5f, 100, BRAND_BLUE, Element.ALIGN_CENTER, -2));
    }

    private void addTableCell(PdfPTable table, String text, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_TD));
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(align);
        cell.setPadding(6);
        cell.setBorderColor(new Color(0xE5, 0xE7, 0xEB));
        table.addCell(cell);
    }

    private void addTotalRow(PdfPTable table, String label, String value, boolean bold) {
        Font labelFont = bold ? new Font(Font.HELVETICA, 11, Font.BOLD, DARK)  : FONT_VALUE;
        Font valueFont = bold ? FONT_TOTAL                                      : FONT_VALUE;

        PdfPCell lCell = new PdfPCell(new Phrase(label, labelFont));
        lCell.setBorder(Rectangle.NO_BORDER);
        lCell.setPadding(4);
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(value, valueFont));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        vCell.setPadding(4);
        table.addCell(vCell);
    }

    private String formatFcfa(BigDecimal amount) {
        if (amount == null) return "—";
        return String.format("%,.0f FCFA", amount.doubleValue());
    }
}
