package bo.edu.uagrm.tienda.entity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recuperacion_contrasena")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecuperacionContrasena {

	private static final Duration VIGENCIA = Duration.ofHours(1);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_recuperacion")
	private Long idRecuperacion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_usuario", nullable = false)
	private Usuario usuario;

	// La columna se llama token, pero guarda su SHA-256 (decisiones.md → Recuperación de contraseña)
	@Column(name = "token", nullable = false, unique = true, length = 255)
	private String hashToken;

	@Column(name = "fecha_creacion", nullable = false)
	private LocalDateTime fechaCreacion;

	@Column(name = "fecha_expiracion", nullable = false)
	private LocalDateTime fechaExpiracion;

	@Column(nullable = false)
	private boolean usado;

	public static RecuperacionContrasena crear(Usuario usuario, String hashToken, LocalDateTime fechaCreacion) {
		RecuperacionContrasena recuperacion = new RecuperacionContrasena();
		recuperacion.usuario = Objects.requireNonNull(usuario);
		recuperacion.hashToken = Objects.requireNonNull(hashToken);
		recuperacion.fechaCreacion = Objects.requireNonNull(fechaCreacion);
		recuperacion.fechaExpiracion = fechaCreacion.plus(VIGENCIA);
		recuperacion.usado = false;
		return recuperacion;
	}

	public boolean estaVigente(LocalDateTime ahora) {
		return !usado && ahora.isBefore(fechaExpiracion);
	}

	public void marcarUsada() {
		usado = true;
	}
}
