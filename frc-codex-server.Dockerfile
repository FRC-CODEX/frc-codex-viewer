FROM node:24 AS node
WORKDIR /usr/app
COPY package.json ./
RUN npm install --omit=dev

FROM amazoncorretto:21

COPY gradle/ /gradle/
COPY build.gradle /build.gradle
COPY gradlew /gradlew
COPY settings.gradle /settings.gradle

# Download the Gradle distribution
RUN ./gradlew --version

COPY --from=node /usr/app/node_modules /node_modules
COPY src/ /src/

RUN ./gradlew build

USER nobody

CMD ["java","-jar","/build/libs/frc-codex.jar"]
EXPOSE 8080
