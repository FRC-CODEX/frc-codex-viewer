FROM amazoncorretto:22

COPY gradle/ /gradle/
COPY src/ /src/
COPY build.gradle /build.gradle
COPY gradlew /gradlew
COPY gradlew.bat /gradlew.bat
COPY settings.gradle /settings.gradle

RUN ./gradlew build

USER nobody

CMD ["java","-jar","/build/libs/frc-codex.jar"]
EXPOSE 8080
