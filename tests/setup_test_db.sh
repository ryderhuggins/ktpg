psql -h 127.0.0.1 -p 5432 -d postgres -f ${PWD}/tests/demo-small.sql
psql -h 127.0.0.1 -p 5432 -d postgres -c "CREATE USER secure1 WITH PASSWORD 'password123';"
psql -h 127.0.0.1 -p 5432 -d postgres -c "CREATE USER secure2 WITH PASSWORD 'password123';"
psql -h 127.0.0.1 -p 5432 -d demo -c "GRANT USAGE ON SCHEMA bookings TO secure2;"
psql -h 127.0.0.1 -p 5432 -d demo -c "GRANT ALL ON ALL TABLES IN SCHEMA bookings TO secure2;"