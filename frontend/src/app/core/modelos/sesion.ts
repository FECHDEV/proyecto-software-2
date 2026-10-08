import { Usuario } from './usuario';

// Espejo de InicioSesionResponse del backend. El token vence a las 8 horas y no
// hay refresh token (decisiones.md → Autenticación por JWT stateless).
export interface Sesion {
	token: string;
	tipo: string;
	expiracion: string;
	usuario: Usuario;
}
