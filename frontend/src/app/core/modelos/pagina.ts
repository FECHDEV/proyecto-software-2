// Espejo de PaginaResponse del backend: el formato propio de la API para las
// listas por páginas, en vez del Page de Spring Data
export interface Pagina<T> {
	contenido: T[];
	pagina: number;
	tamano: number;
	totalElementos: number;
	totalPaginas: number;
}
