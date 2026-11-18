package com.chefcontrol.application.port;

import com.chefcontrol.application.service.CatalogImportService.PreparationRow;
import com.chefcontrol.application.service.CatalogImportService.ProductRow;
import com.chefcontrol.application.service.CatalogImportService.RecipeRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AIService {

    AIInterpretation interpret(InterpretRequest request);

    /**
     * Traduce una planilla de catálogo armada como sea a las filas de /catalog-import. No escribe nada: lo que
     * devuelve pasa después por la vista previa (dryRun) y la confirmación de siempre. Lanza
     * {@code AI_SPREADSHEET_TOO_LARGE} si la salida no entra en el máximo del modelo y
     * {@code AI_INTERPRETATION_FAILED} si el modelo no responde o no se entiende lo que respondió.
     */
    CatalogSpreadsheetInterpretation interpretCatalogSpreadsheet(CatalogSpreadsheetRequest request);

    /**
     * Un mensaje del chat (escrito o dictado) → las operaciones que pide. No registra nada: qué se registra, qué se
     * pregunta y qué se rechaza lo decide {@code ChatService}. Lanza {@code AI_INTERPRETATION_FAILED} si el modelo no
     * responde o no se entiende lo que respondió.
     */
    ChatInterpretation interpretChatMessage(ChatMessageRequest request);

    /**
     * La segunda llamada de un mensaje de receta: la receta completa de un plato o una preparación, con lo que pide el
     * mensaje aplicado sobre la actual ("sacale la crema" devuelve todos los ingredientes menos la crema). Sale en las
     * filas de /catalog-import, con {@code row} = la posición del ingrediente (1, 2, 3…), y va a la vista previa del
     * import: no escribe nada. Lanza {@code AI_INTERPRETATION_FAILED} si el modelo no responde o no se le entiende.
     */
    CatalogSpreadsheetInterpretation interpretRecipeMessage(RecipeMessageRequest request);

    /**
     * Una factura o un remito (fotos de sus hojas o un PDF) → lo que tiene impreso, tal cual: el modelo no hace cuentas
     * ni elige el insumo (sugiere uno del catálogo, que el back valida). Con el modelo de imágenes, que se configura
     * aparte del de texto. Lanza {@code AI_INTERPRETATION_FAILED} si el modelo no responde o no se le entiende.
     */
    InvoiceReading interpretInvoiceDocument(InvoiceDocumentRequest request);

    record DocumentPage(byte[] bytes, String mediaType) {}

    /** @param message lo que escribió el usuario junto con el archivo; puede ser null */
    record InvoiceDocumentRequest(List<DocumentPage> pages, String message, List<ChatCatalogProduct> products,
                                  List<UnitOption> units) {}

    /**
     * @param invoice     false si el archivo no es una factura, un ticket de compra o un remito
     * @param invoiceType A, B, C o REMITO, como está impreso; null si no se ve
     */
    record InvoiceReading(boolean invoice, String supplierName, String supplierCuit, String invoiceType,
                          String invoiceNumber, LocalDate issuedOn, BigDecimal printedTotal, List<InvoiceLineReading> lines) {}

    /**
     * Un renglón tal como está impreso.
     *
     * @param unit          una abreviatura de la lista de unidades si el renglón la dice; null si no
     * @param catalogName   el insumo del catálogo que el modelo reconoce; el back verifica que exista
     * @param suggestedName un nombre limpio, para proponer el insumo si no está en el catálogo
     */
    record InvoiceLineReading(String printedText, BigDecimal quantity, String unit, BigDecimal unitPrice,
                              BigDecimal lineTotal, BigDecimal ivaPercentage, String catalogName, String suggestedName) {}

    /**
     * @param today     la fecha argentina, para "vence el viernes"
     * @param products  los insumos activos del restaurante
     * @param menuItems los platos activos, para las ventas
     */
    record ChatMessageRequest(String message, LocalDate today, List<ChatCatalogProduct> products,
                              List<ChatCatalogMenuItem> menuItems, List<UnitOption> units, List<ChatHistoryTurn> history) {
        public ChatMessageRequest(String message, LocalDate today, List<ChatCatalogProduct> products,
                                  List<ChatCatalogMenuItem> menuItems, List<UnitOption> units) {
            this(message, today, products, menuItems, units, List.of());
        }
    }

    /**
     * Un intercambio anterior de la conversación de hoy, para que el modelo entienda "eran 5", "sí" o "el total".
     *
     * @param userMessage null si fue un "Confirmar"
     * @param actions     lo que se registró, numerado: el modelo nombra la acción que corrige por su número
     */
    record ChatHistoryTurn(String userMessage, String reply, List<HistoryAction> actions) {}

    record HistoryAction(int number, String summary, boolean undone) {}

    /**
     * @param unit        la abreviatura de su unidad por defecto
     * @param preparation tiene receta de tanda: es lo único que se puede producir
     */
    record ChatCatalogProduct(UUID id, String name, String unit, boolean preparation) {}

    record ChatCatalogMenuItem(UUID id, String name, BigDecimal price) {}

    /**
     * @param recipeName  el nombre con el que sale la receta: el del catálogo si ya existe, así el import la reconoce
     * @param preparation receta de tanda de un insumo (con rendimiento); si no, la de un plato (por porción)
     * @param current     la receta de hoy, null si no tiene
     * @param products    el catálogo, para reconocer los ingredientes (no lleva ids: el import busca por nombre)
     */
    record RecipeMessageRequest(String message, String recipeName, boolean preparation, CurrentRecipe current,
                                List<ChatCatalogProduct> products, List<UnitOption> units) {}

    /**
     * @param yieldQuantity solo en una preparación: cuánto rinde la tanda, en {@code yieldUnit}
     * @param ingredients   en un plato, por porción
     */
    record CurrentRecipe(BigDecimal yieldQuantity, String yieldUnit, List<RecipeLine> ingredients) {}

    /** @param unit la abreviatura */
    record RecipeLine(String ingredient, BigDecimal quantity, String unit) {}

    /** @param needsConfirmation lo que dice la IA; ChatService además aplica sus propias reglas */
    record ChatInterpretation(int confidence, boolean needsConfirmation, String responseToUser,
                              List<InterpretedOperation> operations) {}

    /**
     * @param type purchase, waste, sale, stock_adjustment, production, recipe, preparation_recipe o query: viene aunque
     *             no tenga ejecución
     * @param text     el pedazo del mensaje que pide esta operación. Una receta recibe solo el suyo: con el mensaje
     *                 entero, la compra de un mensaje mixto se colaba como ingrediente
     * @param corrects el número de la acción de la conversación que esta operación corrige ("no, eran 5 kg"); null si
     *                 no corrige nada
     */
    record InterpretedOperation(String type, List<InterpretedItem> items, String text, Integer corrects,
                                InterpretedQuery query) {
        public InterpretedOperation(String type, List<InterpretedItem> items) {
            this(type, items, null, null, null);
        }

        public InterpretedOperation(String type, List<InterpretedItem> items, String text) {
            this(type, items, text, null, null);
        }

        public InterpretedOperation(String type, List<InterpretedItem> items, String text, Integer corrects) {
            this(type, items, text, corrects, null);
        }
    }

    /**
     * Una consulta de la lista fija, con un período con nombre: el modelo no cuenta días ("el lunes de la semana pasada",
     * fin de mes, año nuevo); las fechas las calcula el back. Solo en {@code range} vienen fechas: las que dijo el usuario.
     *
     * @param name   stock, expirations, sales, purchases, waste, last_purchase_price, food_cost u open_alerts
     * @param period today, yesterday, this_week, last_week, this_month, last_month, last_n_days o range; null = no dijo
     * @param days   en last_n_days
     */
    record InterpretedQuery(String name, String period, Integer days, LocalDate from, LocalDate to) {}

    /**
     * Cantidad, unidad y precio tal como los dijo el usuario: las cuentas las hace el back, no la IA. Con "500 g de
     * comino" y el comino en kg, una conversión mal hecha por el modelo entraría 500 kg sin aviso; y con "5 kg a 150 mil"
     * el modelo llegó a dividir por su cuenta y la compra entró a $30.000 el kg.
     *
     * @param productId  el insumo del catálogo; null si no es ninguno o el modelo duda
     * @param menuItemId el plato, en una venta; null si no es ninguno o el modelo duda
     * @param name       como lo dijo el usuario
     * @param quantity   en una venta, porciones; en un ajuste, lo que hay ahora (no la diferencia)
     * @param unit       una abreviatura de la lista de unidades; null si no dijo ninguna
     * @param price      el número tal como lo dijo, sin cuentas
     * @param priceBasis a qué se refiere ese número: unit (por unidad), total o unclear. Lo clasifica la IA; la división
     *                   de un total la hace ChatService
     * @param priceUnit  la unidad de un precio por unidad, si la dijo ("a 3000 el kilo"); null = la de la cantidad
     */
    record InterpretedItem(UUID productId, UUID menuItemId, String name, BigDecimal quantity, String unit,
                           BigDecimal price, String priceBasis, String priceUnit, LocalDate expirationDate,
                           String wasteReason) {}

    record InterpretRequest(
        String message,
        UUID restaurantId,
        UUID sessionId,
        String catalog
    ) {}

    record AIInterpretation(
        String intent,
        int confidence,
        boolean needsConfirmation,
        Object extractedData,
        String responseToUser
    ) {}

    /** Lo que ya existe va para que el modelo reconozca qué es cada cosa, no para renombrar. */
    record CatalogSpreadsheetRequest(
        List<SpreadsheetSheet> sheets,
        List<UnitOption> units,
        List<String> productNames,
        List<String> menuItemNames,
        List<String> sectionNames
    ) {}

    record SpreadsheetSheet(String name, List<SpreadsheetRow> rows) {}

    /** @param ref número global de la fila que pone el front, para volver a la hoja y fila originales */
    record SpreadsheetRow(int ref, List<Object> cells) {}

    record UnitOption(String name, String abbreviation) {}

    /**
     * {@code row} de cada fila = el {@code ref} de la fila de origen.
     *
     * @param proposedProducts insumos que arma la IA porque una receta los usa y no existen ni en el catálogo ni en
     *                         las filas de insumos; {@code row} = la primera fila de receta que los usa. El front los
     *                         muestra como propuestos y recién los suma a {@code products} para la vista previa.
     * @param ingredientMatches  ingredientes que el modelo reconoce como un insumo existente escrito de otra forma
     *                           ("Tomates" → "Tomate"). Los aplica el servicio, que verifica que el nombre exista;
     *                           lo que devuelve el servicio ya los trae aplicados y esta lista vacía.
     */
    record CatalogSpreadsheetInterpretation(
        List<ProductRow> products,
        List<PreparationRow> preparations,
        List<RecipeRow> recipes,
        List<ProductRow> proposedProducts,
        List<IngredientMatch> ingredientMatches,
        List<String> warnings
    ) {}

    /**
     * @param row la fila de receta (o de preparación); {@code writtenName} como vino, {@code catalogName} el insumo que el
     *            modelo reconoce
     */
    record IngredientMatch(int row, String writtenName, String catalogName) {}
}
