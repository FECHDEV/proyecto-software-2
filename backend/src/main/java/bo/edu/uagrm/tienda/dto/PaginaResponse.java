package bo.edu.uagrm.tienda.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

// Formato propio y estable de la API, en vez de serializar el Page de Spring Data
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

	public static <E, T> PaginaResponse<T> from(Page<E> pagina, Function<? super E, ? extends T> convertir) {
		return new PaginaResponse<>(pagina.getContent().stream().<T>map(convertir).toList(), pagina.getNumber(),
				pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
	}
}
