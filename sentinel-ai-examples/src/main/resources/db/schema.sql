-- =============================================================================
-- E-Commerce SQLite Database Schema
-- =============================================================================
-- This schema models a simplified e-commerce marketplace with sellers listing
-- products, buyers placing orders, and an inventory tracking system.
--
-- Timestamp convention: All *_at columns store Unix epoch seconds (INTEGER).
-- Use the agent's convertEpochToLocalDateTime tool to display human-readable
-- timestamps in the user's local timezone.
-- =============================================================================

PRAGMA foreign_keys = ON;

-- -----------------------------------------------------------------------------
-- TABLE: users
-- Registered buyers on the marketplace. Each user has a timezone preference
-- used for displaying dates in their local time.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id       INTEGER PRIMARY KEY AUTOINCREMENT, -- unique internal user identifier
    email         TEXT    NOT NULL UNIQUE,           -- login email address (must be unique)
    full_name     TEXT    NOT NULL,                  -- user's display name
    phone         TEXT,                              -- contact phone in E.164 format e.g. +919876543210
    timezone      TEXT    NOT NULL DEFAULT 'UTC',    -- IANA timezone e.g. 'Asia/Kolkata', 'America/New_York'
    city          TEXT,                              -- city of residence
    country       TEXT    NOT NULL DEFAULT 'IN',     -- ISO 3166-1 alpha-2 country code
    is_active     INTEGER NOT NULL DEFAULT 1,        -- 1 = active account, 0 = deactivated
    created_at    INTEGER NOT NULL,                  -- epoch seconds: when the account was created
    last_login_at INTEGER                            -- epoch seconds: most recent successful login (NULL if never)
);

-- -----------------------------------------------------------------------------
-- TABLE: sellers
-- Merchants who list products on the marketplace. A seller can have multiple
-- products in the catalog. Seller rating is the rolling average across all
-- fulfilled orders.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sellers (
    seller_id     INTEGER PRIMARY KEY AUTOINCREMENT, -- unique internal seller identifier
    seller_name   TEXT    NOT NULL,                  -- business / brand name shown to buyers
    contact_email TEXT    NOT NULL UNIQUE,           -- seller's primary contact email
    phone         TEXT,                              -- seller contact phone in E.164 format
    city          TEXT,                              -- city where the seller operates
    country       TEXT    NOT NULL DEFAULT 'IN',     -- ISO 3166-1 alpha-2 country code
    rating        REAL    NOT NULL DEFAULT 0.0,      -- rolling average rating 0.0–5.0 from buyer reviews
    total_reviews INTEGER NOT NULL DEFAULT 0,        -- total number of reviews received
    joined_at     INTEGER NOT NULL,                  -- epoch seconds: when the seller account was approved
    is_active     INTEGER NOT NULL DEFAULT 1         -- 1 = actively selling, 0 = suspended or deactivated
);

-- -----------------------------------------------------------------------------
-- TABLE: catalog
-- Product listings created by sellers. Each product belongs to exactly one
-- seller and one category. Price is in USD at time of listing. It may change
-- over time but orders capture unit_price at time of purchase.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `catalog` (
    product_id    INTEGER PRIMARY KEY AUTOINCREMENT, -- unique internal product identifier
    seller_id     INTEGER NOT NULL,                  -- FK → sellers.seller_id: which seller listed this product
    product_name  TEXT    NOT NULL,                  -- human-readable product title shown to buyers
    category      TEXT    NOT NULL,                  -- top-level category e.g. Electronics, Clothing, Books
    subcategory   TEXT,                              -- optional finer-grained category e.g. Smartphones, T-Shirts
    brand         TEXT,                              -- brand or manufacturer name
    sku           TEXT    UNIQUE,                    -- seller's stock-keeping unit (optional, seller-assigned)
    price         REAL    NOT NULL,                  -- current list price in USD
    description   TEXT,                             -- full product description shown on product page
    image_url     TEXT,                              -- URL to the primary product image
    is_available  INTEGER NOT NULL DEFAULT 1,        -- 1 = listed and available for purchase, 0 = delisted
    created_at    INTEGER NOT NULL,                  -- epoch seconds: when the product was first listed
    updated_at    INTEGER NOT NULL,                  -- epoch seconds: when the product details were last modified
    FOREIGN KEY (seller_id) REFERENCES sellers(seller_id)
);

