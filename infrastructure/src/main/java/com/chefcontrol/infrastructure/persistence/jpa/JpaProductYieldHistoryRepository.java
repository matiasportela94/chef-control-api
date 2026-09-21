package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.ProductYieldHistoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Solo el save heredado: las lecturas de la serie se agregan cuando haya quien las llame.
 * El índice (product_id, valid_from DESC) ya está en V22 esperándolas.
 */
public interface JpaProductYieldHistoryRepository extends JpaRepository<ProductYieldHistoryJpaEntity, UUID> {
}
