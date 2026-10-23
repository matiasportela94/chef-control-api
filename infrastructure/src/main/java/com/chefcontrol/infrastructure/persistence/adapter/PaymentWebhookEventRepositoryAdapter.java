package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.payments.PaymentWebhookEvent;
import com.chefcontrol.domain.payments.PaymentWebhookEventRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentWebhookEventRepositoryAdapter implements PaymentWebhookEventRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * SQL nativo a propósito: {@code ON CONFLICT DO NOTHING} decide "nuevo o repetido" en un solo paso
     * atómico. Capturar la violación de unicidad con JPA dejaría la transacción marcada para rollback.
     */
    @Override
    public boolean saveIfNew(PaymentWebhookEvent event) {
        int inserted = em.createNativeQuery("""
                        INSERT INTO payment_webhook_events (provider, event_key, topic, data_id, payload)
                        VALUES (:provider, :eventKey, :topic, :dataId, :payload)
                        ON CONFLICT (provider, event_key) DO NOTHING
                        """)
                .setParameter("provider", event.provider())
                .setParameter("eventKey", event.eventKey())
                .setParameter("topic", event.topic())
                .setParameter("dataId", event.dataId())
                .setParameter("payload", event.payload() == null ? "" : event.payload())
                .executeUpdate();
        return inserted == 1;
    }
}
