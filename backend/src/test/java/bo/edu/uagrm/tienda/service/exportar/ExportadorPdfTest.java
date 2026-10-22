package bo.edu.uagrm.tienda.service.exportar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import bo.edu.uagrm.tienda.dto.reportes.ProductoVendido;
import bo.edu.uagrm.tienda.service.ReporteCompleto;

// HU-19: el PDF, leído de vuelta con el extractor de texto de OpenPDF
class ExportadorPdfTest {

	private final ExportadorPdf exportador = new ExportadorPdf();

	// El texto de cada página
	private List<String> paginas(byte[] pdf) throws IOException {
		try (PdfReader lector = new PdfReader(pdf)) {
			PdfTextExtractor extractor = new PdfTextExtractor(lector);
			List<String> paginas = new ArrayList<>();
			for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
				paginas.add(extractor.getTextFromPage(pagina));
			}
			return paginas;
		}
	}

	private static ReporteCompleto conSesentaProductos() {
		List<ProductoVendido> muchos = IntStream.rangeClosed(1, 60)
				.mapToObj(i -> ReporteDePrueba.vendido((long) i, "Producto %02d".formatted(i), "1", "%d.00".formatted(100 - i),
						null))
				.toList();
		return ReporteDePrueba.conMasVendidos(muchos);
	}

	// HU-19 RF-5, RF-11
	@Test
	void empiezaConPdfYTraeElEncabezado() throws IOException {
		byte[] pdf = exportador.exportar(ReporteDePrueba.conVentas());

		assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
		String primera = paginas(pdf).getFirst();
		assertThat(primera).contains("Tienda Montero — Reporte de ventas", "01/10/2025 al 22/10/2025",
				"09/09/2025 al 30/09/2025", "Contratista", "Más de 60 días", "22/10/2025 15:30", "Fabio Cruz");
	}

	// HU-19 RF-11
	@Test
	void numeraLasPaginas() throws IOException {
		List<String> paginas = paginas(exportador.exportar(conSesentaProductos()));

		assertThat(paginas.size()).isGreaterThan(2);
		assertThat(paginas.getFirst()).contains("Página 1 de " + paginas.size());
		assertThat(paginas.get(1)).contains("Página 2 de " + paginas.size());
	}

	// HU-19 RF-7, RF-11: la tabla de más vendidos sigue en otra página, con sus títulos y las 60 filas
	@Test
	void repiteLosTitulosEnLaPaginaSiguiente() throws IOException {
		List<String> paginas = paginas(exportador.exportar(conSesentaProductos()));

		int primera = indiceDe(paginas, "Producto 01");
		int ultima = indiceDe(paginas, "Producto 60");
		assertThat(ultima).isGreaterThan(primera);
		// La página donde sigue la tabla no repite el título de la sección, pero sí la fila de títulos
		assertThat(paginas.get(ultima)).doesNotContain("Productos más vendidos").contains("Código", "Importe");
	}

	private static int indiceDe(List<String> paginas, String texto) {
		return IntStream.range(0, paginas.size()).filter(i -> paginas.get(i).contains(texto)).findFirst()
				.orElseThrow();
	}

	// HU-19 RF-6, RF-8
	@Test
	void traeLasSeccionesYElAviso() throws IOException {
		String todo = String.join("\n", paginas(exportador.exportar(ReporteDePrueba.conVentas())));

		assertThat(todo).contains("Cifras del período", "Ventas por día", "Ventas por día de la semana",
				"Ventas por hora del pago", "Ventas por categoría", "Ventas por tipo de cliente",
				"Productos más vendidos", "Productos menos vendidos", "Productos sin venderse hace más de 60 días",
				"Pedidos perdidos", "Productos de los pedidos perdidos", "Clientes que no compran hace más de 60 días",
				"Contiene datos personales de clientes: uso interno.", "ana@mail.com", "70000000", "Bs 970,00",
				"sin costo");
	}

	// HU-19 RF-9
	@Test
	void sinVentasLoDice() throws IOException {
		String todo = String.join("\n", paginas(exportador.exportar(ReporteDePrueba.sinVentas())));

		assertThat(todo).contains("No hubo ventas en este período.", "No hubo pedidos perdidos en este período.",
				"Rastrillo", "Nunca");
	}

	// Caso límite: la ñ y los acentos se ven; el emoji, que la fuente no tiene, no rompe el archivo
	@Test
	void acentosYEmojiNoLoRompen() throws IOException {
		String todo = String.join("\n", paginas(exportador.exportar(ReporteDePrueba.conVentas())));

		assertThat(todo).contains("Llave ñandú", "Miércoles", "Público general");
	}
}
