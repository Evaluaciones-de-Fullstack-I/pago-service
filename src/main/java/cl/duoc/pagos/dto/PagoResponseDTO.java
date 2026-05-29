package cl.duoc.pagos.dto;

import cl.duoc.pagos.model.EstadoPago;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PagoResponseDTO {
    private Long id;
    private Long pedidoId;
    private Double monto;
    private String metodo;
    private EstadoPago estado;
    private String codigoTransaccion;
    private LocalDateTime fechaTransaccion;
}