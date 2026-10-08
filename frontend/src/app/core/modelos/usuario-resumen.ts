import { EstadoCuenta, Rol } from './usuario';

// Espejo de UsuarioResumenResponse: lo que la gestión de usuarios muestra de cada cuenta, sin
// teléfono ni fecha de nacimiento (RNF-03)
export interface UsuarioResumen {
	idUsuario: number;
	nombre: string;
	apellido: string;
	correo: string;
	rol: Rol;
	estado: EstadoCuenta;
	fechaRegistro: string;
}

// Los filtros de la lista. null en rol o estado es "todos"; la página empieza
// en 0, como en el backend.
export interface FiltrosDeUsuarios {
	rol: Rol | null;
	estado: EstadoCuenta | null;
	texto: string;
	pagina: number;
}
