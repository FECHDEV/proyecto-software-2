package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.config.AdministradorInicialProperties;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Crea el Administrador inicial en el despliegue (CLAUDE.md, decisiones.md): sin administradores,
// ningún CU permite recuperarse, así que si no puede crearlo la aplicación no arranca
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(AdministradorInicialProperties.class)
public class AdministradorInicial implements ApplicationRunner {

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final Validator validator;
	private final Clock clock;
	private final AdministradorInicialProperties propiedades;

	@Override
	@Transactional
	public void run(ApplicationArguments argumentos) {
		if (usuarioRepository.existsByRol(Rol.ADMINISTRADOR)) {
			return;
		}
		Set<ConstraintViolation<AdministradorInicialProperties>> errores = validator.validate(propiedades);
		if (!errores.isEmpty()) {
			throw new IllegalStateException("No existe ningún administrador y no se puede crear el inicial: "
					+ errores.stream().map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining("; ")));
		}
		if (usuarioRepository.existsByCorreo(propiedades.correo())) {
			throw new IllegalStateException("No existe ningún administrador y TIENDA_ADMIN_CORREO ya pertenece a "
					+ "otra cuenta, a la que no se le cambia el rol. Configure otro correo.");
		}
		usuarioRepository.save(Usuario.crearAdministrador("Administrador", "Inicial", propiedades.correo(),
				passwordEncoder.encode(propiedades.contrasena()), LocalDateTime.now(clock)));
		log.info("Administrador inicial creado.");
	}
}
