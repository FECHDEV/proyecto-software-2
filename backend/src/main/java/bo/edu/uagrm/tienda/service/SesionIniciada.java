package bo.edu.uagrm.tienda.service;

import bo.edu.uagrm.tienda.config.TokenEmitido;
import bo.edu.uagrm.tienda.entity.Usuario;

public record SesionIniciada(Usuario usuario, TokenEmitido token) {
}
