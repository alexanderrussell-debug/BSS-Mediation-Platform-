package com.meridiantel.bss.ums.decode;

import com.meridiantel.bss.ums.core.UmsConstants;

public class DecoderFactory {

    public Decoder getDecoder(String streamType) {
        if (UmsConstants.STREAM_MSC_BER.equals(streamType)) {
            return new MscBerDecoder();
        } else if (UmsConstants.STREAM_PGW_CSV.equals(streamType)) {
            return new PgwCsvDecoder();
        } else if (UmsConstants.STREAM_SMSC_PIPE.equals(streamType)) {
            return new SmscPipeDecoder();
        }
        throw new IllegalArgumentException("No decoder for stream type " + streamType);
    }
}
