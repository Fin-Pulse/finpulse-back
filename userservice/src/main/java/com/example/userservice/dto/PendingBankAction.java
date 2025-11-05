package com.example.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingBankAction {
    private String bankCode;
    private String bankName;
    private String requestId;
    private String actionMessage;
    private String actionType; // "need_app_approval", "need_sms_verification", etc.
    private String deepLink; // Ссылка для быстрого перехода в приложение банка
    private String instructions; // Подробные инструкции для пользователя
}