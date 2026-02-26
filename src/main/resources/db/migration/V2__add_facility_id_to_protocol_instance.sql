-- V2: Add facility_id column to protocol_instance for facility-level filtering
-- The facility is stamped from the inbound CloudEvent at enrollment time.

ALTER TABLE protocol_instance ADD COLUMN facility_id VARCHAR;

CREATE INDEX idx_protocol_instance_facility ON protocol_instance (facility_id) WHERE facility_id IS NOT NULL;
