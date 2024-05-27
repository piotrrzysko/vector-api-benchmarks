package io.github.piotrrzysko;

import io.github.piotrrzysko.simdjson.BitIndexes;
import io.github.piotrrzysko.simdjson.InlinedIndexStructuralIndexer;
import io.github.piotrrzysko.simdjson.InlinedStepStructuralIndexer;
import io.github.piotrrzysko.simdjson.LoadingInStepStructuralIndexer;
import io.github.piotrrzysko.simdjson.OriginalStructuralIndexer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class StructuralIndexerBenchmark {

    private final BitIndexes bitIndexes = new BitIndexes(128 * 1024);
    private final OriginalStructuralIndexer original = new OriginalStructuralIndexer(bitIndexes);
    private final LoadingInStepStructuralIndexer loadingInStep = new LoadingInStepStructuralIndexer(bitIndexes);
    private final InlinedIndexStructuralIndexer inlinedIndex = new InlinedIndexStructuralIndexer(bitIndexes);
    private final InlinedStepStructuralIndexer inlinedStep = new InlinedStepStructuralIndexer(bitIndexes);

    private byte[] bytes;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        try (InputStream is = StructuralIndexerBenchmark.class.getResourceAsStream("/twitter.json")) {
            bytes = is.readAllBytes();
        }
    }

    @Benchmark
    public int original() {
        original.index(bytes, bytes.length);
        return bitIndexes.getLast();
    }

    @Benchmark
    public int loadingInStep() {
        loadingInStep.index(bytes, bytes.length);
        return bitIndexes.getLast();
    }

    @Benchmark
    public int inlinedIndex() {
        inlinedIndex.index(bytes, bytes.length);
        return bitIndexes.getLast();
    }

    @Benchmark
    public int inlinedStep() {
        inlinedStep.index(bytes, bytes.length);
        return bitIndexes.getLast();
    }
}
