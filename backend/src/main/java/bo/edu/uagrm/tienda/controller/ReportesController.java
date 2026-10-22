package bo.edu.uagrm.tienda.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bo.edu.uagrm.tienda.dto.PaginaResponse;
import bo.edu.uagrm.tienda.dto.reportes.ClienteInactivo;
import bo.edu.uagrm.tienda.dto.reportes.ProductoPerdido;
import bo.edu.uagrm.tienda.dto.reportes.ProductoSinVenta;
import bo.edu.uagrm.tienda.dto.reportes.ProductoVendido;
import bo.edu.uagrm.tienda.dto.reportes.ResumenDePerdidos;
import bo.edu.uagrm.tienda.dto.reportes.VentasResponse;
import bo.edu.uagrm.tienda.entity.TipoCliente;
import bo.edu.uagrm.tienda.service.ReportesService;
import bo.edu.uagrm.tienda.service.exportar.ExportacionDeReportes;
import bo.edu.uagrm.tienda.service.exportar.ExportacionDeReportes.ArchivoExportado;
import lombok.RequiredArgsConstructor;

// Los reportes (HU-18), solo para el Contador y el Administrador (SecurityConfig, RF-1). Las fechas llegan como texto:
// Periodo las lee y responde 400 PERIODO_INVALIDO a lo que no sirve (RF-3). Una página que no es un número es la
// primera; una después de la última, la última
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReportesController {

	private final ReportesService reportesService;
	private final ExportacionDeReportes exportacionDeReportes;

	@GetMapping("/ventas")
	public VentasResponse ventas(@RequestParam(required = false) String desde,
			@RequestParam(required = false) String hasta, @RequestParam(required = false) TipoCliente tipo) {
		return reportesService.ventas(desde, hasta, tipo);
	}

	// orden=MENOS son los menos vendidos (RF-12); cualquier otro, los más vendidos (RF-11)
	@GetMapping("/productos")
	public PaginaResponse<ProductoVendido> productos(@RequestParam(required = false) String desde,
			@RequestParam(required = false) String hasta, @RequestParam(required = false) TipoCliente tipo,
			@RequestParam(required = false) String orden, @RequestParam(required = false) String pagina) {
		return reportesService.productos(desde, hasta, tipo, "MENOS".equals(orden), Paginas.numeroDePagina(pagina));
	}

	@GetMapping("/sin-venta")
	public PaginaResponse<ProductoSinVenta> sinVenta(@RequestParam(required = false) String dias,
			@RequestParam(required = false) String pagina) {
		return reportesService.sinVenta(dias, Paginas.numeroDePagina(pagina));
	}

	@GetMapping("/clientes-inactivos")
	public PaginaResponse<ClienteInactivo> clientesInactivos(@RequestParam(required = false) String dias,
			@RequestParam(required = false) TipoCliente tipo, @RequestParam(required = false) String pagina) {
		return reportesService.clientesInactivos(dias, tipo, Paginas.numeroDePagina(pagina));
	}

	// Las cifras de los pedidos perdidos, sin el resumen entero (RF-17)
	@GetMapping("/perdidos/resumen")
	public ResumenDePerdidos resumenDePerdidos(@RequestParam(required = false) String desde,
			@RequestParam(required = false) String hasta, @RequestParam(required = false) TipoCliente tipo) {
		return reportesService.resumenDePerdidos(desde, hasta, tipo);
	}

	@GetMapping("/perdidos")
	public PaginaResponse<ProductoPerdido> perdidos(@RequestParam(required = false) String desde,
			@RequestParam(required = false) String hasta, @RequestParam(required = false) TipoCliente tipo,
			@RequestParam(required = false) String pagina) {
		return reportesService.perdidos(desde, hasta, tipo, Paginas.numeroDePagina(pagina));
	}

	// HU-19: todo el reporte en un archivo, como descarga (RF-4). no-store porque trae datos de clientes (RF-8)
	@GetMapping("/exportar")
	public ResponseEntity<byte[]> exportar(@RequestParam(required = false) String formato,
			@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
			@RequestParam(required = false) TipoCliente tipo, @RequestParam(required = false) String dias,
			@AuthenticationPrincipal Long idUsuario) {
		ArchivoExportado archivo = exportacionDeReportes.exportar(formato, desde, hasta, tipo, dias, idUsuario);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(archivo.tipoDeContenido()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
				.cacheControl(CacheControl.noStore()).body(archivo.contenido());
	}
}
