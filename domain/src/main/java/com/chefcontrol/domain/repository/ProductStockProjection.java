package com.chefcontrol.domain.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface ProductStockProjection {
    UUID getId();
    String getName();
    String getSku();
    String getUnitAbbreviation();
    BigDecimal getCurrentStock();
    BigDecimal getMinStock();
    BigDecimal getMaxStock();
}
