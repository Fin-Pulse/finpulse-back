package com.example.aggregationservice.model.enums;

public enum VerificationStatus {
    PENDING,           // Только зарегистрировался, еще не проверяли
    VERIFIED,          // Всё ок — найден в банке, счета загружены
    PARTIALLY_VERIFIED, // Часть счетов загружена, есть pending-банки
    PENDING_ACTION,    // Требуется действие пользователя в банках
    NOT_FOUND,         // Такого clientId нет в банке
    ERROR              // Ошибка при обращении к API банка
}
