# ktpg
The humble beginnings of a Kotlin/Native postgres client

## Goals
- Implement a postgres client in K/N 
- Serve as the foundation for a proper K/N database driver

## Purpose
- Why? -> to be able to write e.g. a ktor K/N server with a native postgres client 
- Why not just wrap libpq? -> 50/50 for my own learning/to not have to depend on an implementation in a different language  

## Current Status
Implemented: 
- Connection startup with no auth, password auth, or scram-sha-256 auth 
- Simple query and result parsing 
- Prepared statements and result parsing (with limited type bindings. see BindMessage.kt)

WIP
- Integration tests (see Main.kt)

Not implemented: 
- Connection over SSL
- Other SASL authentication methods
- Some client (frontend) commands: CancelRequest, Close, Describe, FunctionCall
- Publish to maven central
  
See Docs/Feature Roadmap.txt for more info

## Run
Start postgres locally: 
```shell
pg_ctl -D /usr/local/var/postgres start
```

Or if you haven't restarted after install postgres: 
```shell
LC_ALL="C" /opt/homebrew/opt/postgresql@15/bin/postgres -D /opt/homebrew/var/postgresql@15
```

Test a connection: 
```shell
psql -h 127.0.0.1 -p 5432 -d postgres
```

Build and run the "client app" -> this is really just a lazy manual test setup right now: 
```shell
./gradlew runReleaseExecutableNative
```

Run unit tests:
```shell
./gradlew nativeTest
```

## Resources
- [Native crypto](https://slack-chats.kotlinlang.org/t/522601/are-there-plans-from-jetbrains-for-a-multiplatform-crypto-li)
- [Question about hash functions](https://github.com/JetBrains/kotlin-native/issues/2466)
- [Sample project implementing a subset of Postgres server commands on Java 21](https://gavinray97.github.io/blog/postgres-wire-protocol-jdk-21)
- [Ktor TCP client](https://github.com/ktorio/ktor-documentation/blob/2.3.7/codeSnippets/snippets/sockets-client/src/main/kotlin/com/example/Application.kt)
- [Ktor TCP server](https://github.com/ktorio/ktor-documentation/tree/2.3.7/codeSnippets/snippets/embedded-server-native)
- [DataStation blog about Postgres wire protocol](https://datastation.multiprocess.io/blog/2022-02-08-the-world-of-postgresql-wire-compatibility.html)
- [Multiplatform libraries](https://github.com/AAkira/Kotlin-Multiplatform-Libraries)
- [Sample postgres database used in integration testing](https://postgrespro.com/community/demodb)
- [Postgres for the wire](https://beta.pgcon.org/2014/schedule/attachments/330_postgres-for-the-wire.pdf)