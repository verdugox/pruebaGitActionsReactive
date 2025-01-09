# Usar OpenJDK 17 como imagen base
FROM openjdk:17-jdk-alpine

# Crear un directorio para la app
WORKDIR /app

# Copiar el archivo JAR al contenedor
COPY target/client-service-0.0.1-SNAPSHOT.jar app.jar

# Comando para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
