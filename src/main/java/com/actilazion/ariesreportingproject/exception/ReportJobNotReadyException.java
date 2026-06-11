package com.actilazion.ariesreportingproject.exception;

import org.springframework.http.HttpStatus;

public class ReportJobNotReadyException extends AppException {
    public ReportJobNotReadyException(String jobId) {
        super("Report job " + jobId + " is not ready yet.", HttpStatus.ACCEPTED);
    }
}
