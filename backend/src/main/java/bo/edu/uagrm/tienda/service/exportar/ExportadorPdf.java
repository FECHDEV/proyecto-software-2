package bo.edu.uagrm.tienda.service.exportar;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import bo.edu.uagrm.tienda.service.ReporteCompleto;
import bo.edu.uagrm.tienda.service.exportar.Celda.Texto;
import bo.edu.uagrm.tienda.service.exportar.Seccion.Hoja;

// El reporte en PDF (HU-19 RF-11), para entregar o imprimir: A4 apaisado (la tabla de sin venderse tiene diez
// columnas), las secciones una tras otra, la fila de títulos repetida en cada página y «Página N de M» al pie.
// Helvetica trae los acentos, la ñ, «», — y ×; lo que no tiene (un emoji) se omite sin romper el archivo
@Component
public class ExportadorPdf {

	private static final float MARGEN = 36;
	// Lo que ocupan el título de una sección y un par de filas: con menos lugar, la sección empieza en otra página
	// para que el título no quede solo al pie
	private static final float LUGAR_MINIMO = 90;
	private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Font.NORMAL);
	private static final Font SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.NORMAL);
	private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL);
	private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL);

	public byte[] exportar(ReporteCompleto reporte) {
		List<Hoja> hojas = SeccionesDelReporte.de(reporte);
		// Dos pasadas: la primera cuenta las páginas para poder decir «de M» en la segunda
		Armado borrador = armar(hojas, 0);
		return armar(hojas, borrador.paginas()).pdf();
	}

	private record Armado(byte[] pdf, int paginas) {
	}

	private static Armado armar(List<Hoja> hojas, int total) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		Document documento = new Document(PageSize.A4.rotate(), MARGEN, MARGEN, MARGEN, MARGEN + 12);
		Pie pie = new Pie(total);
		try {
			PdfWriter escritor = PdfWriter.getInstance(documento, salida);
			escritor.setPageEvent(pie);
			documento.open();
			boolean primera = true;
			for (Hoja hoja : hojas) {
				for (Seccion seccion : hoja.secciones()) {
					escribir(documento, escritor, seccion, primera);
					primera = false;
				}
			}
			documento.close();
		} catch (DocumentException e) {
			throw new IllegalStateException("No se pudo armar el PDF del reporte", e);
		}
		return new Armado(salida.toByteArray(), pie.paginas);
	}

	private static void escribir(Document documento, PdfWriter escritor, Seccion seccion, boolean primera)
			throws DocumentException {
		if (!primera && escritor.getVerticalPosition(true) - documento.bottom() < LUGAR_MINIMO) {
			documento.newPage();
		}
		Paragraph titulo = new Paragraph(seccion.titulo(), primera ? TITULO : SUBTITULO);
		titulo.setSpacingBefore(primera ? 0 : 14);
		titulo.setSpacingAfter(4);
		documento.add(titulo);
		for (String nota : seccion.notas()) {
			documento.add(new Paragraph(nota, NORMAL));
		}
		if (seccion.filas().isEmpty()) {
			if (!seccion.vacio().isEmpty()) {
				documento.add(new Paragraph(seccion.vacio(), NORMAL));
			}
			return;
		}
		boolean conTitulos = !seccion.columnas().isEmpty();
		int columnas = conTitulos ? seccion.columnas().size() : seccion.filas().getFirst().size();
		PdfPTable tabla = new PdfPTable(columnas);
		// Los pares «dato, valor» no necesitan el ancho de la página
		tabla.setWidthPercentage(conTitulos ? 100 : 55);
		tabla.setHorizontalAlignment(Element.ALIGN_LEFT);
		tabla.setSpacingBefore(4);
		if (conTitulos) {
			for (String columna : seccion.columnas()) {
				PdfPCell celda = new PdfPCell(new Phrase(columna, NEGRITA));
				celda.setGrayFill(0.9f);
				celda.setPadding(4);
				tabla.addCell(celda);
			}
			tabla.setHeaderRows(1);
		}
		for (List<Celda> fila : seccion.filas()) {
			for (Celda valor : fila) {
				PdfPCell celda = new PdfPCell(new Phrase(valor.texto(), NORMAL));
				celda.setPadding(4);
				// En las tablas, los números a la derecha para compararlos de arriba abajo; en los pares, todo a la
				// izquierda
				celda.setHorizontalAlignment(
						!conTitulos || valor instanceof Texto ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
				tabla.addCell(celda);
			}
		}
		documento.add(tabla);
	}

	// «Página N de M» al pie de cada página; cuenta las páginas para la segunda pasada
	private static final class Pie extends PdfPageEventHelper {

		private final int total;
		private int paginas;

		Pie(int total) {
			this.total = total;
		}

		@Override
		public void onEndPage(PdfWriter escritor, Document documento) {
			paginas = escritor.getPageNumber();
			if (total == 0) {
				return;
			}
			PdfContentByte contenido = escritor.getDirectContent();
			BaseFont fuente = NORMAL.getCalculatedBaseFont(false);
			contenido.beginText();
			contenido.setFontAndSize(fuente, 8);
			contenido.showTextAligned(Element.ALIGN_RIGHT, "Página %d de %d".formatted(paginas, total),
					documento.right(), documento.bottom() - 20, 0);
			contenido.endText();
		}
	}
}
