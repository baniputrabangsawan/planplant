CREATE TABLE IF NOT EXISTS identification_requests (
    request_id TEXT PRIMARY KEY,
    day TEXT NOT NULL,
    device TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS identification_requests_day_device
    ON identification_requests (day, device);
