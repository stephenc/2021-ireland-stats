package io.github.stephenc.visualfit;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import io.github.stephenc.visualfit.data.OWIDData;
import io.github.stephenc.visualfit.data.OWIDTesting;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;

import static io.github.stephenc.visualfit.Main.parseTimestamp;

@CommandLine.Command(name="owid", description = "Use OWID data as source")
public class Owid implements Callable<Integer> {

    @CommandLine.Parameters(arity = "1", paramLabel = "ISO")
    String iso;

    @Override
    public Integer call() throws Exception {
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        List<OWIDData> tests = csvMapper.readerFor(OWIDData.class)
                .with(schema)
                .<OWIDData>readValues(
                        Paths.get("owid-covid-data.csv").toFile()).readAll();
        tests.removeIf(r -> !iso.equalsIgnoreCase(r.isoCode));
        tests.sort(Comparator.comparing(r -> r.timestamp));
        List<Date> times = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        tests.forEach(r -> {
            Date timestamp = parseTimestamp(r.timestamp + " 00:00:00+00");
            times.add(timestamp);
            values.add(r.daily);
        });

        new VisualFit(times, values).show();
        return 0;
    }
}
