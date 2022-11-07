package io.github.stephenc.visualfit;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(name = "empty", description = "Use an empty data set to explore adding Gompertz peaks")
public class EmptyData implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        List<Date> times = new ArrayList<>();
        List<Number> values = new ArrayList<>();

        for (int i = 0; i < 365; i++) {
            times.add(new Date(
                    LocalDate.of(2022, 1, 1).plusDays(i).atStartOfDay().atOffset(ZoneOffset.UTC).toInstant()
                            .toEpochMilli()));
            values.add(
                    10000 * (Math.exp(-Math.exp((-0.05 * (i - 180)))) - Math.exp(
                            -Math.exp((-0.05 * (i - 181))))));
        }
        new VisualFit(times, values).show();
        return 0;
    }
}
