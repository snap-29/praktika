-- Optional demonstration data; not a production migration.
INSERT INTO tickets VALUES
('11111111-1111-1111-1111-111111111111','Demo: network','No connection',3,'HIGH','NEW',now(),now()-interval '1 hour',NULL)
ON CONFLICT DO NOTHING;
INSERT INTO status_history(ticket_id,from_status,to_status,changed_at)
SELECT id,NULL,'NEW',created_at FROM tickets t
WHERE id='11111111-1111-1111-1111-111111111111' AND NOT EXISTS (SELECT 1 FROM status_history h WHERE h.ticket_id=t.id);
