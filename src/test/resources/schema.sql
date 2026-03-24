-- H2 인메모리 DB 스키마 (테스트 전용)
-- H2 MODE=MySQL 로 실행 — MySQL 문법 호환

DROP TABLE IF EXISTS order_lines;
DROP TABLE IF EXISTS outbox_event;
DROP TABLE IF EXISTS orders;

CREATE TABLE orders
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    buyer_id     BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',
    total_amount BIGINT      NOT NULL,
    delivery_id  BIGINT,
    created_at   DATETIME    NOT NULL,
    confirmed_at DATETIME,
    cancelled_at DATETIME
);

CREATE TABLE order_lines
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id              BIGINT       NOT NULL,
    product_id            BIGINT       NOT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    unit_price            BIGINT       NOT NULL,
    quantity              INT          NOT NULL
);

CREATE TABLE outbox_event
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type     VARCHAR(50)  NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     DATETIME     NOT NULL
);