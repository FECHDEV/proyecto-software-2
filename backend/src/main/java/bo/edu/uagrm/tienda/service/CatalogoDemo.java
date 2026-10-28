package bo.edu.uagrm.tienda.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;

import bo.edu.uagrm.tienda.config.AdministradorInicialProperties;
import bo.edu.uagrm.tienda.config.CatalogoDemoProperties;
import bo.edu.uagrm.tienda.dto.CategoriaRequest;
import bo.edu.uagrm.tienda.dto.EntradaRequest;
import bo.edu.uagrm.tienda.dto.ProductoRequest;
import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.exception.ImagenConDemasiadosPixelesException;
import bo.edu.uagrm.tienda.exception.ImagenInvalidaException;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

// Carga el catálogo de demostración (issue #25) al arrancar, solo con TIENDA_DEMO_DIRECTORIO y solo si todavía no
// hay productos. catalogo.csv lleva una fila por producto, con estas columnas:
//   categoria,nombre,descripcion,precio_bs,stock_inicial,imagenes,modelo_3d,destacado,unidad
// El lector es simple: separa por comas, sin comillas, así que ningún campo puede llevar una coma. El precio y el
// stock van con punto decimal; imagenes, los archivos de la carpeta separados por '|'; modelo_3d, un .glb de la
// carpeta o vacío (HU-15); destacado, «si» o vacío; unidad, una de UnidadVenta o vacía (UNIDAD). Todo pasa por los
// servicios y las validaciones de la gestión. Si una fila tiene errores, se informan todas
// con su número (la 1 es el encabezado) y no se carga nada: una carga a medias ya no se podría completar, porque
// con productos en la base no se vuelve a cargar. Un fallo durante la carga la revierte entera; en los dos casos la
// aplicación arranca igual, con el catálogo vacío (decisiones.md → Catálogo de demostración)
@Slf4j
@Service
@EnableConfigurationProperties(CatalogoDemoProperties.class)
public class CatalogoDemo implements ApplicationRunner {

	static final String ENCABEZADO =
			"categoria,nombre,descripcion,precio_bs,stock_inicial,imagenes,modelo_3d,destacado,unidad";
	private static final int COLUMNAS = 9;
	private static final String NOTA_STOCK = "Catálogo de demostración";
	// La categoría todavía no existe al validar la fila, y el precio puede no ser un número: @NotNull solo necesita un
	// valor, y el error del precio ya se informó
	private static final Long CATEGORIA_POR_RESOLVER = 0L;
	private static final BigDecimal PRECIO_POR_RESOLVER = BigDecimal.ONE;
	// Lo que FormatoImagen necesita leer para reconocer el formato
	private static final int BYTES_DE_FIRMA = 12;
	private static final Pattern NUMERO = Pattern.compile("\\d{1,9}(\\.\\d{1,9})?");

	private final CatalogoDemoProperties propiedades;
	private final AdministradorInicialProperties administrador;
	private final CategoriaService categoriaService;
	private final ProductoService productoService;
	private final InventarioService inventarioService;
	private final ProductoRepository productoRepository;
	private final CategoriaRepository categoriaRepository;
	private final UsuarioRepository usuarioRepository;
	private final Validator validator;
	private final TransactionTemplate transaccion;
	private final Achicador achicador;

