-- Phase 4 Unit 6: investigation result on the incident aggregate.
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS hypothesis JSONB;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS investigated_at TIMESTAMPTZ;