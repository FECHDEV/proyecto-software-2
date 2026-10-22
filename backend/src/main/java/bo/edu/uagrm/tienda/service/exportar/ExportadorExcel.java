package bo.edu.uagrm.tienda.service.exportar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import bo.edu.uagrm.tienda.service.ReporteCompleto;
import bo.edu.uagrm.tienda.service.exportar.Celda.Dinero;
import bo.edu.uagrm.tienda.service.exportar.Celda.Fecha;
import bo.edu.uagrm.tienda.service.exportar.Celda.FechaHora;
import bo.edu.uagrm.tienda.service.exportar.Celda.Numero;
import bo.edu.uagrm.tienda.service.exportar.Celda.Porcentaje;
import bo.edu.uagrm.tienda.service.exportar.Celda.Texto;
import bo.edu.uagrm.tienda.service.exportar.Seccion.Hoja;

// El reporte en Excel (HU-19 RF-10): una hoja por sección, con los números y las fechas como tales y el formato de
// bolivianos, para seguir trabajándolos. El texto va siempre como texto: un nombre «=1+1» no es una fórmula
@Component
public class ExportadorExcel {

	// Ancho de columna en caracteres: lo bastante para leer sin medir fuentes, que en el contenedor no hay
	private static final int ANCHO_MINIMO = 10;
	private static final int ANCHO_MAXIMO = 50;

	public byte[] exportar(ReporteCompleto reporte) {
		try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			Estilos estilos = new Estilos(libro);
			for (Hoja hoja : SeccionesDelReporte.de(reporte)) {
				escribir(libro.createSheet(hoja.nombre()), hoja.secciones(), estilos);
			}
			libro.write(salida);
			return salida.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void escribir(Sheet hoja, List<Seccion> secciones, Estilos estilos) {
		int fila = 0;
		int[] anchos = new int[secciones.stream().mapToInt(ExportadorExcel::columnas).max().orElse(0)];
		for (Seccion seccion : secciones) {
			if (fila > 0) {
				fila++;
			}
			texto(hoja.createRow(fila++), 0, seccion.titulo(), estilos.titulo);
			for (String nota : seccion.notas()) {
				texto(hoja.createRow(fila++), 0, nota, null);
			}
			if (seccion.filas().isEmpty()) {
				if (!seccion.vacio().isEmpty()) {
					texto(hoja.createRow(fila++), 0, seccion.vacio(), null);
				}
				continue;
			}
			if (!seccion.columnas().isEmpty()) {
				// La fila de títulos queda fija si es la única tabla de la hoja
				if (secciones.size() == 1) {
					hoja.createFreezePane(0, fila + 1);
				}
				Row titulos = hoja.createRow(fila++);
				for (int columna = 0; columna < seccion.columnas().size(); columna++) {
					texto(titulos, columna, seccion.columnas().get(columna), estilos.negrita);
					medir(anchos, columna, seccion.columnas().get(columna));
				}
			}
			for (List<Celda> valores : seccion.filas()) {
				Row row = hoja.createRow(fila++);
				for (int columna = 0; columna < valores.size(); columna++) {
					celda(row.createCell(columna), valores.get(columna), estilos);
					medir(anchos, columna, valores.get(columna).texto());
				}
			}
		}
		for (int columna = 0; columna < anchos.length; columna++) {
			if (anchos[columna] == 0) {
				continue;
			}
			hoja.setColumnWidth(columna, Math.clamp(anchos[columna] + 2L, ANCHO_MINIMO, ANCHO_MAXIMO) * 256);
		}
	}

	// Las columnas más anchas de la sección: los títulos, o la fila más larga si son pares «dato, valor»
	private static int columnas(Seccion seccion) {
		return Math.max(seccion.columnas().size(),
				seccion.filas().stream().mapToInt(List::size).max().orElse(0));
	}

	private static void medir(int[] anchos, int columna, String texto) {
		anchos[columna] = Math.max(anchos[columna], texto.length());
	}

	private static void texto(Row fila, int columna, String valor, CellStyle estilo) {
		Cell celda = fila.createCell(columna);
		celda.setCellValue(valor);
		if (estilo != null) {
			celda.setCellStyle(estilo);
		}
	}

	private static void celda(Cell celda, Celda valor, Estilos estilos) {
		switch (valor) {
			case Texto texto -> celda.setCellValue(texto.valor());
			case Dinero dinero -> {
				celda.setCellValue(dinero.valor().doubleValue());
				celda.setCellStyle(estilos.dinero);
			}
			case Numero numero -> {
				celda.setCellValue(numero.valor().doubleValue());
				celda.setCellStyle(numero.decimales() == 0 ? estilos.entero : estilos.decimal);
			}
			// El valor llega en por ciento y Excel guarda la fracción: 94 % es 0,94
			case Porcentaje porcentaje -> {
				celda.setCellValue(porcentaje.valor().movePointLeft(2).doubleValue());
				celda.setCellStyle(estilos.porcentaje);
			}
			case Fecha fecha -> {
				celda.setCellValue(fecha.valor());
				celda.setCellStyle(estilos.fecha);
			}
			case FechaHora fechaHora -> {
				celda.setCellValue(fechaHora.valor());
				celda.setCellStyle(estilos.fechaHora);
			}
		}
	}

	private static final class Estilos {

		private final CellStyle titulo;
		private final CellStyle negrita;
		private final CellStyle dinero;
		private final CellStyle entero;
		private final CellStyle decimal;
		private final CellStyle porcentaje;
		private final CellStyle fecha;
		private final CellStyle fechaHora;

		Estilos(XSSFWorkbook libro) {
			Font grande = libro.createFont();
			grande.setBold(true);
			grande.setFontHeightInPoints((short) 13);
			titulo = libro.createCellStyle();
			titulo.setFont(grande);
			Font gruesa = libro.createFont();
			gruesa.setBold(true);
			negrita = libro.createCellStyle();
			negrita.setFont(gruesa);
			dinero = formato(libro, "\"Bs\" #,##0.00");
			entero = formato(libro, "#,##0");
			decimal = formato(libro, "#,##0.00");
			porcentaje = formato(libro, "0.0%");
			fecha = formato(libro, "dd/mm/yyyy");
			fechaHora = formato(libro, "dd/mm/yyyy hh:mm");
		}

		private static CellStyle formato(XSSFWorkbook libro, String formato) {
			CellStyle estilo = libro.createCellStyle();
			estilo.setDataFormat(libro.createDataFormat().getFormat(formato));
			return estilo;
		}
	}
}
