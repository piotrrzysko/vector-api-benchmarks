# vector-api-benchmarks

## StructuralIndexerBenchmark

To run the `StructuralIndexerBenchmark` execute:

```shell
./gradlew jmh -Pjmh.includes='.*StructuralIndexerBenchmark.*'
```

If you want to enable profilers add `-Pjmh.profilersEnabled=true`:

```shell
./gradlew jmh -Pjmh.profilersEnabled=true -Pjmh.includes='.*StructuralIndexerBenchmark.*'
```
