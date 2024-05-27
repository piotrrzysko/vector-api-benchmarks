package io.github.piotrrzysko.simdjson;

public class LoadingInStepStructuralIndexerTest extends StructuralIndexerTest {

    @Override
    protected void index(BitIndexes bitIndexes, byte[] buffer, int length) {
        LoadingInStepStructuralIndexer indexer = new LoadingInStepStructuralIndexer(bitIndexes);
        indexer.index(buffer, length);
    }
}
