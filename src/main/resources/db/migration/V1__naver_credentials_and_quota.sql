-- Credentials are stored to allow runtime add/update/delete and survive restarts.
-- NOTE: api_secret is stored as plaintext here; in production consider encryption (KMS/Vault).

CREATE TABLE IF NOT EXISTS naver_credentials (
  customer_id   TEXT PRIMARY KEY,
  api_key       TEXT NOT NULL,
  api_secret    TEXT NOT NULL,
  enabled       BOOLEAN NOT NULL DEFAULT TRUE,
  daily_limit   INT NOT NULL DEFAULT 1000,
  max_in_flight INT NOT NULL DEFAULT 8,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_naver_credentials_enabled ON naver_credentials(enabled);

-- Usage counter per day (date is evaluated in application timezone)
CREATE TABLE IF NOT EXISTS naver_credential_usage (
  customer_id TEXT NOT NULL REFERENCES naver_credentials(customer_id) ON DELETE CASCADE,
  usage_date  DATE NOT NULL,
  used_count  INT NOT NULL DEFAULT 0,
  PRIMARY KEY (customer_id, usage_date)
);

CREATE INDEX IF NOT EXISTS idx_naver_credential_usage_date ON naver_credential_usage(usage_date);
