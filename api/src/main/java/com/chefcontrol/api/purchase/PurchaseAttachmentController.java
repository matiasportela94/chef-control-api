package com.chefcontrol.api.purchase;

import com.chefcontrol.application.service.PurchaseAttachmentService;
import com.chefcontrol.domain.purchase.PurchaseAttachment;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La factura o el remito de una compra (PDF, JPEG, PNG o WEBP, por magic bytes). Se sirve con {@code nosniff} y, el PDF,
 * con {@code CSP sandbox}: un PDF puede traer scripts y lo servimos desde nuestro dominio.
 */
@RestController
@RequestMapping("/purchases/{purchaseId}/attachments")
@RequiredArgsConstructor
public class PurchaseAttachmentController {

    private final PurchaseAttachmentService purchaseAttachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_PURCHASES_CREATE')")
    public ResponseEntity<PurchaseAttachmentResponse> uploadPurchaseAttachment(@PathVariable UUID purchaseId,
                                                                               @RequestParam("file") MultipartFile file)
            throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(PurchaseAttachmentResponse.from(
                purchaseAttachmentService.uploadPurchaseAttachment(purchaseId, file.getBytes(), file.getOriginalFilename())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_PURCHASES_VIEW')")
    public ResponseEntity<List<PurchaseAttachmentResponse>> listPurchaseAttachments(@PathVariable UUID purchaseId) {
        return ResponseEntity.ok(purchaseAttachmentService.listPurchaseAttachments(purchaseId).stream()
                .map(PurchaseAttachmentResponse::from).toList());
    }

    @GetMapping("/{attachmentId}")
    @PreAuthorize("hasAuthority('PERM_PURCHASES_VIEW')")
    public ResponseEntity<byte[]> getPurchaseAttachment(@PathVariable UUID purchaseId, @PathVariable UUID attachmentId) {
        var found = purchaseAttachmentService.getPurchaseAttachment(purchaseId, attachmentId);
        PurchaseAttachment a = found.attachment();
        var response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(a.filename() == null ? "factura" : a.filename(), StandardCharsets.UTF_8).build().toString());
        if (MediaType.APPLICATION_PDF_VALUE.equals(a.contentType())) {
            response.header("Content-Security-Policy", "sandbox");
        }
        return response.body(found.bytes());
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAuthority('PERM_PURCHASES_CREATE')")
    public ResponseEntity<Void> deletePurchaseAttachment(@PathVariable UUID purchaseId, @PathVariable UUID attachmentId) {
        purchaseAttachmentService.deletePurchaseAttachment(purchaseId, attachmentId);
        return ResponseEntity.noContent().build();
    }

    public record PurchaseAttachmentResponse(UUID id, String contentType, String filename, int sizeBytes,
                                             Instant createdAt) {
        static PurchaseAttachmentResponse from(PurchaseAttachment a) {
            return new PurchaseAttachmentResponse(a.id(), a.contentType(), a.filename(), a.sizeBytes(), a.createdAt());
        }
    }
}
