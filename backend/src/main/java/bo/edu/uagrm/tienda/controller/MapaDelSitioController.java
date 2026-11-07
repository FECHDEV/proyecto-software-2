package bo.edu.uagrm.tienda.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import bo.edu.uagrm.tienda.config.CorreoProperties;
import bo.edu.uagrm.tienda.service.CatalogoService;
import lombok.RequiredArgsConstructor;

// El mapa del sitio para los buscadores (plan 2026-11-07-seo): la portada, el catálogo y cada producto que el
// catálogo muestra. nginx lo sirve en /sitemap.xml y robots.txt lo anuncia. Sin fechas: Google las ignora si no son
// exactas, y la del alta no dice cuándo cambió el producto
@RestController
@RequiredArgsConstructor
public class MapaDelSitioController {

	private final CatalogoService catalogoService;
	// La dirección pública de la tienda (TIENDA_SITIO_URL), la misma de los enlaces de los correos; nunca el Host de
	// la petición, que lo manda el cliente
	private final CorreoProperties sitio;

	@GetMapping(value = "/api/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
	public ResponseEntity<String> mapa() {
		// Con la codificación, escapa solo & < > " ': las entidades con nombre de HTML no existen en XML
		String origen = HtmlUtils.htmlEscape(sitio.urlDelSitio(), "UTF-8");
		List<String> rutas = new ArrayList<>(List.of("/", "/catalogo"));
		catalogoService.idsPublicos().forEach(id -> rutas.add("/producto/" + id));
		StringBuilder xml = new StringBuilder("""
				<?xml version="1.0" encoding="UTF-8"?>
				<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
				""");
		rutas.forEach(ruta -> xml.append("  <url><loc>").append(origen).append(ruta).append("</loc></url>\n"));
		xml.append("</urlset>\n");
		// Una hora: los buscadores lo piden poco, y un producto nuevo no apura
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
				.body(xml.toString());
	}
}
