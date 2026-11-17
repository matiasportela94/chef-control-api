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
    GOOGLE_TOKEN_INVALID    (Category.FORBIDDEN),
    GOOGLE_ACCOUNT_NOT_REGISTERED (Category.NOT_FOUND),
    GOOGLE_ACCOUNT_ALREADY_LINKED (Category.CONFLICT),   // esa cuenta de Google es de otro usuario
    USER_ALREADY_LINKED_TO_GOOGLE (Category.CONFLICT),   // el usuario ya tiene otra cuenta de Google vinculada
    GOOGLE_ACCOUNT_MISMATCH       (Category.CONFLICT),   // al desvincular: el token no es de la cuenta vinculada

    // Stock movements
    MOVEMENT_ALREADY_REVERSED   (Category.CONFLICT),
    MOVEMENT_CANNOT_BE_REVERSED (Category.BAD_REQUEST),
    STOCK_COUNT_NOT_FOUND       (Category.NOT_FOUND),
    ALERT_NOT_FOUND             (Category.NOT_FOUND),
    ROLE_NOT_FOUND              (Category.NOT_FOUND),
    DUPLICATE_ROLE_NAME         (Category.CONFLICT),
    SYSTEM_ROLE_IMMUTABLE       (Category.FORBIDDEN),
    ROLE_IN_USE                 (Category.CONFLICT),
    PLAN_LIMIT_REACHED          (Category.FORBIDDEN),
    PLAN_FEATURE_REQUIRED       (Category.FORBIDDEN),

    // Restaurant access
    RESTAURANT_NOT_FOUND    (Category.NOT_FOUND),
    RESTAURANT_ACCESS_DENIED(Category.FORBIDDEN),
    RESTAURANT_INACTIVE     (Category.FORBIDDEN),
    CANNOT_MODIFY_CURRENT_RESTAURANT(Category.CONFLICT),
    NOT_ACCOUNT_OWNER       (Category.FORBIDDEN),

    // Payments
    SUBSCRIPTION_NOT_FOUND      (Category.NOT_FOUND),
    SUBSCRIPTION_ALREADY_ACTIVE (Category.CONFLICT),
    INVALID_SUBSCRIPTION_PLAN   (Category.BAD_REQUEST),
    SUBSCRIPTION_NOT_ACTIVE     (Category.CONFLICT),
    SUBSCRIPTION_PAST_DUE       (Category.CONFLICT),
    SUBSCRIPTION_CHANGE_SAME_PLAN          (Category.BAD_REQUEST),
    SUBSCRIPTION_UPGRADE_AND_PERIOD_CHANGE (Category.BAD_REQUEST),
    SUBSCRIPTION_CHANGE_NOT_FOUND          (Category.NOT_FOUND),
    SUBSCRIPTION_CHANGE_IN_PROGRESS        (Category.CONFLICT),
    // INTERNAL (500) y no 502/503: el front manda esos dos a la pantalla de "sistema caído", y que
    // MercadoPago no responda no es que el sistema esté caído.
    PAYMENT_PROVIDER_UNAVAILABLE(Category.INTERNAL),
    // IA: INTERNAL (500) y no 503 a propósito, como PAYMENT_PROVIDER_UNAVAILABLE: el front manda 502/503 a /maintenance
    AI_INTERPRETATION_FAILED    (Category.INTERNAL),
    AI_SPREADSHEET_TOO_LARGE    (Category.BAD_REQUEST),

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
    CARTA_NOT_FOUND         (Category.NOT_FOUND),
    MENU_SECTION_NOT_FOUND  (Category.NOT_FOUND),
    IMAGE_EMPTY                 (Category.BAD_REQUEST),
    IMAGE_TOO_LARGE             (Category.BAD_REQUEST),
    IMAGE_FORMAT_NOT_SUPPORTED  (Category.BAD_REQUEST),
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
    INVALID_IVA_PERCENTAGE  (Category.BAD_REQUEST),     // fuera de 0, 2.5, 5, 10.5, 21, 27 (IvaRates)
    DUPLICATE_CARTA_NAME    (Category.CONFLICT),
    DUPLICATE_MENU_SECTION_NAME (Category.CONFLICT),
    MENU_SECTION_HAS_ITEMS      (Category.CONFLICT),
    CATEGORY_HAS_PRODUCTS       (Category.CONFLICT),
    SYSTEM_CATEGORY_IMMUTABLE   (Category.FORBIDDEN),

    // Input
    VALIDATION_ERROR        (Category.BAD_REQUEST),

    // Importación de catálogo: no se lanzan, van como errores de fila en el resultado de /catalog-import
    IMPORT_REQUIRED_FIELD       (Category.BAD_REQUEST),
    IMPORT_INVALID_VALUE        (Category.BAD_REQUEST),
    IMPORT_UNKNOWN_UNIT         (Category.BAD_REQUEST),
    IMPORT_UNKNOWN_INGREDIENT   (Category.BAD_REQUEST),
    IMPORT_INCOMPATIBLE_UNIT    (Category.BAD_REQUEST),
    IMPORT_DUPLICATE_PRODUCT    (Category.BAD_REQUEST),
    IMPORT_DUPLICATE_INGREDIENT (Category.BAD_REQUEST),
    IMPORT_AMBIGUOUS_NAME       (Category.BAD_REQUEST),      // el nombre matchea con dos o más del catálogo
    IMPORT_CATEGORY_CREATE_FORBIDDEN (Category.BAD_REQUEST), // haría falta crear la categoría y no hay CATEGORIES_CREATE

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
