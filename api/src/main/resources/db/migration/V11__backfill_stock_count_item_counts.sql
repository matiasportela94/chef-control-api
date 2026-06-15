UPDATE stock_counts sc
SET adjustments_made = (
    SELECT COUNT(*)
    FROM stock_movements sm
    WHERE sm.reference_id = sc.id
      AND sm.reference_type = 'stock_count'
),
items_checked = (
    SELECT COUNT(*)
    FROM stock_movements sm
    WHERE sm.reference_id = sc.id
      AND sm.reference_type = 'stock_count'
)
WHERE adjustments_made = 0;
