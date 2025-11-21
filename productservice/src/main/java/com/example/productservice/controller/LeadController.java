package com.example.productservice.controller;

import com.example.productservice.dto.*;
import com.example.productservice.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/leads")
@RequiredArgsConstructor
@Tag(name = "Lead Management", description = "API для управления лидами (заявками на банковские продукты)")
public class LeadController {

    private final LeadService leadService;

    @Operation(
            summary = "Создать новую заявку",
            description = "Создает новую заявку на банковский продукт. Пользователь может оставить только одну заявку на продукт."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Заявка успешно создана",
                    content = @Content(schema = @Schema(implementation = CreateLeadResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Заявка на этот продукт уже существует"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Пользователь не найден"
            )
    })
    @PostMapping("/new")
    public ResponseEntity<CreateLeadResponse> createLead(
            @Parameter(description = "Данные для создания заявки", required = true)
            @RequestBody CreateLeadRequest request) {

        UUID leadId = leadService.createLead(request);
        return ResponseEntity.ok(new CreateLeadResponse(leadId));
    }

    @Operation(
            summary = "Получить заявки пользователя",
            description = "Возвращает список всех заявок пользователя, отсортированных по дате создания (новые сначала)"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Список заявок успешно получен",
                    content = @Content(schema = @Schema(implementation = LeadListResponse.class))
            )
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<LeadListResponse> getUserLeads(
            @Parameter(description = "UUID пользователя", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID userId) {

        return ResponseEntity.ok(new LeadListResponse(leadService.getUserLeads(userId)));
    }

    @Operation(
            summary = "Получить заявки для банка",
            description = "Возвращает список новых заявок для банка. После получения заявки помечаются как доставленные."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Список заявок успешно получен",
                    content = @Content(schema = @Schema(implementation = LeadListResponse.class))
            )
    })
    @GetMapping("/bank/{bankId}")
    public ResponseEntity<LeadListResponse> getBankLeads(
            @Parameter(description = "UUID банка", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID bankId,

            @Parameter(
                    description = "Статус заявок для фильтрации",
                    required = false,
                    example = "NEW",
                    schema = @Schema(allowableValues = {"NEW", "RECEIVED", "APPROVED", "REJECTED", "EXPIRED"})
            )
            @RequestParam(defaultValue = "NEW") String status,

            @Parameter(description = "Лимит количества заявок (по умолчанию 50)", required = false)
            @RequestParam(required = false) Integer limit) {

        return ResponseEntity.ok(new LeadListResponse(
                leadService.pullBankLeads(bankId, status, limit)
        ));
    }

    @Operation(
            summary = "Обновить статус заявки",
            description = "Обновляет статус заявки. Используется банками для изменения статуса заявки."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статус заявки успешно обновлен"),
            @ApiResponse(responseCode = "404", description = "Заявка не найдена"),
            @ApiResponse(responseCode = "400", description = "Недопустимый статус")
    })
    @PatchMapping("/{leadId}")
    public ResponseEntity<Void> updateLeadStatus(
            @Parameter(description = "UUID заявки", required = true, example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID leadId,

            @Parameter(description = "Новый статус заявки", required = true)
            @RequestBody UpdateLeadStatusRequest request) {

        leadService.updateLeadStatus(leadId, request);
        return ResponseEntity.ok().build();
    }

    @Schema(description = "Ответ при создании заявки")
    public static class CreateLeadResponse {
        @Schema(description = "UUID созданной заявки", example = "123e4567-e89b-12d3-a456-426614174000")
        private UUID leadId;

        public CreateLeadResponse(UUID leadId) {
            this.leadId = leadId;
        }

        public UUID getLeadId() {
            return leadId;
        }

        public void setLeadId(UUID leadId) {
            this.leadId = leadId;
        }
    }
}