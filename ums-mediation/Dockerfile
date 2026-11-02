# Demo / dev only. Production runs as a plain JVM process on RHEL 6 (ossmed01).
# docker build -t ums:legacy .
# docker run --rm ums:legacy              -> generates sample CDRs and runs one mediation cycle
# docker run --rm -it ums:legacy bash     -> poke around /opt/ums/data afterwards
FROM maven:3.6.3-jdk-8 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
COPY refdata ./refdata
RUN mvn -B -q package

FROM openjdk:8-jre
WORKDIR /opt/ums
COPY --from=build /src/target/ums-mediation-5.2.1-jar-with-dependencies.jar lib/
COPY refdata ./refdata
COPY bin ./bin
ENV JAVA_HOME=/usr/local/openjdk-8
CMD ["sh", "-c", "bin/ums.sh gen-samples && bin/ums.sh run-once && echo && echo '=== control totals' && cat data/audit/*.csv && echo '=== outputs' && find data/output -type f"]
