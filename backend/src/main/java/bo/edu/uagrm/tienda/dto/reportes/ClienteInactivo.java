package bo.edu.uagrm.tienda.dto.reportes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import bo.edu.uagrm.tienda.entity.TipoCliente;

// Un cliente que dejó de comprar (HU-18 RF-16), con el contacto para llamarlo: solo lo ven el Contador y el
// Administrador
public record ClienteInactivo(Long idUsuario, String nombre, String apellido, String correo, String telefono,
		TipoCliente tipo, LocalDateTime ultimaCompra, long pedidos, BigDecimal total) {
}
