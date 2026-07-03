-- =============================================================================
-- V2: Seed default retry policy and demo organization
-- =============================================================================

-- Default organization for initial setup
INSERT INTO organizations (id, name, slug)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default Organization', 'default-org');

-- Default retry policies
INSERT INTO retry_policies (id, name, strategy, max_attempts, initial_delay_ms, multiplier, max_delay_ms, organization_id)
VALUES
    ('00000000-0000-0000-0000-000000000010', 'Default Exponential', 'EXPONENTIAL', 3, 1000, 2.0, 300000, '00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000011', 'Aggressive Retry', 'EXPONENTIAL', 5, 500, 1.5, 60000, '00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000012', 'Fixed 5s Retry', 'FIXED', 3, 5000, 1.0, 5000, '00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000013', 'Linear Backoff', 'LINEAR', 4, 2000, 1.0, 30000, '00000000-0000-0000-0000-000000000001');
