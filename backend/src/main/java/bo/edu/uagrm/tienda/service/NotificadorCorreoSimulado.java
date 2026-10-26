package bo.edu.uagrm.tienda.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

// Escribe el token en claro: solo para desarrollo y pruebas, nunca activo en producción (decisiones.md →
// Notificaciones). Es la implementación por defecto; con TIENDA_CORREO_PROVEEDOR=smtp se usa NotificadorCorreoSmtp
@Slf4j
@Service
@ConditionalOnProperty(name = "tienda.correo.proveedor", havingValue = "simulador", matchIfMissing = true)
public class NotificadorCorreoSimulado implements NotificadorCorreo {

	@Override
	public void enviarRecuperacion(String correo, String nombre, String token) {
		log.info("[Correo simulado] Recuperación de contraseña para {} <{}>. Token: {}", nombre, correo, token);
	}

	@Override
	public void enviarVerificacion(String correo, String nombre, String token) {
		log.info("[Correo simulado] Verificación del correo para {} <{}>. Token: {}", nombre, correo, token);
	}

	// Sin el teléfono de quien retira ni las líneas: el log no necesita los datos del pedido (HU-21)
	@Override
	public void enviarPedidoPagado(CorreoDelPedido pedido) {
		log.info("[Correo simulado] Pedido pagado N.º {} para {} <{}>. Total: {}", pedido.numero(), pedido.nombre(),
				pedido.correo(), pedido.total());
	}

	@Override
	public void enviarPagoADevolver(CorreoDelPedido pedido) {
		log.info("[Correo simulado] Pago a devolver N.º {} para {} <{}>. Monto: {}", pedido.numero(), pedido.nombre(),
				pedido.correo(), pedido.total());
	}
}
