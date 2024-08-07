FROM openjdk:11-jdk-slim
ADD /build/libs/*.jar app.jar
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Duser.timezone=Asia/Seoul", "-jar", "/app.jar"]
