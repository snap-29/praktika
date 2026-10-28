CREATE TABLE categories (id BIGSERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE);
INSERT INTO categories(name) VALUES ('Hardware'), ('Software'), ('Network');
CREATE TABLE tickets (
 id UUID PRIMARY KEY,
 title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
 description TEXT NOT NULL,
 category_id BIGINT NOT NULL REFERENCES categories(id),
 priority VARCHAR(10) NOT NULL CHECK (priority IN ('LOW','MEDIUM','HIGH')),
 status VARCHAR(20) NOT NULL CHECK (status IN ('NEW','IN_PROGRESS','RESOLVED','CANCELLED')),
 created_at TIMESTAMPTZ NOT NULL,
 due_at TIMESTAMPTZ,
 closed_at TIMESTAMPTZ,
 CHECK ((status IN ('RESOLVED','CANCELLED')) = (closed_at IS NOT NULL))
);
CREATE TABLE status_history (
 id BIGSERIAL PRIMARY KEY,
 ticket_id UUID NOT NULL REFERENCES tickets(id),
 from_status VARCHAR(20),
 to_status VARCHAR(20) NOT NULL,
 changed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX tickets_created_idx ON tickets(created_at);
CREATE INDEX tickets_filter_idx ON tickets(status,priority,category_id);
CREATE INDEX history_ticket_idx ON status_history(ticket_id,id);
