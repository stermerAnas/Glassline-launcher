# Performance measurement

Macrobenchmark module wiring is intentionally not included in the first runnable project because it requires a separate benchmark application/module and device-side profiling. Use Android Studio Profiler / FrameTimeline first, then add `androidx.benchmark:benchmark-macro-junit4` and a `com.android.test` module. Record cold startup with `StartupTimingMetric`, scroll with `FrameTimingMetric`, and compare release builds on the Galaxy A34 at 120 Hz. Do not infer 120 fps from emulator results.
