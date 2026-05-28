package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.product.Unit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnitRepository {

    List<Unit> findAll();

    Optional<Unit> findById(UUID id);
}
