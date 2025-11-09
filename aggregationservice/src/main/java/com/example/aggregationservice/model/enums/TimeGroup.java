package com.example.aggregationservice.model.enums;

import lombok.Getter;

@Getter
public enum TimeGroup {
    GROUP_00_06("00-06", 0, 6),
    GROUP_06_12("06-12", 6, 12),
    GROUP_12_18("12-18", 12, 18),
    GROUP_18_00("18-00", 18, 24);

    private final String code;
    private final int startHour;
    private final int endHour;

    TimeGroup(String code, int startHour, int endHour) {
        this.code = code;
        this.startHour = startHour;
        this.endHour = endHour;
    }
}