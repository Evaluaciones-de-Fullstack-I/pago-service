package cl.duoc.pagos.controller;

import cl.duoc.pagos.dto.PagoRequestDTO;
import cl.duoc.pagos.dto.PagoResponseDTO;
import cl.duoc.pagos.model.EstadoPago;
import cl.duoc.pagos.model.Pago;
import cl.duoc.pagos.repository.PagoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value; // 👈 Agregado para leer la URL fija
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
@Tag(name = "Pagos", description = "Controlador para el procesamiento de transacciones financieras, reembolsos y sincronización con el MS Ventas")
public class PagoController {

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private WebClient.Builder webClientBuilder;

    // 🛰️ URL del microservicio de Ventas (Configurable desde properties o Railway)
    @Value("${url.ventas:http://localhost:8085}")
    private String urlVentas;

    @PostMapping("/procesar")
    @Operation(
        summary = "Procesar un nuevo pago",
        description = "Registra una transacción financiera asociada a un pedido. Si es aprobada, notifica automáticamente al MS Ventas vía WebClient.",
        responses = {
            @ApiResponse(
                responseCode = "201", 
                description = "Pago procesado y aprobado con éxito"
            ),
            @ApiResponse(
                responseCode = "400", 
                description = "El pedido indicado ya cuenta con un pago registrado o los datos de entrada son incorrectos"
            )
        }
    )
    public ResponseEntity<PagoResponseDTO> procesarPago(
            @Valid @RequestBody(
                description = "Estructura JSON con la información requerida para procesar el pago",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = PagoRequestDTO.class),
                    examples = @ExampleObject(
                        name = "Ejemplo de Pago",
                        value = "{\n  \"pedidoId\": 10024,\n  \"monto\": 49990,\n  \"metodo\": \"TARJETA_CREDITO\"\n}"
                    )
                )
            )
            @org.springframework.web.bind.annotation.RequestBody PagoRequestDTO dto) {
        
        if (pagoRepository.findByPedidoId(dto.getPedidoId()).isPresent()) {
            return ResponseEntity.badRequest().build();
        }

        Pago pago = new Pago();
        pago.setPedidoId(dto.getPedidoId());
        pago.setMonto(dto.getMonto());
        pago.setMetodo(dto.getMetodo());
        
        pago.setEstado(EstadoPago.APROBADO); 
        
        Pago guardado = pagoRepository.save(pago);

        try {
            Map<String, Object> ventaRequest = new HashMap<>();
            ventaRequest.put("pedidoId", guardado.getPedidoId());
            ventaRequest.put("vendedorId", 1); 
            ventaRequest.put("montoTotal", guardado.getMonto());

            // 🛰️ Llamada directa usando la variable urlVentas
            webClientBuilder.build().post()
                    .uri(urlVentas + "/api/ventas/registrar")
                    .bodyValue(ventaRequest)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block(); 

            System.out.println("🧾 [CONEXIÓN] Pago ID " + guardado.getId() + " notificado exitosamente a MS Ventas.");
            
        } catch (Exception e) {
            System.out.println("❌ [ERROR] No se pudo notificar al MS Ventas: " + e.getMessage());
        }

        return new ResponseEntity<>(convertirADto(guardado), HttpStatus.CREATED);
    }

    @GetMapping("/pedido/{pedidoId}")
    @Operation(
        summary = "Consultar pago por ID de Pedido",
        description = "Busca y recupera los detalles del flujo de pago en base al identificador único del pedido.",
        responses = {
            @ApiResponse(
                responseCode = "200", 
                description = "Pago localizado correctamente"
            ),
            @ApiResponse(
                responseCode = "404", 
                description = "No se encontró ningún registro de pago para el ID de pedido suministrado"
            )
        }
    )
    public ResponseEntity<PagoResponseDTO> obtenerPagoPorPedido(@PathVariable Long pedidoId) {
        Pago pago = pagoRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado para el pedido: " + pedidoId));
        return ResponseEntity.ok(convertirADto(pago));
    }

    @PutMapping("/reembolsar/pedido/{pedidoId}")
    @Operation(
        summary = "Procesar el reembolso de un pago",
        description = "Cambia el estado de una transacción existente a REEMBOLSADO (endpoint diseñado para ser invocado por el MS de Reclamos).",
        responses = {
            @ApiResponse(
                responseCode = "200", 
                description = "Reembolso efectuado y guardado correctamente"
            ),
            @ApiResponse(
                responseCode = "404", 
                description = "No se ubicó un pago para el pedido entregado"
            )
        }
    )
    public ResponseEntity<PagoResponseDTO> reembolsarPago(@PathVariable Long pedidoId) {
        Pago pago = pagoRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado para el pedido: " + pedidoId));

        if (pago.getEstado() != EstadoPago.APROBADO) {
            throw new RuntimeException("Solo se pueden reembolsar pagos aprobados");
        }

        pago.setEstado(EstadoPago.REEMBOLSADO);
        Pago actualizado = pagoRepository.save(pago);
        
        return ResponseEntity.ok(convertirADto(actualizado));
    }

    private PagoResponseDTO convertirADto(Pago pago) {
        PagoResponseDTO dto = new PagoResponseDTO();
        dto.setId(pago.getId());
        dto.setPedidoId(pago.getPedidoId());
        dto.setMonto(pago.getMonto());
        dto.setMetodo(pago.getMetodo());
        dto.setEstado(pago.getEstado());
        dto.setCodigoTransaccion(pago.getCodigoTransaccion());
        dto.setFechaTransaccion(pago.getFechaTransaccion());
        return dto;
    }
}