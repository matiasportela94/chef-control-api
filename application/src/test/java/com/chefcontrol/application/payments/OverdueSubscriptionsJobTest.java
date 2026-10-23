package com.chefcontrol.application.payments;

import com.chefcontrol.domain.payments.Subscription;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class OverdueSubscriptionsJobTest {

    @Test
    void oneFailureDoesNotStopTheRest() {
        SubscriptionService service = mock(SubscriptionService.class);
        Subscription failing = Subscription.builder().id(UUID.randomUUID()).build();
        Subscription next = Subscription.builder().id(UUID.randomUUID()).build();
        when(service.findOverdueSubscriptions()).thenReturn(List.of(failing, next));
        doThrow(new PaymentProviderException("down")).when(service).collectOverdueSubscription(failing);

        new OverdueSubscriptionsJob(service).collectOverdueSubscriptions();

        verify(service).collectOverdueSubscription(next);
    }
}
