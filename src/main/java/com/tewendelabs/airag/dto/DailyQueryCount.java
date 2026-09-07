package com.tewendelabs.airag.dto;

import java.time.LocalDate;

public record DailyQueryCount(LocalDate day, long count) {
}
