package bo.edu.uagrm.tienda.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import bo.edu.uagrm.tienda.config.ImagenesProperties;
import lombok.extern.slf4j.Slf4j;

// Las imágenes, y los modelos 3D de HU-15, viven en una carpeta del servidor con nombres que genera este componente.
// Ninguna ruta se arma con lo que manda el cliente: solo se aceptan nombres con la forma de los que genera guardar()
@Slf4j
@Component
@EnableConfigurationProperties(ImagenesProperties.class)
public class AlmacenImagenes {

	private static final String UUID_GENERADO = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
	// El original y, para una foto, sus versiones achicadas (HU-22): <uuid>-400.jpg y <uuid>-1200.jpg
	private static final Pattern NOMBRE_GENERADO = Pattern
			.compile(UUID_GENERADO + "(\\.(jpg|png|webp|glb)|-(400|1200)\\.jpg)");

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
		return guardar(contenido, formato.extension());
	}

	public String guardar(byte[] contenido, String extension) {
		String nombre = UUID.randomUUID() + "." + extension;
		try {
			Files.write(directorio.resolve(nombre), contenido);
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo guardar la imagen " + nombre, e);
		}
		return nombre;
	}

	// HU-22: la versión de una foto lleva su nombre sin la extensión, el ancho y .jpg. No valida: leer() y borrar()
	// solo aceptan nombres generados, así que con un original inventado la versión tampoco se encuentra
	public static String nombreDeVersion(String original, int ancho) {
		int punto = original.lastIndexOf('.');
		return (punto < 0 ? original : original.substring(0, punto)) + "-" + ancho + ".jpg";
	}

	public static List<String> nombresDeVersiones(String original) {
		return List.of(nombreDeVersion(original, Achicador.ANCHOS[0]), nombreDeVersion(original, Achicador.ANCHOS[1]));
	}

	// A un temporal de la misma carpeta y después a su nombre, de una vez: nunca se sirve una versión a medias
	public String guardarVersion(String original, int ancho, byte[] jpeg) {
		String nombre = nombreDeVersion(original, ancho);
		try {
			Path temporal = Files.createTempFile(directorio, "version-", ".tmp");
			try {
				Files.write(temporal, jpeg);
				Files.move(temporal, directorio.resolve(nombre), StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} finally {
				Files.deleteIfExists(temporal);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo guardar la versión " + nombre, e);
		}
		return nombre;
	}

	public boolean tieneVersiones(String original) {
		return nombresDeVersiones(original).stream().allMatch(nombre -> Files.exists(directorio.resolve(nombre)));
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
