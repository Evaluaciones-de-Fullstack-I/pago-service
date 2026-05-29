package cl.duoc.pagos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PagoRequestDTO {
    
    @NotNull(message = "El ID del pedido es obligatorio")
    private Long pedidoId;

    @NotNull(message = "El monto es obligatorio")
    @Min(value = 1, message = "El monto debe ser mayor a 0")
    private Double monto;

    @NotBlank(message = "El método de pago es obligatorio")
    private String metodo;
}