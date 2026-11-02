package com.meridiantel.bss.ums.core;

/**
 * Record-level rejection. Record goes to the reject file with the reason code.
 */
public class RejectException extends Exception {

    private String reasonCode;

    public RejectException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
