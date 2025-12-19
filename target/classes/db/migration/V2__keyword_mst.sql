CREATE TABLE IF NOT EXISTS keyword_mst (
  id          BIGSERIAL PRIMARY KEY,
  keyword     TEXT NOT NULL,
  category_id TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (keyword, category_id)
);

CREATE INDEX IF NOT EXISTS idx_keyword_mst_keyword ON keyword_mst(keyword);
CREATE INDEX IF NOT EXISTS idx_keyword_mst_category_id ON keyword_mst(category_id);
