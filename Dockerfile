FROM clojure:openjdk-11-lein-slim-buster AS BUILD
COPY . /code
WORKDIR /code
RUN lein with-profile prod uberjar

FROM openjdk:11-jre-slim
WORKDIR /app
COPY --from=BUILD /code/target/uberjar/*-standalone.jar ./app.jar
EXPOSE 3000
CMD ["java", "-jar", "app.jar", "serve"]
