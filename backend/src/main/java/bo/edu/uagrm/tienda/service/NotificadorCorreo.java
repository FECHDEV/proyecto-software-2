package bo.edu.uagrm.tienda.service;

// Los correos del sistema (CLAUDE.md → 'Correos'). Cada correo nuevo es un método más, con su versión en el simulador
// y en el adaptador SMTP
public interface NotificadorCorreo {

	void enviarRecuperacion(String correo, String nombre, String token);
}
