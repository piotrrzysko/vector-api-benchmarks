import me.champeau.jmh.JmhBytecodeGeneratorTask

plugins {
    id("java")
    id("me.champeau.jmh") version "0.7.2"
}

group = "io.github.piotrrzysko"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-modules", "jdk.incubator.vector")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("--add-modules=jdk.incubator.vector")
}

tasks.withType<JmhBytecodeGeneratorTask> {
    jvmArgs.set(listOf("--add-modules=jdk.incubator.vector"))
}

jmh {
    fork = 1
    warmupIterations = 3
    iterations = 5
    jvmArgsPrepend.set(listOf("--add-modules=jdk.incubator.vector"))

    if (getBooleanProperty("jmh.profilersEnabled", false)) {
        createDirIfDoesNotExist("./profilers/perfasm")
        profilers.add("perfasm:intelSyntax=true;saveLog=true;saveLogTo=./profilers/perfasm")
        profilers.add("perf")
        profilers.add("gc")

        val asyncProfilerPath = System.getenv("LD_LIBRARY_PATH") ?: System.getProperty("java.library.path")
        if (asyncProfilerPath != null) {
            createDirIfDoesNotExist("./profilers/async")
            profilers.add("async:verbose=true;output=flamegraph;event=alloc;dir=./profilers/async;libPath=${asyncProfilerPath}")
        }
    }

    if (project.hasProperty("jmh.includes")) {
        includes.set(listOf(project.findProperty("jmh.includes").toString()))
    }
}

fun createDirIfDoesNotExist(dir: String) {
    val file = File(dir)
    file.mkdirs()
}

fun getBooleanProperty(name: String, defaultValue: Boolean) =
    project.findProperty(name)?.toString()?.toBoolean() ?: defaultValue
