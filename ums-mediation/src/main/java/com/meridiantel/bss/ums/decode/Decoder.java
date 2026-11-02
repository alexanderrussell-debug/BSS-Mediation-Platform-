package com.meridiantel.bss.ums.decode;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.meridiantel.bss.ums.core.StreamConfig;

/**
 * Decodes one raw network file into UsageRecords.
 * Records that cannot be decoded are still returned, with extra "DECODE_ERROR" set,
 * so they are counted and rejected (R00) rather than lost.
 * Whole file is loaded in memory.
 */
public interface Decoder {

    List decode(File file, StreamConfig cfg) throws IOException;
}
