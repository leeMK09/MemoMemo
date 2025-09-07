CREATE TABLE IF NOT EXISTS shedlock (
                                        name        VARCHAR(64)   NOT NULL,
    lock_until  DATETIME(3)   NOT NULL,
    locked_at   DATETIME(3)   NOT NULL,
    locked_by   VARCHAR(255)  NOT NULL,
    PRIMARY KEY (name)
    );

CREATE TABLE IF NOT EXISTS outbox (
                                      id               BIGINT       NOT NULL AUTO_INCREMENT,
                                      idempotency_key  VARCHAR(191) NOT NULL,
    attempt_count    INT          NOT NULL DEFAULT 0,
    max_attempts     INT          NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    channel          VARCHAR(32)  NOT NULL,
    next_attempt_at  DATETIME(3)  NOT NULL,
    CONSTRAINT pk_outbox PRIMARY KEY (id),
    CONSTRAINT uk_outbox_idempotency_key UNIQUE (idempotency_key),
    KEY idx_outbox_status_next (status, next_attempt_at),
    KEY idx_outbox_channel (channel)
    );
