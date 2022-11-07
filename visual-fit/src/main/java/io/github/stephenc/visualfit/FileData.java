package io.github.stephenc.visualfit;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import java.io.File;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;

import static io.github.stephenc.visualfit.Main.parseTimestamp;

/**
 * @author Stephen Connolly
 */
@CommandLine.Command(name = "file", description = "Use a CSV file as a source")
public class FileData implements Callable<Integer> {
    @CommandLine.Parameters(arity = "1", description = "The CSV file to load")
    File file;

    @CommandLine.Option(names = "--timestamp-column",
                        defaultValue = "0",
                        description = "The zero-based index of the column holding the timestamps")
    int timestampColumn;
    @CommandLine.Option(names = "--value-column",
                        defaultValue = "1",
                        description = "The zero-based index of the column holding the values")
    int valueColumn;

    @Override
    public Integer call() throws Exception {
        List<Date> times;
        List<Number> values;

        CsvMapper csvMapper = new CsvMapper();
        List<List<String>> data = csvMapper.readerForListOf(String.class)
                .with(CsvParser.Feature.WRAP_AS_ARRAY)
                .<List<String>>readValues(file).readAll();
        times = new ArrayList<>();
        values = new ArrayList<>();
        data.forEach(r -> {
            try {
                Date timestamp = parseTimestamp(r.get(timestampColumn) + " 00:00:00+00");
                times.add(timestamp);
                values.add(Double.valueOf(r.get(valueColumn)));
            } catch (DateTimeParseException e) {
                e.printStackTrace();
            }
        });

        new VisualFit(times, values).show();
        return 0;
    }
}
