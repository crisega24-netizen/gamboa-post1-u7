package com.example.multas.infrastructure.simulador;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulador de las dos pasarelas externas, solo para pruebas locales (perfil
 * "simulador"). Imita el contrato HTTP de cada proveedor y rechaza los pagos
 * superiores a $10.000 para poder demostrar tanto la aprobación como el rechazo.
 */
@Profile("simulador")
@RestController
@RequestMapping("/simulador")
public class PasarelasSimuladasController {

    private static final BigDecimal MONTO_MAXIMO_APROBADO = new BigDecimal("10000");
    private static final long MONTO_MAXIMO_APROBADO_CENTAVOS = 1_000_000L;

    @PostMapping("/pagosudes/transacciones")
    public PagosUdesRespuesta procesarPagosUdes(@RequestBody PagosUdesSolicitud solicitud) {
        boolean aprobada = solicitud.monto().compareTo(MONTO_MAXIMO_APROBADO) <= 0;
        String idTransaccion = "PU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new PagosUdesRespuesta(idTransaccion, aprobada ? "APROBADA" : "RECHAZADA");
    }

    @PostMapping("/wompi/transactions")
    public WompiRespuesta procesarWompi(@RequestBody WompiSolicitud solicitud) {
        boolean aprobada = solicitud.amountInCents() <= MONTO_MAXIMO_APROBADO_CENTAVOS;
        return new WompiRespuesta(solicitud.reference(), aprobada ? "APPROVED" : "DECLINED");
    }

    public record PagosUdesSolicitud(String estudianteId, BigDecimal monto) {
    }

    public record PagosUdesRespuesta(String idTransaccion, String estadoTransaccion) {
    }

    public record WompiSolicitud(String reference, long amountInCents) {
    }

    public record WompiRespuesta(String reference, String status) {
    }
}
