package com.example.multas.domain;

/**
 * Resultado de un pago expresado en términos del dominio y neutral respecto a
 * las pasarelas: cada adaptador traduce su propio formato a este contrato común.
 */
public record ResultadoPago(
        String proveedor,
        boolean exitoso,
        String referenciaExterna,
        String mensaje
) {
}
