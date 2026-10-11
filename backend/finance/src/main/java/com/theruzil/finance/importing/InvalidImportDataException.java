package com.theruzil.finance.importing;

public class InvalidImportDataException extends RuntimeException {

    public InvalidImportDataException(String message) {
        super(message);
    }
}
