package io.github.piotrrzysko.simdjson;

public class OriginalStructuralIndexerTest extends StructuralIndexerTest {

    @Override
    protected void index(BitIndexes bitIndexes, byte[] buffer, int length) {
        OriginalStructuralIndexer indexer = new OriginalStructuralIndexer(bitIndexes);
        indexer.index(buffer, length);
    }
}
