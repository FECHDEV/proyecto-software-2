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
								"/api/auth/recuperacion", "/api/auth/recuperacion/restablecimiento").permitAll()
						// El rol se toma de la base en cada petición (JwtFiltroAutenticacion): un cambio de rol
						// rige desde la siguiente
						.requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR")
						// Lo que lee el catálogo es público; la gestión de la tienda es del Administrador (HU-06)
						.requestMatchers(HttpMethod.GET, "/api/categorias/**").permitAll()
						.requestMatchers("/api/gestion/**").hasRole("ADMINISTRADOR")
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

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
