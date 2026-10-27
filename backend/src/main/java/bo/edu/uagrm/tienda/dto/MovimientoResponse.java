package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import bo.edu.uagrm.tienda.entity.MotivoAjuste;
import bo.edu.uagrm.tienda.entity.MovimientoStock;
import bo.edu.uagrm.tienda.entity.TipoMovimiento;

// Una fila del historial de stock (HU-08 RF-11): la cantidad con signo y quién lo hizo, por su nombre. La reserva y la
// liberación llevan su pedido (HU-12 RF-14), y el ajuste, lo contado y lo comprometido (HU-08 RF-17; null en los
// anteriores al 27/10/2026)
public record MovimientoResponse(Long idMovimiento, LocalDateTime fecha, TipoMovimiento tipo, MotivoAjuste motivo,
		BigDecimal cantidad, BigDecimal stockResultante, String usuario, String nota, Long idPedido, BigDecimal contado,
		BigDecimal comprometido) {

	// Lee el usuario: dentro de la transacción
	public static MovimientoResponse from(MovimientoStock movimiento) {
		return new MovimientoResponse(movimiento.getIdMovimiento(), movimiento.getFecha(), movimiento.getTipo(),
				movimiento.getMotivo(), movimiento.getCantidad(), movimiento.getStockResultante(),
				movimiento.getUsuario().getNombre() + " " + movimiento.getUsuario().getApellido(), movimiento.getNota(),
				movimiento.getPedido() == null ? null : movimiento.getPedido().getIdPedido(), movimiento.getContado(),
				movimiento.getComprometido());
	}
}
