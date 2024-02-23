psql -h 127.0.0.1 -p 5432 -d postgres -c "DROP DATABASE demo;"
psql -h 127.0.0.1 -p 5432 -d postgres -c "DROP USER IF EXISTS secure1;"
psql -h 127.0.0.1 -p 5432 -d postgres -c "DROP USER IF EXISTS secure2;"