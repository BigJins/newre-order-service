CREATE TABLE order_lines
(
    id                    BIGINT AUTO_INCREMENT NOT NULL,
    product_id            BIGINT NULL,
    product_name_snapshot VARCHAR(255) NULL,
    unit_price            BIGINT NULL,
    quantity              INT NULL,
    order_id              BIGINT NOT NULL,
    CONSTRAINT pk_order_lines PRIMARY KEY (id)
);

CREATE TABLE orders
(
    id           BIGINT AUTO_INCREMENT NOT NULL,
    buyer_id     BIGINT NULL,
    total_amount BIGINT NULL,
    status       VARCHAR(255) NULL,
    delivery_id  BIGINT NULL,
    created_at   datetime NULL,
    confirmed_at datetime NULL,
    cancelled_at datetime NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE TABLE outbox_event
(
    id             BIGINT AUTO_INCREMENT NOT NULL,
    event_type     VARCHAR(255) NULL,
    aggregate_type VARCHAR(255) NULL,
    aggregate_id   VARCHAR(255) NULL,
    payload        VARCHAR(255) NULL,
    created_at     datetime NULL,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);

ALTER TABLE order_lines
    ADD CONSTRAINT fk_order_lines_on_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE;