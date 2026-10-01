package com.example.multas.domain.port;

import com.example.multas.domain.ResultadoPago;
import com.example.multas.model.Multa;

/**
 * Puerto de salida del dominio para el pago en línea. El dominio define qué
 * necesita (procesar el pago de una multa) sin conocer qué pasarela lo atiende.
 */
public interface PasarelaPagoPort {

    ResultadoPago procesar(Multa multa);
}
