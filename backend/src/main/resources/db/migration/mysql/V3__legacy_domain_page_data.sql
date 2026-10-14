-- Legacy table of the desktop application (Swing client, /api/data endpoints).
-- To be dropped with the Swing interface (plan P3.7).
CREATE TABLE domain_page_data (
    domain              VARCHAR(255) NOT NULL,
    html_content        MEDIUMTEXT,
    text_content        MEDIUMTEXT,
    meta_description    TEXT,
    meta_keywords       VARCHAR(1000),
    meta_author         VARCHAR(500),
    meta_og_title       VARCHAR(500),
    meta_og_description VARCHAR(1000),
    detected_language   VARCHAR(10),
    timestamp           BIGINT,
    http_code           INTEGER,
    http_headers        TEXT,
    screenshot          MEDIUMBLOB,
    CONSTRAINT pk_domain_page_data PRIMARY KEY (domain)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_bin;
