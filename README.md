# kotligres
a Kotlin/Native postgres client

## Goals
- Implement a postgres client in K/N 
- Serve as the foundation for a future JDBC equivalent for kotlin-native  

## Purpose
- Why? -> to be able to write e.g. a ktor K/N server with a native postgres client 
- Why not just wrap libpq? -> 50/50 for my own learning/to not have to depend on a native library \

## Resources
- Use this for testing? -> https://github.com/jackc/pgmock
- [Native JDBC equivalent](https://www.google.com/url?sa=t&rct=j&q=&esrc=s&source=web&cd=&ved=2ahUKEwiPlMzbwpmDAxXMElkFHXMwDw0QFnoECBEQAQ&url=https%3A%2F%2Fslack-chats.kotlinlang.org%2Ft%2F527178%2Fhi-what-is-the-support-for-databases-in-kotlin-native-e-g-is&usg=AOvVaw0IDhu0PnzfMq77WxGhNKzO&opi=89978449)
- [Native crypto](https://slack-chats.kotlinlang.org/t/522601/are-there-plans-from-jetbrains-for-a-multiplatform-crypto-li)
- [Question about hash functions](https://github.com/JetBrains/kotlin-native/issues/2466)
- [Sample project implementing a subset of Postgres server commands on Java 21](https://gavinray97.github.io/blog/postgres-wire-protocol-jdk-21) 
- [Ktor TCP client](https://github.com/ktorio/ktor-documentation/blob/2.3.7/codeSnippets/snippets/sockets-client/src/main/kotlin/com/example/Application.kt)
- [Ktor TCP server](https://github.com/ktorio/ktor-documentation/tree/2.3.7/codeSnippets/snippets/embedded-server-native)
- [DataStation blog about Postgres wire protocol](https://datastation.multiprocess.io/blog/2022-02-08-the-world-of-postgresql-wire-compatibility.html)
- [Multiplatform libraries](https://github.com/AAkira/Kotlin-Multiplatform-Libraries)

## Other notes/reminders
- Might have to wrap a library for MD5 if necessary 

## Run
Start postgres locally: 
```shell
pg_ctl -D /usr/local/var/postgres start
```

Or if you haven't restarted after install postgres: 
```shell
/usr/local/opt/postgresql@14/bin/postgres -D /usr/local/var/postgresql@14
```

Test a connection: 
```shell
psql -h 127.0.0.1 -p 5432 -d postgres
```

Build and run the client app: 
```shell
./gradlew runReleaseExecutableNative
```