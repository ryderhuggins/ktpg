
# High-level Overview
Driver.java - this implements the java.sql Driver interface
BaseDataSource - parent class for other data sources
PgSimpleDataSource.java - easiest way to get a connection, no pooling provided
PGConnectionPoolDataSource.java - provides connection pooling

# Q&A 
## How is a connection opened? 
Call PgSimpleDataSource.getConnection() -> BaseDataSource.getConnection() -> DriverManager.getConnection() looks for drivers and calls -> Driver.connect()