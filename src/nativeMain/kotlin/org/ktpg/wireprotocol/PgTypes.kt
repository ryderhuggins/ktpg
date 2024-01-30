package org.ktpg.wireprotocol

enum class PgTypes(val oid: Int) {
    BOOL(16),
    CHAR(18),
    INT8(20),
    INT2(21),
    INT4(23),
    TEXT(25),
    JSON(114),
    XML(142),
    MONEY(790),
    VARCHAR(1043),
    DATE(1082),
    TIME(1083),
    TIMESTAMP(1114),
    TIMESTAMPZ(1184),
    BIT(1560),
    NUMERIC(1700),
    UUID(2950),
    JSONB(3802),
    JSONPATH(4072),
    INT4RANGE(3904),
    NUMRANGE(3904),
    TSRANGE(3908),
    TSTZRANGE(3908),
    DATERANGE(3912),
    ANY(2276),
    VOID(2278)
}