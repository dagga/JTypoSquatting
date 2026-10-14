-- Web service schema (MySQL 8.4, InnoDB).
-- Domain names are stored in ASCII/punycode with a binary collation: the default utf8mb4_0900_ai_ci
-- collation ignores case and accents, which would merge homoglyph variants (exämple.com = example.com).
-- The Unicode form is kept separately for display only.

CREATE TABLE users (
    id           VARCHAR(255) NOT NULL,          -- OpenID Connect subject
    username     VARCHAR(255),
    email        VARCHAR(320),
    created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_seen_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_users PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_bin;

-- A scan is also the job processed by a worker (status PENDING → RUNNING → COMPLETED | CANCELLED | FAILED)
CREATE TABLE scans (
    id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id             VARCHAR(255) NOT NULL,
    base_domain         VARCHAR(253) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_domain_unicode VARCHAR(253) NOT NULL,
    status              VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    force_refresh       BOOLEAN NOT NULL DEFAULT FALSE,
    total_domains       INT NOT NULL DEFAULT 0,
    checked_domains     INT NOT NULL DEFAULT 0,
    worker_id           VARCHAR(100),
    heartbeat_at        TIMESTAMP(3) NULL,
    created_at          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    started_at          TIMESTAMP(3) NULL,
    finished_at         TIMESTAMP(3) NULL,
    error               VARCHAR(500),
    CONSTRAINT pk_scans PRIMARY KEY (id),
    CONSTRAINT fk_scans_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_scans_user_created (user_id, created_at),
    INDEX idx_scans_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_bin;

-- Latest check of each domain, shared by all users (cache)
CREATE TABLE domain_checks (
    domain         VARCHAR(253) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    domain_unicode VARCHAR(253) NOT NULL,
    status         VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    http_code      INT NOT NULL,
    tls_status     VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title          VARCHAR(500),
    language       VARCHAR(32),
    description    VARCHAR(1000),
    homepage_text  VARCHAR(1000),
    http_headers   JSON,
    screenshot_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin,
    checked_at     TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_domain_checks PRIMARY KEY (domain),
    INDEX idx_domain_checks_checked_at (checked_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_bin;

-- Results of a scan: a snapshot of each check, so that history does not change when the cache is refreshed.
-- The auto-increment id orders the results and is used as SSE event id (Last-Event-ID).
CREATE TABLE scan_results (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    scan_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    domain         VARCHAR(253) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    domain_unicode VARCHAR(253) NOT NULL,
    status         VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    http_code      INT NOT NULL,
    tls_status     VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title          VARCHAR(500),
    language       VARCHAR(32),
    description    VARCHAR(1000),
    homepage_text  VARCHAR(1000),
    http_headers   JSON,
    screenshot_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin,
    checked_at     TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_scan_results PRIMARY KEY (id),
    CONSTRAINT uk_scan_results_domain UNIQUE (scan_id, domain),
    CONSTRAINT fk_scan_results_scan FOREIGN KEY (scan_id) REFERENCES scans (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_bin;
