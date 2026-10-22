package bo.edu.uagrm.tienda.service.exportar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import bo.edu.uagrm.tienda.service.ReporteCompleto;

// HU-19: el Excel, leído de vuelta con POI
class ExportadorExcelTest {

	private final ExportadorExcel exportador = new ExportadorExcel();
	private final DataFormatter formateador = new DataFormatter();

	private Workbook leer(ReporteCompleto reporte) throws IOException {
		return new XSSFWorkbook(new ByteArrayInputStream(exportador.exportar(reporte)));
	}

	// Todas las filas de una hoja como texto, para buscar lo que dicen
	private List<String> textos(Sheet hoja) {
		List<String> textos = new ArrayList<>();
		for (Row fila : hoja) {
			for (Cell celda : fila) {
				textos.add(formateador.formatCellValue(celda));
			}
		}
		return textos;
	}

	// La primera celda de la columna 0 que dice el texto
	private Row filaQueEmpiezaCon(Sheet hoja, String texto) {
		for (Row fila : hoja) {
			Cell primera = fila.getCell(0);
			if (primera != null && primera.getCellType() == CellType.STRING
					&& primera.getStringCellValue().equals(texto)) {
				return fila;
			}
		}
		throw new AssertionError("No hay una fila «" + texto + "» en " + hoja.getSheetName());
	}

	// HU-19 RF-10: una hoja por sección
	@Test
	void tieneUnaHojaPorSeccion() throws IOException {
		try (Workbook libro = leer(ReporteDePrueba.conVentas())) {
			List<String> nombres = new ArrayList<>();
			libro.forEach(hoja -> nombres.add(hoja.getSheetName()));
			assertThat(nombres).containsExactly("Resumen", "Por fecha", "Por día de la semana", "Por hora",
					"Por categoría", "Por tipo de cliente", "Más vendidos", "Menos vendidos", "Sin venderse",
					"Pedidos perdidos", "Clientes inactivos");
		}
	}

