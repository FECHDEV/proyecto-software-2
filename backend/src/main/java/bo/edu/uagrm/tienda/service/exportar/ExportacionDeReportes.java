package bo.edu.uagrm.tienda.service.exportar;

import org.springframework.stereotype.Service;

import bo.edu.uagrm.tienda.dto.reportes.VentasResponse.PeriodoResponse;
import bo.edu.uagrm.tienda.entity.TipoCliente;
import bo.edu.uagrm.tienda.service.ReporteCompleto;
import bo.edu.uagrm.tienda.service.ReportesService;
import lombok.RequiredArgsConstructor;

// Exporta los reportes (HU-19): el formato se valida antes de consultar (RF-3), el reporte sale de las mismas
// consultas que la pantalla y el archivo se nombra con el período (RF-4)
@Service
@RequiredArgsConstructor
public class ExportacionDeReportes {

	private final ReportesService reportesService;
	private final ExportadorExcel exportadorExcel;
	private final ExportadorPdf exportadorPdf;

	public ArchivoExportado exportar(String formato, String desde, String hasta, TipoCliente tipo, String dias,
			Long idUsuario) {
		FormatoExportacion elegido = FormatoExportacion.de(formato);
		ReporteCompleto reporte = reportesService.completo(desde, hasta, tipo, dias, idUsuario);
		byte[] contenido = elegido == FormatoExportacion.XLSX ? exportadorExcel.exportar(reporte)
				: exportadorPdf.exportar(reporte);
		PeriodoResponse periodo = reporte.ventas().periodo();
		return new ArchivoExportado("reporte-ventas-%s-a-%s.%s".formatted(periodo.desde(), periodo.hasta(),
				elegido.extension()), elegido.tipoDeContenido(), contenido);
	}

	public record ArchivoExportado(String nombre, String tipoDeContenido, byte[] contenido) {
	}
}
