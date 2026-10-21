package bo.edu.uagrm.tienda.entity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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

// El enlace del correo «Confirme su correo» (HU-20), con el esquema de RecuperacionContrasena. En su propia tabla:
// un enlace de verificación nunca sirve para recuperar la contraseña
@Entity
@Table(name = "verificacion_correo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificacionCorreo {

	private static final Duration VIGENCIA = Duration.ofHours(24);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_verificacion")
	private Long idVerificacion;

	// Las cuentas no se borran (se desactivan); borrar una, como hacen las pruebas, se lleva sus enlaces
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_usuario", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Usuario usuario;

	// La columna se llama token, pero guarda su SHA-256, como la recuperación
	@Column(name = "token", nullable = false, unique = true, length = 255)
	private String hashToken;

	@Column(name = "fecha_creacion", nullable = false)
	private LocalDateTime fechaCreacion;

	@Column(name = "fecha_expiracion", nullable = false)
	private LocalDateTime fechaExpiracion;

	@Column(nullable = false)
	private boolean usado;

	public static VerificacionCorreo crear(Usuario usuario, String hashToken, LocalDateTime fechaCreacion) {
		VerificacionCorreo verificacion = new VerificacionCorreo();
		verificacion.usuario = Objects.requireNonNull(usuario);
		verificacion.hashToken = Objects.requireNonNull(hashToken);
		verificacion.fechaCreacion = Objects.requireNonNull(fechaCreacion);
		verificacion.fechaExpiracion = fechaCreacion.plus(VIGENCIA);
		verificacion.usado = false;
		return verificacion;
	}

	public boolean estaVigente(LocalDateTime ahora) {
		return !usado && ahora.isBefore(fechaExpiracion);
	}

	public void marcarUsada() {
		usado = true;
	}
}