	// HU-19 RF-5
	@Test
	void elResumenTraeElEncabezado() throws IOException {
		try (Workbook libro = leer(ReporteDePrueba.conVentas())) {
			Sheet resumen = libro.getSheet("Resumen");
			assertThat(resumen.getRow(0).getCell(0).getStringCellValue())
					.isEqualTo("Tienda Montero — Reporte de ventas");
			assertThat(textos(resumen)).contains("01/10/2025 al 22/10/2025", "09/09/2025 al 30/09/2025",
					"Contratista", "Más de 60 días", "Fabio Cruz");
			Cell generado = filaQueEmpiezaCon(resumen, "Generado").getCell(1);
			assertThat(DateUtil.isCellDateFormatted(generado)).isTrue();
			assertThat(generado.getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2025, 10, 22, 15, 30));
		}
	}

	// HU-19 RF-10: los importes son números con formato de bolivianos, y la diferencia un porcentaje
	@Test
	void losImportesSonNumerosConFormato() throws IOException {
		try (Workbook libro = leer(ReporteDePrueba.conVentas())) {
			Row ingresos = filaQueEmpiezaCon(libro.getSheet("Resumen"), "Ingresos");
			assertThat(ingresos.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
			assertThat(ingresos.getCell(1).getNumericCellValue()).isEqualTo(970.0);
			assertThat(ingresos.getCell(1).getCellStyle().getDataFormatString()).contains("#,##0.00");
			assertThat(ingresos.getCell(2).getNumericCellValue()).isEqualTo(500.0);
			// (970 − 500) / 500 = 94 %
			assertThat(ingresos.getCell(3).getNumericCellValue()).isEqualTo(0.94);
			assertThat(ingresos.getCell(3).getCellStyle().getDataFormatString()).contains("%");
			Row taladro = filaQueEmpiezaCon(libro.getSheet("Más vendidos"), "HER-0001");
			assertThat(taladro.getCell(3).getNumericCellValue()).isEqualTo(2.0);
			assertThat(taladro.getCell(4).getNumericCellValue()).isEqualTo(900.0);
		}
	}

	// HU-19 RF-10: las fechas son fechas, no texto
	@Test
	void lasFechasSonFechas() throws IOException {
		try (Workbook libro = leer(ReporteDePrueba.conVentas())) {
			Cell dia = libro.getSheet("Por fecha").getRow(filaDeTitulos(libro.getSheet("Por fecha")) + 1).getCell(0);
			assertThat(DateUtil.isCellDateFormatted(dia)).isTrue();
			assertThat(dia.getLocalDateTimeCellValue().toLocalDate()).isEqualTo(ReporteDePrueba.DESDE);
			Cell ultimaCompra = filaQueEmpiezaCon(libro.getSheet("Clientes inactivos"), "Ana Paz").getCell(4);
			assertThat(DateUtil.isCellDateFormatted(ultimaCompra)).isTrue();
		}
	}

	private int filaDeTitulos(Sheet hoja) {
		return filaQueEmpiezaCon(hoja, "Fecha").getRowNum();
	}

	// Caso límite: 366 días van por mes, 13 filas
	@Test
	void unAnoVaPorMes() throws IOException {
		try (Workbook libro = leer(ReporteDePrueba.unAno())) {
			Sheet hoja = libro.getSheet("Por fecha");
			assertThat(textos(hoja)).contains("Ventas por mes", "Mes", "octubre de 2024", "octubre de 2025");
			int titulos = filaQueEmpiezaCon(hoja, "Mes").getRowNum();
			assertThat(hoja.getLastRowNum() - titulos).isEqualTo(13);
		}
	}

	// HU-19 RF-8
	@Test
	void losClientesTraenContactoYAviso() throws IOException {
		try (Workbook libro = leer(ReporteDePrueba.conVentas())) {
			Sheet clientes = libro.getSheet("Clientes inactivos");
			assertThat(textos(clientes)).contains("Contiene datos personales de clientes: uso interno.");
			Row ana = filaQueEmpiezaCon(clientes, "Ana Paz");
			assertThat(ana.getCell(1).getStringCellValue()).isEqualTo("ana@mail.com");
			assertThat(ana.getCell(2).getStringCellValue()).isEqualTo("70000000");
			assertThat(ana.getCell(3).getStringCellValue()).isEqualTo("Contratista");
			assertThat(ana.getCell(6).getNumericCellValue()).isEqualTo(1200.0);
		}
	}

	// HU-19 RF-9: las secciones de ventas lo dicen, con el tipo elegido si hay uno
	@Test
	void sinVentasLoDice() throws IOException {
		try (Workbook libro = leer(ReporteDePrueba.sinVentas())) {
			for (String hoja : List.of("Resumen", "Por fecha", "Por día de la semana", "Por hora", "Por categoría",
					"Por tipo de cliente", "Más vendidos")) {
				assertThat(textos(libro.getSheet(hoja))).as(hoja).contains("No hubo ventas en este período.");
			}
			assertThat(textos(libro.getSheet("Pedidos perdidos")))
					.contains("No hubo pedidos perdidos en este período.");
			assertThat(textos(libro.getSheet("Clientes inactivos")))
					.contains("Ningún cliente dejó de comprar hace más de 30 días.");
		}
	}

	// HU-19 RF-9: lo que no depende del período sale igual
	@Test
	void sinVenderseIgualSale() throws IOException {
		try (Workbook libro = leer(ReporteDePrueba.sinVentas())) {
			assertThat(textos(libro.getSheet("Sin venderse"))).contains("Rastrillo", "Nunca", "precio");
			assertThat(textos(libro.getSheet("Menos vendidos"))).contains("Sierra");
		}
	}

	// HU-19 RF-10: sin costo lo dice; un margen negativo queda negativo
	@Test
	void margenSinCostoYNegativo() throws IOException {
		try (Workbook libro = leer(ReporteDePrueba.conVentas())) {
			Sheet hoja = libro.getSheet("Más vendidos");
			assertThat(filaQueEmpiezaCon(hoja, "HER-0002").getCell(5).getStringCellValue()).isEqualTo("sin costo");
			assertThat(filaQueEmpiezaCon(hoja, "HER-0003").getCell(5).getNumericCellValue()).isEqualTo(-5.0);
		}
	}

	// Caso límite: un nombre que empieza con = es texto, no una fórmula
	@Test
	void unNombreConIgualQuedaComoTexto() throws IOException {
		try (Workbook libro = leer(ReporteDePrueba.conVentas())) {
			Cell nombre = filaQueEmpiezaCon(libro.getSheet("Más vendidos"), "HER-0003").getCell(1);
			assertThat(nombre.getCellType()).isEqualTo(CellType.STRING);
			assertThat(nombre.getStringCellValue()).isEqualTo("=1+1");
		}
	}

	// Caso límite: acentos, ñ y emoji vuelven iguales
	@Test
	void acentosYEmojiVuelvenIguales() throws IOException {
		try (Workbook libro = leer(ReporteDePrueba.conVentas())) {
			assertThat(filaQueEmpiezaCon(libro.getSheet("Más vendidos"), "HER-0002").getCell(1).getStringCellValue())
					.isEqualTo("Llave ñandú 🔧");
		}
	}
}
