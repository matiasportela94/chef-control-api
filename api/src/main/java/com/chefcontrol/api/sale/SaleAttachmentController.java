package com.chefcontrol.api.sale;

import com.chefcontrol.application.service.SaleAttachmentService;
import com.chefcontrol.domain.sale.SaleAttachment;
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

// ponytail: copia de la de compras (tanda 7). Si aparece un tercer dueño de adjuntos, generalizar en una sola.
/**
 * El ticket Z o reporte de caja de una venta (PDF, JPEG, PNG o WEBP, por magic bytes). Se sirve con {@code nosniff} y, el PDF,
 * con {@code CSP sandbox}: un PDF puede traer scripts y lo servimos desde nuestro dominio.
 */
@RestController
@RequestMapping("/sales/{saleId}/attachments")
@RequiredArgsConstructor
public class SaleAttachmentController {

    private final SaleAttachmentService saleAttachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_SALES_CREATE')")
    public ResponseEntity<SaleAttachmentResponse> uploadSaleAttachment(@PathVariable UUID saleId,
                                                                               @RequestParam("file") MultipartFile file)
            throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(SaleAttachmentResponse.from(
                saleAttachmentService.uploadSaleAttachment(saleId, file.getBytes(), file.getOriginalFilename())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_SALES_VIEW')")
    public ResponseEntity<List<SaleAttachmentResponse>> listSaleAttachments(@PathVariable UUID saleId) {
        return ResponseEntity.ok(saleAttachmentService.listSaleAttachments(saleId).stream()
                .map(SaleAttachmentResponse::from).toList());
    }

    @GetMapping("/{attachmentId}")
    @PreAuthorize("hasAuthority('PERM_SALES_VIEW')")
    public ResponseEntity<byte[]> getSaleAttachment(@PathVariable UUID saleId, @PathVariable UUID attachmentId) {
        var found = saleAttachmentService.getSaleAttachment(saleId, attachmentId);
        SaleAttachment a = found.attachment();
        var response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(a.filename() == null ? "ticket" : a.filename(), StandardCharsets.UTF_8).build().toString());
        if (MediaType.APPLICATION_PDF_VALUE.equals(a.contentType())) {
            response.header("Content-Security-Policy", "sandbox");
        }
        return response.body(found.bytes());
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAuthority('PERM_SALES_CREATE')")
    public ResponseEntity<Void> deleteSaleAttachment(@PathVariable UUID saleId, @PathVariable UUID attachmentId) {
        saleAttachmentService.deleteSaleAttachment(saleId, attachmentId);
        return ResponseEntity.noContent().build();
    }

    public record SaleAttachmentResponse(UUID id, String contentType, String filename, int sizeBytes,
                                             Instant createdAt) {
        static SaleAttachmentResponse from(SaleAttachment a) {
            return new SaleAttachmentResponse(a.id(), a.contentType(), a.filename(), a.sizeBytes(), a.createdAt());
        }
    }
}
