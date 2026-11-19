package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import bo.edu.uagrm.tienda.entity.TipoCliente;

// Un cliente que compró en el período (HU-18 RF-23 a RF-25), con el contacto para atenderlo: solo lo ven el Contador
// y el Administrador. diasEntreCompras es nulo con una sola compra (RF-24); activa es falso si hoy está desactivada
public record ClienteQueCompra(Long idUsuario, String nombre, String apellido, String correo, String telefono,
		TipoCliente tipo, boolean activa, long compras, BigDecimal total, BigDecimal promedio, LocalDateTime primera,
		LocalDateTime ultima, BigDecimal diasEntreCompras, long enLinea, long enTienda) {
}