-- -----------------------------------------------------------------------------
-- TABLE: inventory
-- Current stock levels per product per warehouse. A single product may be
-- stocked in multiple warehouses. The total available stock is the SUM of
-- quantity across all rows for that product_id.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inventory (
    inventory_id  INTEGER PRIMARY KEY AUTOINCREMENT, -- unique internal inventory record identifier
    product_id    INTEGER NOT NULL,                  -- FK → catalog.product_id: which product this stock belongs to
    warehouse     TEXT    NOT NULL,                  -- warehouse location code e.g. 'MUM-01', 'DEL-02', 'BLR-03'
    quantity      INTEGER NOT NULL DEFAULT 0,        -- current units available at this warehouse (never negative)
    reorder_level INTEGER NOT NULL DEFAULT 10,       -- stock level that triggers a reorder alert
    updated_at    INTEGER NOT NULL,                  -- epoch seconds: when this stock level was last updated
    UNIQUE (product_id, warehouse),                  -- one record per product per warehouse
    FOREIGN KEY (product_id) REFERENCES catalog(product_id)
);

-- -----------------------------------------------------------------------------
-- TABLE: orders
-- Purchase transactions placed by users. Each row represents one line item
-- (one product, one quantity). A multi-product cart results in multiple rows
-- sharing the same session but different order_ids.
--
-- Status lifecycle: pending → confirmed → shipped → delivered
--                   pending → cancelled
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    order_id      INTEGER PRIMARY KEY AUTOINCREMENT, -- unique internal order identifier
    user_id       INTEGER NOT NULL,                  -- FK → users.user_id: who placed the order
    product_id    INTEGER NOT NULL,                  -- FK → catalog.product_id: which product was ordered
    seller_id     INTEGER NOT NULL,                  -- FK → sellers.seller_id: denormalised for quick seller queries
    quantity      INTEGER NOT NULL,                  -- number of units ordered (≥ 1)
    unit_price    REAL    NOT NULL,                  -- price per unit at the moment of purchase (snapshot)
    total_amount  REAL    NOT NULL,                  -- quantity × unit_price. Pre-computed for reporting
    status        TEXT    NOT NULL DEFAULT 'pending',-- order lifecycle state: pending/confirmed/shipped/delivered/cancelled
    shipping_city TEXT,                              -- destination city for delivery
    ordered_at    INTEGER NOT NULL,                  -- epoch seconds: when the order was placed
    confirmed_at  INTEGER,                           -- epoch seconds: when payment was confirmed (NULL if not yet)
    shipped_at    INTEGER,                           -- epoch seconds: when the package was dispatched (NULL if not yet)
    delivered_at  INTEGER,                           -- epoch seconds: when the package was delivered (NULL if not yet)
    cancelled_at  INTEGER,                           -- epoch seconds: when the order was cancelled (NULL if not cancelled)
    FOREIGN KEY (user_id)    REFERENCES users(user_id),
    FOREIGN KEY (product_id) REFERENCES catalog(product_id),
    FOREIGN KEY (seller_id)  REFERENCES sellers(seller_id)
);

-- -----------------------------------------------------------------------------
-- Indexes for common query patterns
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_catalog_seller    ON `catalog`   (seller_id);
CREATE INDEX IF NOT EXISTS idx_catalog_category  ON `catalog`   (category);
CREATE INDEX IF NOT EXISTS idx_inventory_product ON inventory (product_id);
CREATE INDEX IF NOT EXISTS idx_orders_user       ON orders    (user_id);
CREATE INDEX IF NOT EXISTS idx_orders_product    ON orders    (product_id);
CREATE INDEX IF NOT EXISTS idx_orders_seller     ON orders    (seller_id);
CREATE INDEX IF NOT EXISTS idx_orders_status     ON orders    (status);
CREATE INDEX IF NOT EXISTS idx_orders_ordered_at ON orders    (ordered_at);
