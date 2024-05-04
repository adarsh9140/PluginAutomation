
package org.example;


final public class ChunkExecutor {

    private ChunkExecutor() {
        super();
    }

    public static void execute(ChunkProcess chunkProcess) throws Exception {
        ChunkProcessor.instance(chunkProcess).execute();
    }
}
