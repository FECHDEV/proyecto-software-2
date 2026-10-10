package bo.edu.uagrm.tienda.entity;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Una categoría del catálogo, de un solo nivel (decisiones.md → Categorías del catálogo)
@Entity
@Table(name = "categoria")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Categoria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_categoria")
	private Long idCategoria;

	@Column(nullable = false, length = 60)
	private String nombre;

	// El nombre recortado, sin tildes y en minúsculas: la restricción única impide dos categorías que solo difieren
	// en eso, también si llegan a la vez, y se comporta igual en H2 que en MySQL (cuya intercalación ignora tildes).
	// Más larga que el nombre porque algunos caracteres crecen al pasar a minúsculas
	@Column(name = "nombre_clave", nullable = false, length = 120, unique = true)
	private String nombreClave;

	@Column(length = 300)
	private String descripcion;

	// El nombre del archivo que generó AlmacenImagenes; nunca el que trajo la persona
	@Column(length = 64)
	private String imagen;

	@Column(nullable = false)
	private boolean visible;

	public static Categoria crear(String nombre, String descripcion) {
		Categoria categoria = new Categoria();
		categoria.editar(nombre, descripcion);
		categoria.visible = true;
		return categoria;
	}

	public static String clave(String nombre) {
		String sinTildes = Normalizer.normalize(nombre.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return sinTildes.toLowerCase(Locale.ROOT);
	}

	public void editar(String nombre, String descripcion) {
		this.nombre = Objects.requireNonNull(nombre).strip();
		this.nombreClave = clave(nombre);
		this.descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.strip();
	}

	public void cambiarImagen(String imagen) {
		this.imagen = imagen;
	}

	public void ocultar() {
		visible = false;
	}

	public void mostrar() {
		visible = true;
	}
}
