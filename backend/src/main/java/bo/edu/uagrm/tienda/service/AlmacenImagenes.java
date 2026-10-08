package bo.edu.uagrm.tienda.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import bo.edu.uagrm.tienda.config.ImagenesProperties;
import lombok.extern.slf4j.Slf4j;

// Las imágenes viven en una carpeta del servidor con nombres que genera este componente. Ninguna ruta se arma con lo
// que manda el cliente: solo se aceptan nombres con la forma de los que genera guardar()
@Slf4j
@Component
@EnableConfigurationProperties(ImagenesProperties.class)
public class AlmacenImagenes {

	private static final Pattern NOMBRE_GENERADO = Pattern
			.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");

	private final Path directorio;

	public AlmacenImagenes(ImagenesProperties propiedades) {
		directorio = propiedades.directorio().toAbsolutePath().normalize();
		try {
			Files.createDirectories(directorio);
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo crear la carpeta de imágenes " + directorio, e);
		}
	}

	public String guardar(byte[] contenido, FormatoImagen formato) {
		String nombre = UUID.randomUUID() + "." + formato.extension();
		try {
			Files.write(directorio.resolve(nombre), contenido);
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo guardar la imagen " + nombre, e);
		}
		return nombre;
	}

	public Optional<byte[]> leer(String nombre) {
		if (!esNombreGenerado(nombre)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Files.readAllBytes(directorio.resolve(nombre)));
		} catch (NoSuchFileException e) {
			log.warn("La imagen {} figura en la base pero no está en {}", nombre, directorio);
			return Optional.empty();
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo leer la imagen " + nombre, e);
		}
	}

	// Un archivo que no se pudo borrar queda huérfano en la carpeta, pero no afecta a ningún dato: no se propaga
	public void borrar(String nombre) {
		if (!esNombreGenerado(nombre)) {
			log.warn("Se ignoró el borrado de un nombre de imagen inválido: {}", nombre);
			return;
		}
		try {
			Files.deleteIfExists(directorio.resolve(nombre));
		} catch (IOException e) {
			log.warn("No se pudo borrar la imagen {}", nombre, e);
		}
	}

	private static boolean esNombreGenerado(String nombre) {
		return nombre != null && NOMBRE_GENERADO.matcher(nombre).matches();
	}
}
