package com.actilazion.ariesreportingproject.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found: " + id, HttpStatus.NOT_FOUND);
    }
}
