package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bo.edu.uagrm.tienda.config.ImagenesProperties;

class AlmacenImagenesTest {

	@TempDir
	Path carpeta;

	private AlmacenImagenes almacen;

	@BeforeEach
	void crearAlmacen() {
		almacen = new AlmacenImagenes(new ImagenesProperties(carpeta.resolve("imagenes")));
	}

	@Test
	void creaLaCarpetaSiNoExiste() {
		assertThat(carpeta.resolve("imagenes")).isDirectory();
	}

	// RF-22 y RF-29: el nombre lo genera el sistema, con la extensión del formato real
	@Test
	void guardaConUnNombreGeneradoYLoLee() {
		String nombre = almacen.guardar(FormatoImagenTest.PNG, FormatoImagen.PNG);

		assertThat(nombre).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png");
		assertThat(almacen.leer(nombre)).hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(FormatoImagenTest.PNG));
	}

	@Test
	void dosImagenesIgualesTienenNombresDistintos() {
		assertThat(almacen.guardar(FormatoImagenTest.PNG, FormatoImagen.PNG))
				.isNotEqualTo(almacen.guardar(FormatoImagenTest.PNG, FormatoImagen.PNG));
	}

	@Test
	void borrarQuitaElArchivo() {
		String nombre = almacen.guardar(FormatoImagenTest.JPEG, FormatoImagen.JPEG);

		almacen.borrar(nombre);

		assertThat(carpeta.resolve("imagenes").resolve(nombre)).doesNotExist();
		assertThat(almacen.leer(nombre)).isEmpty();
	}

	// Un archivo que ya no está no rompe la operación que intenta borrarlo
	@Test
	void borrarUnoQueNoExisteNoFalla() {
		almacen.borrar("5f2c1d3e-0000-4000-8000-000000000000.png");
	}

	// HU-15 RF-2, RF-8: los modelos van a la misma carpeta, con nombre generado y extensión glb
	@Test
	void guardaYLeeUnGlb() {
		byte[] modelo = FormatoModeloTest.glb(64);
		String nombre = almacen.guardar(modelo, FormatoModelo.EXTENSION);

		assertThat(nombre).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.glb");
		assertThat(almacen.leer(nombre)).hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(modelo));
	}

	// HU-15 RF-8: otra extensión o un nombre que no generó no se lee
	@Test
	void ignoraNombresQueNoGenero() throws IOException {
		Files.writeString(carpeta.resolve("imagenes").resolve("modelo.glb"), "no");

		assertThat(almacen.leer("modelo.glb")).isEmpty();
		assertThat(almacen.leer("5f2c1d3e-0000-4000-8000-000000000000.gltf")).isEmpty();
		assertThat(almacen.leer("../5f2c1d3e-0000-4000-8000-000000000000.glb")).isEmpty();
	}

	// RF-29: nada de rutas armadas con datos de afuera, aunque vengan de la base
	@Test
	void noLeeNiBorraFueraDeSuCarpeta() throws IOException {
		Path ajeno = Files.writeString(carpeta.resolve("secreto.txt"), "no");

		assertThat(almacen.leer("../secreto.txt")).isEmpty();
		almacen.borrar("../secreto.txt");

		assertThat(ajeno).exists();
	}
}