	public CatalogoDemo(CatalogoDemoProperties propiedades, AdministradorInicialProperties administrador,
			CategoriaService categoriaService, ProductoService productoService, InventarioService inventarioService,
			ProductoRepository productoRepository, CategoriaRepository categoriaRepository,
			UsuarioRepository usuarioRepository, Validator validator, TransactionTemplate transaccion,
			Achicador achicador) {
		this.propiedades = propiedades;
		this.administrador = administrador;
		this.categoriaService = categoriaService;
		this.productoService = productoService;
		this.inventarioService = inventarioService;
		this.productoRepository = productoRepository;
		this.categoriaRepository = categoriaRepository;
		this.usuarioRepository = usuarioRepository;
		this.validator = validator;
		this.transaccion = transaccion;
		this.achicador = achicador;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		if (!propiedades.activa()) {
			return;
		}
		// Aunque ya haya productos: una variable mal configurada se ve en el primer arranque, no al vaciar la base
		Set<ConstraintViolation<CatalogoDemoProperties>> errores = validator.validate(propiedades);
		if (!errores.isEmpty()) {
			throw new IllegalStateException("No se puede cargar el catálogo de demostración: "
					+ errores.stream().map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining("; ")));
		}
		if (productoRepository.count() > 0) {
			log.info("Catálogo de demostración: ya hay productos en la base, no se carga nada.");
			return;
		}
		Path carpeta = carpetaReal();
		List<String> problemas = new ArrayList<>();
		// Una categoría oculta no muestra sus productos: cargarlos ahí dejaría el catálogo vacío sin aviso
		Set<String> ocultas = categoriaRepository.findAll().stream().filter(c -> !c.isVisible())
				.map(Categoria::getNombreClave).collect(Collectors.toSet());
		List<Fila> filas = leer(carpeta, ocultas, problemas);
		Optional<Usuario> admin = administradorActivo();
		if (admin.isEmpty() && filas.stream().anyMatch(fila -> fila.stock().signum() > 0)) {
			problemas.add("el stock inicial se registra a nombre de un administrador activo, y no hay ninguno");
		}
		if (!problemas.isEmpty()) {
			problemas.forEach(problema -> log.warn("Catálogo de demostración: {}", problema));
			log.error("No se cargó el catálogo de demostración: {} errores en {}. Corríjalos y vuelva a arrancar.",
					problemas.size(), carpeta.resolve(CatalogoDemoProperties.CATALOGO));
			return;
		}
		if (filas.isEmpty()) {
			log.info("Catálogo de demostración: {} no tiene productos.", CatalogoDemoProperties.CATALOGO);
			return;
		}
		// Todo o nada: si algo falla, la transacción se revierte y ArchivosAlTerminar borra las imágenes guardadas
		Integer categoriasNuevas;
		try {
			categoriasNuevas = transaccion.execute(estado -> cargar(filas, admin.orElse(null)));
		} catch (RuntimeException e) {
			log.error("No se cargó el catálogo de demostración: falló la carga y se revirtió entera. {}",
					e.getMessage(), e);
			return;
		}
		log.info("Catálogo de demostración cargado: {} productos y {} categorías nuevas.", filas.size(),
				categoriasNuevas);
	}

	// Ya validada: es una carpeta con el catálogo. Con su camino real se reconocen los enlaces que salen de ella
	private Path carpetaReal() {
		try {
			return propiedades.directorio().toRealPath();
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo abrir TIENDA_DEMO_DIRECTORIO", e);
		}
	}

	// La cuenta de TIENDA_ADMIN_CORREO si sigue siendo de un administrador activo; si no, otro administrador activo.
	// Nunca un cliente ni una cuenta desactivada
	private Optional<Usuario> administradorActivo() {
		return usuarioRepository.findByCorreo(administrador.correo())
				.filter(usuario -> usuario.getRol() == Rol.ADMINISTRADOR && usuario.getEstado() == EstadoCuenta.ACTIVA)
				.or(() -> usuarioRepository.findFirstByRolAndEstadoOrderByIdUsuario(Rol.ADMINISTRADOR,
						EstadoCuenta.ACTIVA));
	}

	private int cargar(List<Fila> filas, Usuario admin) {
		Map<String, Long> categorias = new HashMap<>();
		categoriaRepository.findAll().forEach(c -> categorias.put(c.getNombreClave(), c.getIdCategoria()));
		int nuevas = 0;
		for (Fila fila : filas) {
			// Lo que solo rechaza un servicio también dice en qué fila está
			try {
				String clave = Categoria.clave(fila.categoria());
				Long idCategoria = categorias.get(clave);
				if (idCategoria == null) {
					idCategoria = categoriaService.crear(new CategoriaRequest(fila.categoria(), null)).getIdCategoria();
					categorias.put(clave, idCategoria);
					nuevas++;
				}
				Long idProducto = productoService.crear(fila.producto(idCategoria)).idProducto();
				for (Path imagen : fila.imagenes()) {
					productoService.agregarImagen(idProducto, leerArchivo(imagen));
				}
				if (fila.modelo() != null) {
					productoService.subirModelo(idProducto, leerArchivo(fila.modelo()));
				}
				if (fila.stock().signum() > 0) {
					inventarioService.registrarEntrada(idProducto, admin.getIdUsuario(),
							new EntradaRequest(fila.stock(), NOTA_STOCK));
				}
			} catch (RuntimeException e) {
				throw new IllegalStateException("Fila " + fila.numero() + ": " + e.getMessage(), e);
			}
		}
		return nuevas;
	}

	private List<Fila> leer(Path carpeta, Set<String> ocultas, List<String> problemas) {
		List<String> lineas;
		try {
			lineas = Files.readAllLines(carpeta.resolve(CatalogoDemoProperties.CATALOGO), StandardCharsets.UTF_8);
		} catch (IOException e) {
			problemas.add(CatalogoDemoProperties.CATALOGO + " no se pudo leer como texto UTF-8 (" + e + ")");
			return List.of();
		}
		// Un BOM al principio lo agregan algunos editores de Windows
		if (lineas.isEmpty() || !lineas.getFirst().replace("﻿", "").strip().equals(ENCABEZADO)) {
			problemas.add("Fila 1: el encabezado tiene que ser " + ENCABEZADO);
			return List.of();
		}
		List<Fila> filas = new ArrayList<>();
		for (int i = 1; i < lineas.size(); i++) {
			if (!lineas.get(i).isBlank()) {
				leerFila(carpeta, ocultas, i + 1, lineas.get(i), problemas).ifPresent(filas::add);
			}
		}
		return filas;
	}

	private Optional<Fila> leerFila(Path carpeta, Set<String> ocultas, int numero, String linea,
			List<String> problemas) {
		String[] campos = linea.split(",", -1);
		if (campos.length != COLUMNAS) {
			problemas.add("Fila " + numero + ": tiene " + campos.length + " columnas y se esperan " + COLUMNAS
					+ " (ningún campo puede llevar comas)");
			return Optional.empty();
		}
		List<String> errores = new ArrayList<>();
		String categoria = campos[0].strip();
		errores(validator.validate(new CategoriaRequest(categoria, null)), Map.of("nombre", "categoria"), errores);
		if (ocultas.contains(Categoria.clave(categoria))) {
			errores.add("categoria: «" + categoria + "» es una categoría oculta; muéstrela antes de cargar");
		}
		BigDecimal precio = numero(campos[3], "precio_bs", errores);
		BigDecimal stock = numero(campos[4], "stock_inicial", errores);
		boolean destacado = destacado(campos[7].strip(), errores);
		UnidadVenta unidad = unidad(campos[8].strip(), errores);
		Fila fila = new Fila(numero, categoria, campos[1].strip(), campos[2].strip(), precio, stock,
				imagenes(carpeta, campos[5].strip(), errores), modelo(carpeta, campos[6].strip(), errores), destacado,
				unidad);
		ProductoRequest producto = fila.producto(CATEGORIA_POR_RESOLVER, precio != null ? precio : PRECIO_POR_RESOLVER);
		errores(validator.validate(producto), Map.of("precio", "precio_bs"), errores);
		if (stock != null && stock.signum() > 0) {
			errores(validator.validate(new EntradaRequest(stock, NOTA_STOCK)), Map.of("cantidad", "stock_inicial"),
					errores);
			if (!InventarioService.admiteDecimales(unidad, stock)) {
				errores.add("stock_inicial: " + InventarioService.mensajeDecimales(unidad));
			}
		}
		errores.forEach(error -> problemas.add("Fila " + numero + ": " + error));
		return errores.isEmpty() ? Optional.of(fila) : Optional.empty();
	}

	private static BigDecimal numero(String campo, String columna, List<String> errores) {
		String texto = campo.strip();
		if (!NUMERO.matcher(texto).matches()) {
			errores.add(columna + ": «" + texto + "» no es un número (los decimales van con punto)");
			return null;
		}
		return new BigDecimal(texto);
	}

	// Vacío es no destacado: lo normal es que la mayoría no lo sea
	private static boolean destacado(String campo, List<String> errores) {
		if (campo.isEmpty() || campo.equalsIgnoreCase("no")) {
			return false;
		}
		if (!campo.equalsIgnoreCase("si") && !campo.equalsIgnoreCase("sí")) {
			errores.add("destacado: «" + campo + "» tiene que ser si o quedar vacío");
		}
		return true;
	}

	// Vacía es UNIDAD, lo más común en una ferretería; METRO, KILO y LITRO admiten stock con decimales
	private static UnidadVenta unidad(String campo, List<String> errores) {
		if (campo.isEmpty()) {
			return UnidadVenta.UNIDAD;
		}
		for (UnidadVenta unidad : UnidadVenta.values()) {
			if (unidad.name().equalsIgnoreCase(campo)) {
				return unidad;
			}
		}
		errores.add("unidad: «" + campo + "» no es una unidad de venta (" + Arrays.stream(UnidadVenta.values())
				.map(u -> u.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", ")) + " o vacía)");
		return UnidadVenta.UNIDAD;
	}

	private List<Path> imagenes(Path carpeta, String campo, List<String> errores) {
		if (campo.isEmpty()) {
			return List.of();
		}
		String[] nombres = campo.split("\\|", -1);
		if (nombres.length > ProductoService.MAXIMO_IMAGENES) {
			errores.add("imagenes: admite hasta " + ProductoService.MAXIMO_IMAGENES);
			return List.of();
		}
		List<Path> imagenes = new ArrayList<>();
		Set<String> vistas = new HashSet<>();
		for (String sinRecortar : nombres) {
			String nombre = sinRecortar.strip();
			if (!vistas.add(nombre)) {
				errores.add("imagenes: " + nombre + " está repetida");
				continue;
			}
			Path archivo = archivoDeLaCarpeta(carpeta, "imagenes", nombre, errores);
			try {
				if (archivo == null) {
					continue;
				} else if (Files.size(archivo) > FormatoImagen.MAXIMO) {
					errores.add("imagenes: " + nombre + " pasa de "
							+ DataSize.ofBytes(FormatoImagen.MAXIMO).toMegabytes() + " MB");
				} else if (FormatoImagen.detectar(firma(archivo)).isEmpty()) {
					errores.add("imagenes: " + nombre + " no es JPG, PNG ni WebP");
				} else {
					// HU-22 RF-12: con las reglas de una foto subida, antes de cargar. Se achica dos veces (acá y al
					// subirla), pero solo al arrancar con una demostración nueva
					String problema = problemaAlAchicar(Files.readAllBytes(archivo));
					if (problema == null) {
						imagenes.add(archivo);
					} else {
						errores.add("imagenes: " + nombre + " " + problema);
					}
				}
			} catch (IOException e) {
				errores.add("imagenes: " + nombre + " no se pudo leer");
			}
		}
		return imagenes;
	}

	// Con las reglas de un modelo subido desde la gestión (HU-15 RF-13); vacío es sin modelo
	private static Path modelo(Path carpeta, String nombre, List<String> errores) {
		if (nombre.isEmpty()) {
			return null;
		}
		Path archivo = archivoDeLaCarpeta(carpeta, "modelo_3d", nombre, errores);
		try {
			if (archivo == null) {
				return null;
			} else if (Files.size(archivo) > FormatoModelo.MAXIMO) {
				errores.add("modelo_3d: " + nombre + " pasa de "
						+ DataSize.ofBytes(FormatoModelo.MAXIMO).toMegabytes() + " MB");
			} else if (!FormatoModelo.esGlb(Files.readAllBytes(archivo))) {
				errores.add("modelo_3d: " + nombre + " no es un modelo .glb");
			} else {
				return archivo;
			}
		} catch (IOException e) {
			errores.add("modelo_3d: " + nombre + " no se pudo leer");
		}
		return null;
	}

	// Solo nombres de archivo de la misma carpeta: ninguna ruta sale de ella, tampoco por un enlace simbólico. Sin el
	// archivo, anota el error y devuelve null
	private static Path archivoDeLaCarpeta(Path carpeta, String columna, String nombre, List<String> errores) {
		Path archivo = carpeta.resolve(nombre).normalize();
		try {
			if (nombre.isEmpty() || !carpeta.equals(archivo.getParent())) {
				errores.add(columna + ": " + nombre + " no es un archivo de la carpeta");
			} else if (!Files.isRegularFile(archivo)) {
				errores.add(columna + ": " + nombre + " no existe en la carpeta");
			} else if (!carpeta.equals(archivo.toRealPath().getParent())) {
				errores.add(columna + ": " + nombre + " no es un archivo de la carpeta");
			} else {
				return archivo;
			}
		} catch (IOException e) {
			errores.add(columna + ": " + nombre + " no se pudo leer");
		}
		return null;
	}

	private String problemaAlAchicar(byte[] contenido) {
		try {
			achicador.achicar(contenido);
			return null;
		} catch (ImagenConDemasiadosPixelesException enorme) {
			return "pasa de " + Achicador.MAXIMO_LADO + " px de lado o "
					+ Achicador.MAXIMO_PIXELES / 1_000_000 + " megapíxeles";
		} catch (ImagenInvalidaException danada) {
			return "no se puede abrir";
		}
	}

	private static byte[] firma(Path archivo) throws IOException {
		try (InputStream entrada = Files.newInputStream(archivo)) {
			return entrada.readNBytes(BYTES_DE_FIRMA);
		}
	}

	private static byte[] leerArchivo(Path archivo) {
		try {
			return Files.readAllBytes(archivo);
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo leer " + archivo, e);
		}
	}

	private static <T> void errores(Set<ConstraintViolation<T>> violaciones, Map<String, String> columnas,
			List<String> errores) {
		violaciones.stream().map(v -> {
			String campo = v.getPropertyPath().toString();
			return columnas.getOrDefault(campo, campo) + ": " + v.getMessage();
		}).sorted().forEach(errores::add);
	}

	// Una fila válida del CSV. Las imágenes y el modelo se vuelven a leer al cargar, para no tener todos en memoria
	private record Fila(int numero, String categoria, String nombre, String descripcion, BigDecimal precio,
			BigDecimal stock, List<Path> imagenes, Path modelo, boolean destacado, UnidadVenta unidad) {

		ProductoRequest producto(Long idCategoria) {
			return producto(idCategoria, precio);
		}

		ProductoRequest producto(Long idCategoria, BigDecimal precio) {
			return new ProductoRequest(nombre, null, descripcion, idCategoria, unidad, precio, null, null, destacado);
		}
	}
}
