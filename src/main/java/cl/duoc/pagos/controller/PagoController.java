package cl.duoc.pagos.controller;

import cl.duoc.pagos.dto.PagoRequestDTO;
import cl.duoc.pagos.dto.PagoResponseDTO;
import cl.duoc.pagos.model.EstadoPago;
import cl.duoc.pagos.model.Pago;
import cl.duoc.pagos.repository.PagoRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private WebClient.Builder webClientBuilder;

    // Procesar un nuevo pago (Llamado por el microservicio de Pedidos)
    @PostMapping("/procesar")
    public ResponseEntity<PagoResponseDTO> procesarPago(@Valid @RequestBody PagoRequestDTO dto) {
        
        // Verificamos si ya existe un pago para este pedido
        if (pagoRepository.findByPedidoId(dto.getPedidoId()).isPresent()) {
            return ResponseEntity.badRequest().build(); // Ya se pagó este pedido
        }

        Pago pago = new Pago();
        pago.setPedidoId(dto.getPedidoId());
        pago.setMonto(dto.getMonto());
        pago.setMetodo(dto.getMetodo());
        
        // Simulamos que la pasarela siempre lo aprueba
        pago.setEstado(EstadoPago.APROBADO); 
        
        Pago guardado = pagoRepository.save(pago);

        // 🧾 [CONEXIÓN AUTOMÁTICA] PASO 6: AVISAR AL MS VENTAS (Puerto 8085)
        try {
            // Armamos el mapa con los datos exactos que pide tu VentaRequestDTO
            Map<String, Object> ventaRequest = new HashMap<>();
            ventaRequest.put("pedidoId", guardado.getPedidoId());
            ventaRequest.put("vendedorId", 1); // Enviamos un ID por defecto para la validación
            ventaRequest.put("montoTotal", guardado.getMonto()); // Cambiado a montoTotal para hacer match

            // Enviamos la petición POST a la ruta exacta /api/ventas/registrar
            webClientBuilder.build().post()
                    .uri("http://localhost:8085/api/ventas/registrar") 
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

    // Consultar el estado de un pago por el ID del Pedido
    @GetMapping("/pedido/{pedidoId}")
    public ResponseEntity<PagoResponseDTO> obtenerPagoPorPedido(@PathVariable Long pedidoId) {
        Pago pago = pagoRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado para el pedido: " + pedidoId));
        return ResponseEntity.ok(convertirADto(pago));
    }

    // Procesar reembolso (ESTE ES EL QUE LLAMARÁ EL MICROSERVICIO DE RECLAMOS)
    @PutMapping("/reembolsar/pedido/{pedidoId}")
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

    // Método auxiliar
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