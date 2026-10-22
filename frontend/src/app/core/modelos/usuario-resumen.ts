import { EstadoCuenta, Rol, TipoCliente } from './usuario';

// Espejo de UsuarioResumenResponse: lo que la gestión de usuarios muestra de cada cuenta, sin
// teléfono ni fecha de nacimiento (RNF-03)
export interface UsuarioResumen {
	idUsuario: number;
	nombre: string;
	apellido: string;
	correo: string;
	rol: Rol;
	estado: EstadoCuenta;
	// HU-18 RF-21
	tipoCliente: TipoCliente;
	fechaRegistro: string;
}

// Los filtros de la lista. null en rol, estado o tipo es "todos"; la página
// empieza en 0, como en el backend.
export interface FiltrosDeUsuarios {
	rol: Rol | null;
	estado: EstadoCuenta | null;
	tipo: TipoCliente | null;
	texto: string;
	pagina: number;
}
