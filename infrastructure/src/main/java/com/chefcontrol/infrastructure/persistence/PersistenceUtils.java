package com.chefcontrol.infrastructure.persistence;

import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import org.springframework.data.domain.Sort;

public final class PersistenceUtils {

    private PersistenceUtils() {}

    public static org.springframework.data.domain.PageRequest toSpring(PageRequest req) {
        return org.springframework.data.domain.PageRequest.of(req.page(), req.size());
    }

    public static org.springframework.data.domain.PageRequest toSpring(PageRequest req, Sort sort) {
        return org.springframework.data.domain.PageRequest.of(req.page(), req.size(), sort);
    }

    public static <T> Page<T> toDomain(org.springframework.data.domain.Page<T> springPage) {
        return new Page<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements()
        );
    }
}
