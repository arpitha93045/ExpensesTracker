-- V5: Replace Material Icon names with emoji for global categories

UPDATE categories SET icon = '🍔' WHERE name = 'Food & Dining'    AND user_id IS NULL;
UPDATE categories SET icon = '🚗' WHERE name = 'Transportation'   AND user_id IS NULL;
UPDATE categories SET icon = '🛍️' WHERE name = 'Shopping'         AND user_id IS NULL;
UPDATE categories SET icon = '🎬' WHERE name = 'Entertainment'    AND user_id IS NULL;
UPDATE categories SET icon = '💊' WHERE name = 'Healthcare'       AND user_id IS NULL;
UPDATE categories SET icon = '💡' WHERE name = 'Utilities'        AND user_id IS NULL;
UPDATE categories SET icon = '✈️' WHERE name = 'Travel'           AND user_id IS NULL;
UPDATE categories SET icon = '📚' WHERE name = 'Education'        AND user_id IS NULL;
UPDATE categories SET icon = '🛒' WHERE name = 'Groceries'        AND user_id IS NULL;
UPDATE categories SET icon = '📺' WHERE name = 'Subscriptions'    AND user_id IS NULL;
UPDATE categories SET icon = '🏠' WHERE name = 'Rent & Housing'   AND user_id IS NULL;
UPDATE categories SET icon = '🛡️' WHERE name = 'Insurance'        AND user_id IS NULL;
UPDATE categories SET icon = '💰' WHERE name = 'Income'           AND user_id IS NULL;
UPDATE categories SET icon = '🏷️' WHERE name = 'Other'           AND user_id IS NULL;
