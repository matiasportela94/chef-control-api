ALTER TABLE stock_counts ADD COLUMN items_checked  INT NOT NULL DEFAULT 0;
ALTER TABLE stock_counts ADD COLUMN adjustments_made INT NOT NULL DEFAULT 0;
