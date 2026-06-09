package com.chefcontrol.application.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    INVALID_CREDENTIALS     (Category.FORBIDDEN),
    USER_NOT_FOUND          (Category.NOT_FOUND),
    USER_INACTIVE           (Category.FORBIDDEN),
    NO_ACTIVE_MEMBERSHIPS   (Category.FORBIDDEN),
    INVALID_TOKEN           (Category.BAD_REQUEST),
    TOKEN_EXPIRED           (Category.BAD_REQUEST),
    TOKEN_ALREADY_USED      (Category.BAD_REQUEST),

    // Stock movements
    MOVEMENT_ALREADY_REVERSED   (Category.CONFLICT),
    MOVEMENT_CANNOT_BE_REVERSED (Category.BAD_REQUEST),
    STOCK_COUNT_NOT_FOUND       (Category.NOT_FOUND),
    ALERT_NOT_FOUND             (Category.NOT_FOUND),

    // Restaurant access
    RESTAURANT_NOT_FOUND    (Category.NOT_FOUND),
    RESTAURANT_ACCESS_DENIED(Category.FORBIDDEN),
    RESTAURANT_INACTIVE     (Category.FORBIDDEN),

    // WhatsApp
    UNREGISTERED_PHONE      (Category.FORBIDDEN),

    // Resources
    SUPPLIER_NOT_FOUND      (Category.NOT_FOUND),
    PURCHASE_NOT_FOUND      (Category.NOT_FOUND),
    PRODUCT_NOT_FOUND       (Category.NOT_FOUND),
    UNIT_NOT_FOUND          (Category.NOT_FOUND),
    CATEGORY_NOT_FOUND      (Category.NOT_FOUND),
    MOVEMENT_NOT_FOUND      (Category.NOT_FOUND),
    WASTE_EVENT_NOT_FOUND   (Category.NOT_FOUND),
    RECIPE_NOT_FOUND        (Category.NOT_FOUND),
    MENU_ITEM_NOT_FOUND     (Category.NOT_FOUND),
    MENU_ITEM_INACTIVE      (Category.BAD_REQUEST),
    SALE_NOT_FOUND          (Category.NOT_FOUND),

    // Business rules
    UNIT_CONVERSION_NOT_FOUND  (Category.BAD_REQUEST),
    MISSING_PURCHASE_COST      (Category.BAD_REQUEST),
    INSUFFICIENT_STOCK      (Category.UNPROCESSABLE),
    INVALID_EXPIRATION_DATE (Category.BAD_REQUEST),
    DUPLICATE_EMAIL         (Category.CONFLICT),
    DUPLICATE_PHONE         (Category.CONFLICT),
    DUPLICATE_SLUG          (Category.CONFLICT),
    DUPLICATE_SKU           (Category.CONFLICT),
    CATEGORY_HAS_PRODUCTS       (Category.CONFLICT),
    SYSTEM_CATEGORY_IMMUTABLE   (Category.FORBIDDEN),

    // Input
    VALIDATION_ERROR        (Category.BAD_REQUEST),

    // Generic
    NOT_FOUND               (Category.NOT_FOUND),
    FORBIDDEN               (Category.FORBIDDEN),
    UNPROCESSABLE           (Category.UNPROCESSABLE),
    INTERNAL_ERROR          (Category.INTERNAL);

    private final Category category;

    public enum Category {
        NOT_FOUND,
        FORBIDDEN,
        CONFLICT,
        BAD_REQUEST,
        UNPROCESSABLE,
        INTERNAL
    }
}
