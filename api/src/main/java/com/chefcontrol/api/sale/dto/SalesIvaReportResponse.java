package com.chefcontrol.api.sale.dto;

import com.chefcontrol.application.service.SaleService.SalesIvaReport;
import com.chefcontrol.domain.shared.valueobject.IvaRateTotal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * IVA de las ventas de un período (débito fiscal), sin las revertidas. Misma forma que el de compras para que el
 * front calcule el saldo. {@code netTotal + ivaTotal = total}, y los tres cuentan solo las ventas con datos de IVA;
 * las anteriores (sin alícuota) suman aparte en {@code withoutIvaData} y van igual en {@code sales}, con alícuota,
 * neto e IVA en null. Una venta de un restaurante que no es RI tiene alícuota 0: neto = total.
 *
 * <p>Los records anidados tienen nombre propio ({@code SaleIvaRow}, {@code SalesWithoutIvaData}) porque el nombre
 * del record es el del schema del spec: dos {@code WithoutIvaData} distintos chocarían con el de compras.
 */
public record SalesIvaReportResponse(
        Instant from,
        Instant to,
        BigDecimal netTotal,
        BigDecimal ivaTotal,
        BigDecimal total,
        List<IvaRateTotal> byRate,
        List<SaleIvaRow> sales,
        SalesWithoutIvaData withoutIvaData
) {
    public static SalesIvaReportResponse from(SalesIvaReport report) {
        return new SalesIvaReportResponse(report.from(), report.to(),
                report.netTotal(), report.ivaTotal(), report.total(), report.byRate(),
                report.sales().stream().map(s -> new SaleIvaRow(s.getId(), s.getSoldAt(), s.getIvaPercentage(),
                        s.getNetTotal(), s.getIvaTotal(), s.getTotalAmount())).toList(),
                new SalesWithoutIvaData(report.salesWithoutIvaData(), report.totalWithoutIvaData()));
    }

    /** De la más vieja a la más nueva. Alícuota, neto e IVA en null si es anterior al IVA. */
    public record SaleIvaRow(UUID id, Instant soldAt, BigDecimal ivaPercentage,
                             BigDecimal netTotal, BigDecimal ivaTotal, BigDecimal total) {}

    public record SalesWithoutIvaData(int saleCount, BigDecimal total) {}
}
