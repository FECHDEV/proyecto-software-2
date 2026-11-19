package bo.edu.uagrm.tienda.service.pago;

import java.math.BigDecimal;

// El sistema de pago detrás de una interfaz propia (decisiones.md → Pago: interfaz propia con dos implementaciones).
// Sigue el circuito del proveedor: crear el cobro (la deuda, con su QR) y consultar su resultado. El aviso por
// callback_url es del adaptador del proveedor y termina en la misma consulta. Este paquete es el único que va a
// conocer al proveedor; idOperacion es lo que se cobra (un pedido, una reserva) (CLAUDE.md → Pago). rutaDeRetorno es
// la página de la tienda a la que vuelve quien paga en la pasarela; nula, la portada (HU-13 RF-13)
public interface IPago {

	CobroGenerado generarCobro(Long idOperacion, BigDecimal monto, String concepto, String rutaDeRetorno);

	ResultadoDelCobro consultar(String referencia);
}
