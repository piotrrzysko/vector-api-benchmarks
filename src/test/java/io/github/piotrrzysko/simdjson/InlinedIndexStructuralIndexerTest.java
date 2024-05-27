package io.github.piotrrzysko.simdjson;

public class InlinedIndexStructuralIndexerTest extends StructuralIndexerTest {

    @Override
    protected void index(BitIndexes bitIndexes, byte[] buffer, int length) {
        InlinedIndexStructuralIndexer indexer = new InlinedIndexStructuralIndexer(bitIndexes);
        indexer.index(buffer, length);
    }
}
