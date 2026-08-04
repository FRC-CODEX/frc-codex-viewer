-- Survives the server task, so that the age of the last event received from the Companies House
-- stream is not reset to zero by every deploy and every task replacement.
CREATE TABLE stream_receipts (
    id INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    last_received_date TIMESTAMPTZ NOT NULL
);
