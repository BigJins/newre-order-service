CREATE TABLE orders
(
    id           BIGINT AUTO_INCREMENT NOT NULL,
    buyer_id     BIGINT                NULL,
    total_amount BIGINT                NULL,
    status       VARCHAR(255)          NULL,
    delivery_id  BIGINT                NULL,
    created_at   datetime              NULL,
    confirmed_at datetime              NULL,
    cancelled_at datetime              NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);