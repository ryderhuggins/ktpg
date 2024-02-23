
# psql -h 127.0.0.1 -p 5432 -d postgres
./tests/setup_test_db.sh

# do integration tests here
./gradlew runReleaseExecutableNative

./tests/teardown_test_db.sh