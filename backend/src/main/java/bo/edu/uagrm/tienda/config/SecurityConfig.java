package bo.edu.uagrm.tienda.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import bo.edu.uagrm.tienda.service.AutenticacionService;
import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, TokenJwt tokenJwt,
			AutenticacionService autenticacionService,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver manejadorExcepciones) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(peticiones -> peticiones
						// El despacho interno a /error tras una excepción o un sendError no pasa por el filtro del
						// token: sin esto, cualquier error de una petición autenticada terminaría como 401. Pedir
						// /error directamente es un despacho REQUEST y sigue exigiendo sesión
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/registro", "/api/auth/inicio-sesion",
								"/api/auth/recuperacion", "/api/auth/recuperacion/restablecimiento",
								"/api/auth/verificacion").permitAll()
						// El rol se toma de la base en cada petición (JwtFiltroAutenticacion): un cambio de rol
						// rige desde la siguiente
						.requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR")
						// Lo que lee el catálogo es público; la gestión de la tienda es del Administrador (HU-06)
						.requestMatchers(HttpMethod.GET, "/api/categorias/**", "/api/productos/**").permitAll()
						.requestMatchers("/api/gestion/**").hasRole("ADMINISTRADOR")
						// El inventario lo leen el Empleado, el Administrador y el Contador; solo los dos primeros
						// registran movimientos (HU-08)
						.requestMatchers(HttpMethod.GET, "/api/inventario/**")
						.hasAnyRole("EMPLEADO", "ADMINISTRADOR", "CONTADOR")
						.requestMatchers("/api/inventario/**").hasAnyRole("EMPLEADO", "ADMINISTRADOR")
						// Los pedidos de todos los clientes, para el personal: el Contador solo los ve (HU-17 RF-9)
						.requestMatchers(HttpMethod.GET, "/api/despacho/**")
						.hasAnyRole("EMPLEADO", "ADMINISTRADOR", "CONTADOR")
						.requestMatchers("/api/despacho/**").hasAnyRole("EMPLEADO", "ADMINISTRADOR")
						// Los reportes, para el Contador y el Administrador; el Empleado no (HU-18 RF-1)
						.requestMatchers("/api/reportes/**").hasAnyRole("CONTADOR", "ADMINISTRADOR")
						// El carrito es solo del Cliente: el personal no compra (HU-11 RF-7)
						.requestMatchers("/api/carrito/**").hasRole("CLIENTE")
						// El aviso del sistema de pago llega sin sesión. No confirma nada: solo adelanta la consulta
						// (HU-13 RF-10)
						.requestMatchers(HttpMethod.GET, "/api/pagos/aviso").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/pagos/aviso").permitAll()
						// Los pedidos de la API del Cliente: el personal los ve en /api/despacho (HU-12 RF-13, HU-17)
						.requestMatchers("/api/pedidos/**").hasRole("CLIENTE")
						.anyRequest().authenticated())
				// El 401 y el 403 pasan por ManejadorErrores para salir con el mismo formato que el resto de la
				// API; el 401 anuncia además el esquema de autenticación esperado (RFC 6750)
				.exceptionHandling(errores -> errores
						.authenticationEntryPoint((peticion, respuesta, excepcion) -> {
							respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
							manejadorExcepciones.resolveException(peticion, respuesta, null, excepcion);
						})
						.accessDeniedHandler((peticion, respuesta, excepcion) -> manejadorExcepciones
								.resolveException(peticion, respuesta, null, excepcion)))
				.addFilterBefore(new JwtFiltroAutenticacion(tokenJwt, autenticacionService),
						UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(
			@Value("${tienda.cors.origenes-permitidos}") List<String> origenesPermitidos) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(origenesPermitidos);
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
		fuente.registerCorsConfiguration("/api/**", cors);
		return fuente;
	}

	// Costo 12 (decisiones.md → Contraseñas con BCrypt): cada intento de adivinar una contraseña robada cuesta 4 veces
	// más que con 10. Las guardadas con otro costo siguen sirviendo, porque el costo va dentro del hash. En las pruebas
	// es 4, para no hacerlas lentas
	@Bean
	PasswordEncoder passwordEncoder(@Value("${tienda.contrasenas.costo-bcrypt:12}") int costo) {
		return new BCryptPasswordEncoder(costo);
	}
}
