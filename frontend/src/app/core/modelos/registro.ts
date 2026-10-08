// Espejo de RegistroClienteRequest del backend
// (backend/src/main/java/bo/edu/uagrm/tienda/dto/RegistroClienteRequest.java).
// El teléfono es el único dato opcional.
export interface DatosDeRegistro {
	nombre: string;
	apellido: string;
	correo: string;
	contrasena: string;
	telefono: string;
}
