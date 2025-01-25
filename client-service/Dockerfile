# 1. Usar una imagen ligera de OpenJDK 17 basada en Alpine
FROM openjdk:17-jdk-alpine

# 2. Instalar certificados raíz necesarios para conexiones SSL
RUN apk add --no-cache ca-certificates \
    && update-ca-certificates

# 3. Crear un directorio de trabajo en el contenedor
WORKDIR /app

# 4. Copiar el archivo JAR generado por Maven al contenedor
COPY target/client-service-0.0.1-SNAPSHOT.jar app.jar

# 5. Crear un usuario sin privilegios (seguridad mejorada)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# 6. Exponer el puerto en el que corre la aplicación
EXPOSE 8086

# 7. Definir variables de entorno para JVM (optimización)
ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom"

# 8. Establecer el comando por defecto para ejecutar la app
CMD ["java", "-jar", "/app/app.jar"]

