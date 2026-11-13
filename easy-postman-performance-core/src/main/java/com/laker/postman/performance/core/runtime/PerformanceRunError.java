package com.laker.postman.performance.core.runtime;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PerformanceRunError {
    String message;
    Throwable cause;
    /**
     * True when the run keeps going after this error (for example a single virtual-user iteration failed).
     */
    boolean recoverable;
}
