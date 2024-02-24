psql -h 127.0.0.1 -p 5432 -d postgres -f ${PWD}/tests/demo-small.sql

psql -h 127.0.0.1 -p 5432 -d demo -c "CREATE TABLE bookings.things(thing_id uuid primary key, bool_val boolean);"
psql -h 127.0.0.1 -p 5432 -d demo -c "insert into bookings.things(thing_id, bool_val) values ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', true);"
psql -h 127.0.0.1 -p 5432 -d demo -c "insert into bookings.things(thing_id, bool_val) values ('77bb7667-f126-4436-a4dc-799b2f781e48', true);"
psql -h 127.0.0.1 -p 5432 -d demo -c "insert into bookings.things(thing_id, bool_val) values ('2f80b315-15e9-4290-ad07-308d619ee213', false);"
psql -h 127.0.0.1 -p 5432 -d demo -c "insert into bookings.things(thing_id, bool_val) values ('8a0dbfd2-1a15-4453-a2a1-63b5ea654d43', true);"
psql -h 127.0.0.1 -p 5432 -d demo -c "insert into bookings.things(thing_id, bool_val) values ('3d313b88-4941-46b3-8fde-4753f6f5bc6b', false);"

psql -h 127.0.0.1 -p 5432 -d postgres -c "CREATE USER secure1 WITH PASSWORD 'password123';"
psql -h 127.0.0.1 -p 5432 -d postgres -c "CREATE USER secure2 WITH PASSWORD 'password123';"
psql -h 127.0.0.1 -p 5432 -d demo -c "GRANT USAGE ON SCHEMA bookings TO secure2;"
psql -h 127.0.0.1 -p 5432 -d demo -c "GRANT ALL ON ALL TABLES IN SCHEMA bookings TO secure2;"