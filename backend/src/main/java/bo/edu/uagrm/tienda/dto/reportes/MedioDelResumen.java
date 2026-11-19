package bo.edu.uagrm.tienda.dto.reportes;

// Las filas de «Por medio de pago» del resumen (HU-23 RF-35): en línea va aparte, porque la tienda no sabe con qué se
// pagó en la página del proveedor; QR y efectivo son del mostrador
public enum MedioDelResumen {
	EN_LINEA, QR, EFECTIVO
}
