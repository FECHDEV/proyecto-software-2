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
}
