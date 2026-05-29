package cl.duoc.pagos.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "pagos")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long pedidoId; // Vinculamos el pago a un pedido específico

    @Column(nullable = false)
    private Double monto;

    @Column(nullable = false)
    private String metodo; // Ej: TARJETA_CREDITO, TRANSFERENCIA

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPago estado;

    @Column(nullable = false, unique = true)
    private String codigoTransaccion; // Simulamos un código de pasarela tipo Transbank/Stripe

    @Column(nullable = false)
    private LocalDateTime fechaTransaccion;

    @PrePersist
    protected void onCreate() {
        this.fechaTransaccion = LocalDateTime.now();
        // Generamos un código único simulando la respuesta del banco
        this.codigoTransaccion = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        if (this.estado == null) {
            this.estado = EstadoPago.PENDIENTE;
        }
    }
}