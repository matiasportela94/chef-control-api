package com.chefcontrol.domain.plan;

import java.util.EnumSet;
import java.util.Set;

public enum PlanTier {

    TRIAL(EnumSet.of(
            Feature.STOCK_MOVEMENTS,
            Feature.PRODUCT_CATALOG,
            Feature.BASIC_REPORTS
    )),

    STARTER(EnumSet.of(
            Feature.STOCK_MOVEMENTS,
            Feature.PRODUCT_CATALOG,
            Feature.BASIC_REPORTS,
            Feature.WHATSAPP_INTEGRATION,
            Feature.AI_INTERPRETER,
            Feature.MULTI_USER
    )),

    PRO(EnumSet.of(
            Feature.STOCK_MOVEMENTS,
            Feature.PRODUCT_CATALOG,
            Feature.BASIC_REPORTS,
            Feature.WHATSAPP_INTEGRATION,
            Feature.AI_INTERPRETER,
            Feature.MULTI_USER,
            Feature.RECIPES_AND_FOOD_COST,
            Feature.ADVANCED_REPORTS,
            Feature.CUSTOM_ALERTS,
            Feature.MULTI_RESTAURANT
    )),

    ENTERPRISE(EnumSet.allOf(Feature.class));

    private final Set<Feature> features;

    PlanTier(Set<Feature> features) {
        this.features = features;
    }

    public boolean hasFeature(Feature feature) {
        return features.contains(feature);
    }

    public Set<Feature> getFeatures() {
        return Set.copyOf(features);
    }
}
