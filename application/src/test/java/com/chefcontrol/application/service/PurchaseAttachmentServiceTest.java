package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.purchase.Purchase;
import com.chefcontrol.domain.purchase.PurchaseAttachment;
import com.chefcontrol.domain.repository.PurchaseAttachmentRepository;
import com.chefcontrol.domain.repository.PurchaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseAttachmentServiceTest {

    @Mock PurchaseAttachmentRepository purchaseAttachmentRepository;
    @Mock PurchaseRepository purchaseRepository;

    private static final UUID R = UUID.randomUUID();
    private final UUID purchaseId = UUID.randomUUID();
    private PurchaseAttachmentService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(R);
        when(purchaseRepository.findByIdAndRestaurantId(purchaseId, R))
                .thenReturn(Optional.of(Purchase.builder().id(purchaseId).restaurantId(R).build()));
        when(purchaseAttachmentRepository.save(any(), any())).thenAnswer(i -> i.getArgument(0));
        service = new PurchaseAttachmentService(purchaseAttachmentRepository, purchaseRepository,
                Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void theTypeIsTheOneOfTheBytesNotTheFilename() {
        service.uploadPurchaseAttachment(purchaseId, "%PDF-1.4 factura".getBytes(StandardCharsets.US_ASCII), "foto.jpg");

        var saved = ArgumentCaptor.forClass(PurchaseAttachment.class);
        verify(purchaseAttachmentRepository).save(saved.capture(), any());
        assertEquals("application/pdf", saved.getValue().contentType());
        assertEquals("foto.jpg", saved.getValue().filename());
    }

    @Test
    void anSvgOrATooLargeFileIsRejected() {
        assertEquals(ErrorCode.ATTACHMENT_FORMAT_NOT_SUPPORTED, assertThrows(AppException.class, () ->
                service.uploadPurchaseAttachment(purchaseId, "<svg/>".getBytes(StandardCharsets.US_ASCII), "x.svg")).getErrorCode());
        assertEquals(ErrorCode.ATTACHMENT_TOO_LARGE, assertThrows(AppException.class, () ->
                service.uploadPurchaseAttachment(purchaseId, new byte[PurchaseAttachmentService.MAX_BYTES + 1], "x.pdf")).getErrorCode());
        verify(purchaseAttachmentRepository, never()).save(any(), any());
    }

    @Test
    void anotherRestaurantsPurchaseIsNotFound() {
        UUID foreign = UUID.randomUUID();
        assertEquals(ErrorCode.PURCHASE_NOT_FOUND, assertThrows(AppException.class, () ->
                service.listPurchaseAttachments(foreign)).getErrorCode());
    }
}
