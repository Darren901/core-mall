CREATE TABLE orders (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      VARCHAR(255) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity     INTEGER      NOT NULL CHECK (quantity > 0),
    status       VARCHAR(50)  NOT NULL DEFAULT 'CREATED',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
