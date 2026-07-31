package com.actilazion.ariesreportingproject.service.job;

public interface EmailRateLimiter {
    boolean tryAcquire();
}
