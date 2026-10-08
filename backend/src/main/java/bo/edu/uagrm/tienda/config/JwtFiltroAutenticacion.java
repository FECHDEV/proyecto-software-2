package bo.edu.uagrm.tienda.config;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.service.AutenticacionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// No es un @Component: SecurityConfig lo crea, para que el contenedor no lo registre una segunda vez
@RequiredArgsConstructor
public class JwtFiltroAutenticacion extends OncePerRequestFilter {

	private static final String PREFIJO = "Bearer ";

	private final TokenJwt tokenJwt;
	private final AutenticacionService autenticacionService;

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		String encabezado = peticion.getHeader(HttpHeaders.AUTHORIZATION);
		// Un token ausente, inválido, de una cuenta inactiva o de una contraseña ya cambiada no autentica, pero
		// tampoco corta la petición: las rutas públicas la atienden igual y las protegidas responden 401
		if (encabezado != null && encabezado.startsWith(PREFIJO)) {
			tokenJwt.leer(encabezado.substring(PREFIJO.length()))
					.flatMap(leido -> autenticacionService.usuarioActivo(leido.idUsuario())
							.filter(usuario -> tokenJwt.correspondeA(leido, usuario)))
					.ifPresent(JwtFiltroAutenticacion::autenticar);
		}
		cadena.doFilter(peticion, respuesta);
	}

	private static void autenticar(Usuario usuario) {
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(usuario.getIdUsuario(), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()))));
		SecurityContextHolder.setContext(contexto);
	}
}
