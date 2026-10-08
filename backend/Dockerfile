# API REST (Spring Boot). Dos etapas: se compila con el JDK y se ejecuta con el
# JRE, así la imagen final no lleva Maven ni el código fuente.

FROM eclipse-temurin:21-jdk AS compilacion
WORKDIR /fuente
# Primero el wrapper y el pom: las dependencias se bajan en una capa propia que
# solo se rehace si cambia el pom
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline
COPY src src
# Las pruebas corren en la máquina de desarrollo y antes de cada PR, no al armar la imagen
RUN ./mvnw -q -B -DskipTests package && cp target/tienda-*.jar /fuente/tienda.jar

FROM eclipse-temurin:21-jre
# La hora de Bolivia también para los registros; la aplicación ya usa America/La_Paz en su reloj
ENV TZ=America/La_Paz
# Sin privilegios de root: la aplicación solo necesita escribir las imágenes subidas
RUN groupadd --system tienda && useradd --system --gid tienda --no-create-home tienda \
	&& mkdir -p /datos/imagenes && chown -R tienda:tienda /datos
WORKDIR /app
COPY --from=compilacion /fuente/tienda.jar tienda.jar
USER tienda
ENV SPRING_PROFILES_ACTIVE=prod \
	TIENDA_IMAGENES_DIRECTORIO=/datos/imagenes
VOLUME /datos/imagenes
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/tienda.jar"]
