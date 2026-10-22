package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.dto.PaginaResponse;
import bo.edu.uagrm.tienda.dto.PedidoDespachoResponse;
import bo.edu.uagrm.tienda.dto.PedidoDespachoResumenResponse;
import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.exception.PedidoNoEncontradoException;
import bo.edu.uagrm.tienda.exception.PedidoNoEntregableException;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.PedidoRepository.FilaDeDespacho;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

// Los pedidos de todos los clientes, para el personal (HU-17): verlos y entregar los pagados. Quién puede cada cosa lo
// decide SecurityConfig
@Service
@RequiredArgsConstructor
public class DespachoService {

	static final int POR_PAGINA = 10;
	static final String TODOS = "TODOS";
	private static final int LARGO_BUSQUEDA = 100;
	// Que el desplazamiento de la página entre en un int (HU-14, revisión)
	private static final int PAGINA_MAXIMA = Integer.MAX_VALUE / POR_PAGINA - 1;

	private final PedidoRepository pedidoRepository;
	private final UsuarioRepository usuarioRepository;
	private final Clock clock;

	// La lista (RF-1 a RF-3, RF-12). Sin estado o con uno que no existe, los pagados, el pagado hace más tiempo primero;
	// TODOS, sin filtro. Del texto cuentan los primeros 100 caracteres, sin tildes ni mayúsculas; si son solo dígitos,
	// también busca ese número. No vence pedidos: eso lo hace la tarea de cada minuto
	@Transactional(readOnly = true)
	public PaginaResponse<PedidoDespachoResumenResponse> lista(String estado, String texto, int pagina) {
		EstadoPedido filtro = TODOS.equals(estado) ? null : estadoOPagado(estado);
		// Sin los espacios de alrededor primero: no cuentan para el límite. Por caracteres completos: no parte un emoji
		String limpio = texto == null ? null : texto.strip();
		String recortado = limpio == null ? null
				: limpio.substring(0, limpio.offsetByCodePoints(0, Math.min(limpio.codePointCount(0, limpio.length()),
						LARGO_BUSQUEDA)));
		String patron = Busquedas.patron(recortado, Producto::textoDeBusqueda);
		Long numero = patron != null && recortado.strip().matches("\\d{1,18}") ? Long.valueOf(recortado.strip()) : null;
		Sort orden = filtro == EstadoPedido.PAGADO ? Sort.by("pagado", "idPedido")
				: Sort.by(Sort.Direction.DESC, "confirmado", "idPedido");
		Page<FilaDeDespacho> filas = pedidoRepository.despacho(filtro, patron, numero,
				PageRequest.of(Math.clamp(pagina, 0, PAGINA_MAXIMA), POR_PAGINA, orden));
		if (filas.isEmpty() && filas.getTotalPages() > 0) { // después de la última, la última
			filas = pedidoRepository.despacho(filtro, patron, numero,
					PageRequest.of(filas.getTotalPages() - 1, POR_PAGINA, orden));
		}
		return PaginaResponse.from(filas, fila -> new PedidoDespachoResumenResponse(fila.idPedido(), fila.estado(),
				fila.confirmado(), fila.pagado(), fila.nombreRetira(), fila.cantidadProductos(), fila.total()));
	}

	private static EstadoPedido estadoOPagado(String estado) {
		return Arrays.stream(EstadoPedido.values()).filter(valor -> valor.name().equals(estado)).findFirst()
				.orElse(EstadoPedido.PAGADO);
	}

	@Transactional(readOnly = true)
	public PedidoDespachoResponse ficha(Long idPedido) {
		return pedidoRepository.findById(idPedido).map(PedidoDespachoResponse::from)
				.orElseThrow(PedidoNoEncontradoException::new);
	}

	// Cliente y después pedido, como todo lo que cambia un pedido (decisiones.md → Pedidos). El id del cliente se lee
	// sin bloqueo y sin cargar el pedido: una entidad ya leída no se vuelve a leer al bloquearla, y dos entregas a la vez
	// la verían pagada. READ_COMMITTED como lo demás que cambia un pedido (decisiones.md → Pagos)
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public PedidoDespachoResponse entregar(Long idPedido, Long idPersonal) {
		Long idCliente = pedidoRepository.idClienteDelPedido(idPedido).orElseThrow(PedidoNoEncontradoException::new);
		usuarioRepository.findConBloqueoByIdUsuario(idCliente).orElseThrow(PedidoNoEncontradoException::new);
		Pedido pedido = pedidoRepository.findConBloqueoByIdPedidoAndUsuario_IdUsuario(idPedido, idCliente)
				.orElseThrow(PedidoNoEncontradoException::new);
		if (pedido.getEstado() != EstadoPedido.PAGADO) {
			throw new PedidoNoEntregableException(); // RF-7: ya entregado, pendiente, cerrado o con pago a devolver
		}
		pedido.entregar(usuarioRepository.getReferenceById(idPersonal), LocalDateTime.now(clock));
		return PedidoDespachoResponse.from(pedido);
	}
}
