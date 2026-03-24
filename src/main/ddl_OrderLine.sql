CREATE TABLE order_lines
(
    id                    BIGINT AUTO_INCREMENT NOT NULL,
    product_id            BIGINT                NULL,
    product_name_snapshot VARCHAR(255)          NULL,
    unit_price            BIGINT                NULL,
    quantity              INT                   NULL,
    order_id              BIGINT                NOT NULL,
    CONSTRAINT pk_order_lines PRIMARY KEY (id)
);

ALTER TABLE order_lines
    ADD CONSTRAINT fk_order_lines_on_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE;
CREATE TABLE order_lines
(
    id                    BIGINT AUTO_INCREMENT NOT NULL,
    product_id            BIGINT                NULL,
    product_name_snapshot VARCHAR(255)          NULL,
    unit_price            BIGINT                NULL,
    quantity              INT                   NULL,
    order_id              BIGINT                NOT NULL,
    CONSTRAINT pk_order_lines PRIMARY KEY (id)
);

ALTER TABLE order_lines
    ADD CONSTRAINT fk_order_lines_on_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE;