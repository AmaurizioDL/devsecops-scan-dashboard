package com.devsecops.dashboard;

import java.time.Instant;

public record ProgressSample(Instant timestamp, int percent) {
}
