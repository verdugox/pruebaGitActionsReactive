# Usar OpenJDK 17 como imagen base
FROM openjdk:17-jdk-alpine

# Instalar certificados raíz necesarios para conexiones SSL
RUN apk add --no-cache ca-certificates \
    && update-ca-certificates

# Crear un directorio para la app
WORKDIR /app

# Copiar el archivo JAR al contenedor
COPY target/client-service-0.0.1-SNAPSHOT.jar app.jar

# Establecer variables de entorno para JVM si es necesario
# Por ejemplo, para habilitar depuración o configuraciones específicas de red
ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom"

# Comando para ejecutar la aplicación
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
