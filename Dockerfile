FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY engine/src engine/src
COPY standalone/src standalone/src
RUN mkdir classes && find engine/src/main/java standalone/src/main/java -name '*.java' > sources.txt && javac --release 21 --add-modules jdk.httpserver,jdk.compiler -d classes @sources.txt
FROM eclipse-temurin:21-jdk
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --create-home app && mkdir /app/data && chown app:app /app/data
COPY --from=build /app/classes /app/classes
COPY web /app/web
USER app
ENV PORT=8311 APP_BIND=0.0.0.0 APP_DATA=/app/data
EXPOSE 8311
CMD ["java","--add-modules","jdk.httpserver,jdk.compiler","-cp","classes","dev.portfolio.launcher.Main"]
