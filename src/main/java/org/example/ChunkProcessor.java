package org.example;

import java.io.Serializable;

public class ChunkProcessor implements Serializable {

    private static final long serialVersionUID = -8784122993424148022L;
    private ChunkProcess chunkProcess;

    private ChunkProcessor() {
        super();
    }

    public ChunkProcessor(ChunkProcess chunkProcess) {
        this.chunkProcess = chunkProcess;
    }

    public static ChunkProcessor instance(ChunkProcess chunkProcess) {
        return new ChunkProcessor(chunkProcess);
    }

    public void execute() throws Exception {
       // if (chunkProcess.getTotal() == null) throw new OperationFailureException("Invalid Total");
      //  if (chunkProcess.getSlice() == null) throw new OperationFailureException("Invalid Slice");
        chunkProcess.beforeAllCode();
        for (int i = 0; i < Math.ceil((double) chunkProcess.getTotal() / chunkProcess.getSlice()); i++) {
            chunkProcess.setPager(new SimplePageRequest(i, chunkProcess.getSlice()));
            chunkProcess.beforeCode();
            chunkProcess.code();
            chunkProcess.afterCode();
        }
        chunkProcess.afterAllCode();
        chunkProcess.result();
    }
}
