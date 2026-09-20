-- ============================================================
-- V15: borrado duro de un restaurante = borra TODA su data.
--
-- Hasta acá ninguna FK a restaurants tenía ON DELETE (Postgres default = NO ACTION),
-- así que un DELETE FROM restaurants fallaba contra la primera tabla hija. Para que
-- el borrado sea realmente total hace falta cascada en todo el subgrafo que cuelga
-- de restaurants, no solo en las ~21 tablas con restaurant_id: purchase_items,
-- sale_items, recipe_items y stock_movement_batch_allocations no tienen restaurant_id
-- y cuelgan de sus padres.
--
-- En vez de escribir ~50 ALTER a mano (fáciles de dejar incompletos), se calcula el
-- cierre transitivo de dependientes de restaurants desde pg_constraint y se reescribe
-- cada FK del subgrafo con ON DELETE CASCADE.
--
-- audit_log queda afuera de la cascada porque su restaurant_id no es FK (es un UUID
-- suelto, ya desnormalizado como actor_email). Sus filas igual se borran, pero a mano
-- desde el servicio — ver RestaurantRegistrationService.deleteRestaurant().
-- ============================================================

DO $$
DECLARE
    cmds text[];
    cmd  text;
BEGIN
    SELECT array_agg(format('ALTER TABLE %s DROP CONSTRAINT %I, ADD CONSTRAINT %I %s ON DELETE CASCADE',
                            c.conrelid::regclass::text, c.conname, c.conname,
                            pg_get_constraintdef(c.oid)))
    INTO cmds
    FROM pg_constraint c
    JOIN (
        WITH RECURSIVE dependents(oid) AS (
            SELECT 'restaurants'::regclass::oid
            UNION
            SELECT fk.conrelid
            FROM pg_constraint fk
            JOIN dependents d ON fk.confrelid = d.oid
            WHERE fk.contype = 'f'
        )
        SELECT oid FROM dependents
    ) dep ON c.confrelid = dep.oid
    WHERE c.contype = 'f'
      AND c.confdeltype = 'a';  -- 'a' = NO ACTION: solo las que faltan

    FOREACH cmd IN ARRAY coalesce(cmds, '{}')
    LOOP
        EXECUTE cmd;
    END LOOP;
END $$;
