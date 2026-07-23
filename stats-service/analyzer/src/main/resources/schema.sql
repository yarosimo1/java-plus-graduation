CREATE TABLE IF NOT EXISTS user_interactions (
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    weight DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP NOT NULL,
PRIMARY KEY(user_id,event_id));

CREATE TABLE IF NOT EXISTS event_similarities (
event_a BIGINT NOT NULL,
event_b BIGINT NOT NULL,
score DOUBLE PRECISION NOT NULL,
updated_at TIMESTAMP NOT NULL,
PRIMARY KEY(event_a,event_b));