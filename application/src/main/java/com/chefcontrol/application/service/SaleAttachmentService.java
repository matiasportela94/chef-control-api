package com.chefcontrol.application.service;

import com.chefcontrol.application.attachment.AttachmentFormat;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.sale.SaleAttachment;
import com.chefcontrol.domain.repository.SaleAttachmentRepository;
import com.chefcontrol.domain.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ponytail: copia de la de compras (tanda 7). Si aparece un tercer dueño de adjuntos, generalizar en una sola.
/**
 * El ticket Z o reporte de caja de una venta (F19, tanda 8): se sube después de confirmar la venta, desde el chat o desde el
 * formulario de Ventas. Los bytes viven en la base, como las fotos de los platos.
 */
@Service
@RequiredArgsConstructor
public class SaleAttachmentService {

    public static final int MAX_BYTES = 4 * 1024 * 1024;
    /** Un ticket largo son varias fotos; más que esto ya no es un ticket. */
    static final int MAX_PER_SALE = 10;

    private final SaleAttachmentRepository saleAttachmentRepository;
    private final SaleRepository saleRepository;
    private final Clock clock;

    @Transactional
    public SaleAttachment uploadSaleAttachment(UUID saleId, byte[] data, String filename) {
        requireSale(saleId);
        if (data == null || data.length == 0 || data.length > MAX_BYTES) {
            throw AppException.badRequest(ErrorCode.ATTACHMENT_TOO_LARGE, "An attachment can be up to 4 MB");
        }
        AttachmentFormat format = AttachmentFormat.detect(data).orElseThrow(() -> AppException.badRequest(
                ErrorCode.ATTACHMENT_FORMAT_NOT_SUPPORTED, "Only PDF, JPEG, PNG or WEBP"));
        if (saleAttachmentRepository.findBySaleId(saleId).size() >= MAX_PER_SALE) {
            throw AppException.badRequest(ErrorCode.ATTACHMENT_TOO_LARGE, "A sale can have up to 10 attachments");
        }
        String name = filename == null || filename.isBlank() ? null
                : filename.replaceAll("[\\r\\n\"]", "").substring(0, Math.min(filename.length(), 255));
        return saleAttachmentRepository.save(new SaleAttachment(null, saleId, TenantContext.require(),
                format.mediaType(), name, data.length, clock.instant()), data);
    }

    public List<SaleAttachment> listSaleAttachments(UUID saleId) {
        requireSale(saleId);
        return saleAttachmentRepository.findBySaleId(saleId);
    }

    /** El adjunto y sus bytes. */
    public Attachment getSaleAttachment(UUID saleId, UUID attachmentId) {
        requireSale(saleId);
        SaleAttachment a = saleAttachmentRepository.findByIdAndSaleId(attachmentId, saleId)
                .orElseThrow(SaleAttachmentService::notFound);
        return new Attachment(a, saleAttachmentRepository.findBytes(a.id()).orElseThrow(SaleAttachmentService::notFound));
    }

    @Transactional
    public void deleteSaleAttachment(UUID saleId, UUID attachmentId) {
        requireSale(saleId);
        saleAttachmentRepository.findByIdAndSaleId(attachmentId, saleId)
                .orElseThrow(SaleAttachmentService::notFound);
        saleAttachmentRepository.delete(attachmentId);
    }


    private void requireSale(UUID saleId) {
        saleRepository.findByIdAndRestaurantId(saleId, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.SALE_NOT_FOUND, "Sale not found"));
    }

    private static AppException notFound() {
        return AppException.notFound(ErrorCode.ATTACHMENT_NOT_FOUND, "Attachment not found");
    }

    public record Attachment(SaleAttachment attachment, byte[] bytes) {}
}
