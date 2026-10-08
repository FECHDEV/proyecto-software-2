// Espejo de UsuarioResponse del backend
// (backend/src/main/java/bo/edu/uagrm/tienda/dto/UsuarioResponse.java)

export type Rol = 'CLIENTE' | 'ADMINISTRADOR' | 'EMPLEADO' | 'CONTADOR';

export type EstadoCuenta = 'ACTIVA' | 'DESACTIVADA';

export interface Usuario {
	idUsuario: number;
	nombre: string;
	apellido: string;
	correo: string;
	telefono: string | null;
	rol: Rol;
	estado: EstadoCuenta;
	fechaRegistro: string;
}

export const ROLES: readonly Rol[] = ['CLIENTE', 'ADMINISTRADOR', 'EMPLEADO', 'CONTADOR'];

export const ESTADOS: readonly EstadoCuenta[] = ['ACTIVA', 'DESACTIVADA'];

// Cómo se nombran en pantalla; en el código siguen siendo los del backend
export const NOMBRES_DE_ROL: Record<Rol, string> = {
	CLIENTE: 'Cliente',
	ADMINISTRADOR: 'Administrador',
	EMPLEADO: 'Empleado',
	CONTADOR: 'Contador',
};

export const NOMBRES_DE_ESTADO: Record<EstadoCuenta, string> = {
	ACTIVA: 'Activa',
	DESACTIVADA: 'Desactivada',
};

export function nombreCompleto(usuario: Usuario): string {
	return `${usuario.nombre} ${usuario.apellido}`;
}
