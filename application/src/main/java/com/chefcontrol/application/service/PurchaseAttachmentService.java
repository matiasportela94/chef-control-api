package com.chefcontrol.application.service;

import com.chefcontrol.application.attachment.AttachmentFormat;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.purchase.PurchaseAttachment;
import com.chefcontrol.domain.repository.PurchaseAttachmentRepository;
import com.chefcontrol.domain.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La factura o el remito de una compra (F19, tanda 7): se sube después de confirmar la compra, desde el chat o desde el
 * formulario de Compras. Los bytes viven en la base, como las fotos de los platos.
 */
@Service
@RequiredArgsConstructor
public class PurchaseAttachmentService {

    public static final int MAX_BYTES = 4 * 1024 * 1024;
    /** Una factura de varias hojas son varias fotos; más que esto ya no es una factura. */
    static final int MAX_PER_PURCHASE = 10;

    private final PurchaseAttachmentRepository purchaseAttachmentRepository;
    private final PurchaseRepository purchaseRepository;
    private final Clock clock;

    @Transactional
    public PurchaseAttachment uploadPurchaseAttachment(UUID purchaseId, byte[] data, String filename) {
        requirePurchase(purchaseId);
        if (data == null || data.length == 0 || data.length > MAX_BYTES) {
            throw AppException.badRequest(ErrorCode.ATTACHMENT_TOO_LARGE, "An attachment can be up to 4 MB");
        }
        AttachmentFormat format = AttachmentFormat.detect(data).orElseThrow(() -> AppException.badRequest(
                ErrorCode.ATTACHMENT_FORMAT_NOT_SUPPORTED, "Only PDF, JPEG, PNG or WEBP"));
        if (purchaseAttachmentRepository.findByPurchaseId(purchaseId).size() >= MAX_PER_PURCHASE) {
            throw AppException.badRequest(ErrorCode.ATTACHMENT_TOO_LARGE, "A purchase can have up to 10 attachments");
        }
        String name = filename == null || filename.isBlank() ? null
                : filename.replaceAll("[\\r\\n\"]", "").substring(0, Math.min(filename.length(), 255));
        return purchaseAttachmentRepository.save(new PurchaseAttachment(null, purchaseId, TenantContext.require(),
                format.mediaType(), name, data.length, clock.instant()), data);
    }

    public List<PurchaseAttachment> listPurchaseAttachments(UUID purchaseId) {
        requirePurchase(purchaseId);
        return purchaseAttachmentRepository.findByPurchaseId(purchaseId);
    }

    /** El adjunto y sus bytes. */
    public Attachment getPurchaseAttachment(UUID purchaseId, UUID attachmentId) {
        requirePurchase(purchaseId);
        PurchaseAttachment a = purchaseAttachmentRepository.findByIdAndPurchaseId(attachmentId, purchaseId)
                .orElseThrow(PurchaseAttachmentService::notFound);
        return new Attachment(a, purchaseAttachmentRepository.findBytes(a.id()).orElseThrow(PurchaseAttachmentService::notFound));
    }

    @Transactional
    public void deletePurchaseAttachment(UUID purchaseId, UUID attachmentId) {
        requirePurchase(purchaseId);
        purchaseAttachmentRepository.findByIdAndPurchaseId(attachmentId, purchaseId)
                .orElseThrow(PurchaseAttachmentService::notFound);
        purchaseAttachmentRepository.delete(attachmentId);
    }

    public Map<UUID, Integer> countPurchaseAttachments(Collection<UUID> purchaseIds) {
        return purchaseIds.isEmpty() ? Map.of() : purchaseAttachmentRepository.countByPurchaseIds(purchaseIds);
    }

    private void requirePurchase(UUID purchaseId) {
        purchaseRepository.findByIdAndRestaurantId(purchaseId, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.PURCHASE_NOT_FOUND, "Purchase not found"));
    }

    private static AppException notFound() {
        return AppException.notFound(ErrorCode.ATTACHMENT_NOT_FOUND, "Attachment not found");
    }

    public record Attachment(PurchaseAttachment attachment, byte[] bytes) {}
}
