# Demo / dev only. Production runs as a plain JVM process on RHEL (ossmed01).
# docker build -t ums:jdk21 .
# docker run --rm ums:jdk21              -> generates sample CDRs and runs one mediation cycle
# docker run --rm -it ums:jdk21 bash     -> poke around /opt/ums/data afterwards
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
COPY refdata ./refdata
RUN mvn -B -q package

FROM eclipse-temurin:21-jre
WORKDIR /opt/ums
COPY --from=build /src/target/ums-mediation-5.2.1-jar-with-dependencies.jar lib/
COPY refdata ./refdata
COPY bin ./bin
ENV JAVA_HOME=/opt/java/openjdk
CMD ["sh", "-c", "bin/ums.sh gen-samples && bin/ums.sh run-once && echo && echo '=== control totals' && cat data/audit/*.csv && echo '=== outputs' && find data/output -type f"]
