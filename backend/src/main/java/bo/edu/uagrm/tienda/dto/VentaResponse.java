package bo.edu.uagrm.tienda.dto;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.MedioPago;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Usuario;

// Una venta del mostrador y su comprobante (HU-23 RF-6 a RF-9, RF-15, RF-19). comprador: nulo para el consumidor
// final. cobro: el QR mientras espera el pago. anulable: si hoy se puede anular (en efectivo, entregada y del día); que
// lo haga un Administrador lo decide SecurityConfig, y la pantalla muestra el botón solo a él
public record VentaResponse(Long idPedido, EstadoPedido estado, LocalDateTime confirmado, LocalDateTime vence,
		long segundosParaPagar, LocalDateTime pagado, CompradorResponse comprador, String vendidoPor,
		List<LineaPedidoResponse> lineas, BigDecimal total, MedioPago medio, BigDecimal recibido, BigDecimal cambio,
		CobroResponse cobro, LocalDateTime anulado, String anuladoPor, String motivoAnulacion, boolean anulable) {

	// Lee las líneas y las cuentas: dentro de la transacción
	public static VentaResponse from(Pedido venta, CobroResponse cobro, boolean anulable, LocalDateTime ahora) {
		long segundos = venta.getEstado() == EstadoPedido.PENDIENTE_PAGO
				? Math.max(0, Duration.between(ahora, venta.getVence()).toSeconds())
				: 0;
		return new VentaResponse(venta.getIdPedido(), venta.getEstado(), venta.getConfirmado(), venta.getVence(),
				segundos, venta.getPagado(), venta.getUsuario() == null ? null : CompradorResponse.from(venta.getUsuario()),
				nombre(venta.getVendidoPor()), venta.getLineas().stream().map(LineaPedidoResponse::from).toList(),
				venta.getTotal(), venta.getMedioPago(), venta.getRecibido(), venta.cambio(),
				venta.getEstado() == EstadoPedido.PENDIENTE_PAGO ? cobro : null, venta.getAnulado(),
				venta.getAnuladoPor() == null ? null : nombre(venta.getAnuladoPor()), venta.getMotivoAnulacion(),
				anulable);
	}

	private static String nombre(Usuario usuario) {
		return usuario.getNombre() + " " + usuario.getApellido();
	}
}
