package bo.edu.uagrm.tienda.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import bo.edu.uagrm.tienda.config.SitioProperties;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;

// El mapa del sitio para los buscadores (plan 2026-11-07-seo): la portada, el catálogo y cada producto que el
// catálogo muestra. nginx lo sirve en /sitemap.xml y robots.txt lo anuncia. Sin fechas: Google las ignora si no son
// exactas, y la del alta no dice cuándo cambió el producto
@RestController
@RequiredArgsConstructor
@EnableConfigurationProperties(SitioProperties.class)
public class MapaDelSitioController {

	private final ProductoRepository productoRepository;
	private final SitioProperties sitio;

	@GetMapping(value = "/api/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
	@Transactional(readOnly = true)
	public ResponseEntity<String> mapa() {
		String origen = HtmlUtils.htmlEscape(sitio.url());
		StringBuilder xml = new StringBuilder("""
				<?xml version="1.0" encoding="UTF-8"?>
				<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
				""");
		List<String> rutas = new ArrayList<>(List.of("/", "/catalogo"));
		productoRepository.idsPublicos().forEach(id -> rutas.add("/producto/" + id));
		rutas.forEach(ruta -> xml.append("  <url><loc>").append(origen).append(ruta).append("</loc></url>\n"));
		xml.append("</urlset>\n");
		// Una hora: los buscadores lo piden poco, y un producto nuevo no apura
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
				.body(xml.toString());
	}
}
