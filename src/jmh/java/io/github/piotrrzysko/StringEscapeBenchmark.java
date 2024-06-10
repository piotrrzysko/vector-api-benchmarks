package io.github.piotrrzysko;

import io.github.piotrrzysko.escape.ScalarCompressStringEscape;
import io.github.piotrrzysko.escape.VectorStringEscape;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class StringEscapeBenchmark {

    private final byte[] src = "\\bbbbb\\nnnn\\baaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8);
    private final byte[] dst = new byte[1024];

    @Benchmark
    public int vector() {
        return VectorStringEscape.escape(src, dst);
    }

    @Benchmark
    public int scalar() {
        return ScalarCompressStringEscape.escape(src, dst);
    }
}
