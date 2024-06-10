package io.github.piotrrzysko.escape;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class StringEscapeTest {

    @Test
    public void stringEscape() {
        byte[] src = "\\bbbbb\\nnnn\\baaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8);

        byte[] vectorDst = new byte[1024];
        int vectorDstIdx = VectorStringEscape.escape(src, vectorDst);
        assertThat(vectorDstIdx).isEqualTo(61);

        byte[] scalarDst = new byte[1024];
        int scalarDstIdx = ScalarCompressStringEscape.escape(src, scalarDst);
        assertThat(scalarDstIdx).isEqualTo(61);

        assertThat(vectorDst).isEqualTo(scalarDst);
    }
}
