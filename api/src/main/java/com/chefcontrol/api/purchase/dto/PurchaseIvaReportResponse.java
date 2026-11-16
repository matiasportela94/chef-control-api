package com.chefcontrol.api.purchase.dto;

import com.chefcontrol.domain.shared.valueobject.IvaRateTotal;
import com.chefcontrol.application.service.PurchaseService.IvaReport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * IVA de las compras de un período, sin las revertidas. {@code netTotal + ivaTotal = total}, y los tres cuentan
 * solo las compras con datos de IVA. Las anteriores (sin neto ni IVA) suman aparte en {@code withoutIvaData}, y en
 * {@code purchases} van igual, con neto e IVA en null, para que el Excel tenga todas las compras del período.
 */
public record PurchaseIvaReportResponse(
        Instant from,
        Instant to,
        BigDecimal netTotal,
        BigDecimal ivaTotal,
        BigDecimal total,
        List<IvaRateTotal> byRate,
        List<PurchaseRow> purchases,
        WithoutIvaData withoutIvaData
) {
    public static PurchaseIvaReportResponse from(IvaReport report) {
        return new PurchaseIvaReportResponse(report.from(), report.to(),
                report.netTotal(), report.ivaTotal(), report.total(), report.byRate(),
                report.purchases().stream().map(p -> new PurchaseRow(p.getId(), p.getPurchasedAt(), p.getSupplierName(),
                        p.getPricesIncludeIva(), p.getNetTotal(), p.getIvaTotal(), p.getTotal())).toList(),
                new WithoutIvaData(report.purchasesWithoutIvaData(), report.totalWithoutIvaData()));
    }

    /** De la más vieja a la más nueva. Neto, IVA y {@code pricesIncludeIva} en null si es anterior al IVA. */
    public record PurchaseRow(UUID id, Instant purchasedAt, String supplierName, Boolean pricesIncludeIva,
                              BigDecimal netTotal, BigDecimal ivaTotal, BigDecimal total) {}

    public record WithoutIvaData(int purchaseCount, BigDecimal total) {}
}
