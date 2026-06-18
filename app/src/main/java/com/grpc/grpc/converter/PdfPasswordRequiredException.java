package com.grpc.grpc.converter;

/**
 * PDF requires a password for iText to open it (owner/user encryption).
 */
public final class PdfPasswordRequiredException extends Exception {

    public PdfPasswordRequiredException() {
        super("PDF is password protected.");
    }

    public PdfPasswordRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
