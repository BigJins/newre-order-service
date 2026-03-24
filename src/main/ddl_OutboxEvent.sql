CREATE TABLE outbox_event
(
    id             BIGINT AUTO_INCREMENT NOT NULL,
    event_type     VARCHAR(255)          NULL,
    aggregate_type VARCHAR(255)          NULL,
    aggregate_id   VARCHAR(255)          NULL,
    payload        VARCHAR(255)          NULL,
    created_at     datetime              NULL,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);