-- The healer optional reason for healing each chakra, as JSON {chakra name: reason}.
-- Nullable: protocol understandings saved before this migration have none.

ALTER TABLE healer_protocol_understanding ADD COLUMN chakra_reasons_json TEXT;
ALTER TABLE healer_protocol_understanding_aud ADD COLUMN chakra_reasons_json TEXT;
