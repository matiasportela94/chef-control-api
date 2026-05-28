package com.chefcontrol.domain.restaurant;

import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.domain.plan.PlanTier;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.text.Normalizer;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class Restaurant {

    private UUID id;
    private String name;
    private String slug;
    private String timezone = "America/Argentina/Buenos_Aires";
    private PlanTier plan = PlanTier.TRIAL;
    private boolean isActive = true;
    private Instant createdAt;

    public boolean hasFeature(Feature feature) {
        return plan.hasFeature(feature);
    }

    /**
     * Generates a URL-safe slug from a restaurant name.
     * Strips diacritics, lowercases, removes special chars, and replaces spaces with hyphens.
     * Uniqueness enforcement is the caller's responsibility.
     */
    public static String generateBaseSlug(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
    }
}
