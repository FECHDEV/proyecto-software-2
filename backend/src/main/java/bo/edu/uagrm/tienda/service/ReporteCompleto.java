package bo.edu.uagrm.tienda.service;

import java.time.LocalDateTime;
import java.util.List;

import bo.edu.uagrm.tienda.dto.reportes.ClienteInactivo;
import bo.edu.uagrm.tienda.dto.reportes.ProductoPerdido;
import bo.edu.uagrm.tienda.dto.reportes.ProductoSinVenta;
import bo.edu.uagrm.tienda.dto.reportes.ProductoVendido;
import bo.edu.uagrm.tienda.dto.reportes.ResumenDePerdidos;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse;
import bo.edu.uagrm.tienda.entity.TipoCliente;

// Todo lo que se exporta (HU-19): las secciones de la pantalla con las listas enteras (RF-6, RF-7), los filtros con
// que se pidió y quién lo pidió y cuándo (RF-5). tipo es nulo con todos los tipos
public record ReporteCompleto(VentasResponse ventas, ResumenDePerdidos perdidos, List<ProductoVendido> masVendidos,
		List<ProductoVendido> menosVendidos, List<ProductoSinVenta> sinVenta, List<ProductoPerdido> productosPerdidos,
		List<ClienteInactivo> clientesInactivos, TipoCliente tipo, int dias, LocalDateTime generado, String autor) {
}
