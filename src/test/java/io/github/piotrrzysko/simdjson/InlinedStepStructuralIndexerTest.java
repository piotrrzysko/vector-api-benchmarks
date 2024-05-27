package io.github.piotrrzysko.simdjson;

public class InlinedStepStructuralIndexerTest extends StructuralIndexerTest {

    @Override
    protected void index(BitIndexes bitIndexes, byte[] buffer, int length) {
        InlinedStepStructuralIndexer indexer = new InlinedStepStructuralIndexer(bitIndexes);
        indexer.index(buffer, length);
    }
}
