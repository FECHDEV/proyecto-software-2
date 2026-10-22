package bo.edu.uagrm.tienda.entity;

// A quién le vende la tienda: los reportes agrupan las ventas por esto (HU-18). Toda cuenta nace «Público general» y
// solo el Administrador lo cambia, en una cuenta de Cliente (RF-21)
public enum TipoCliente {
	PUBLICO_GENERAL, CONTRATISTA, TALLER, EMPRESA
}
