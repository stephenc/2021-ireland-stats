package io.github.stephenc.visualfit;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import io.github.stephenc.visualfit.data.Antigen;
import io.github.stephenc.visualfit.data.LabTests;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;

import static io.github.stephenc.visualfit.Main.parseTimestamp;

@CommandLine.Command(name = "ireland", description = "Use Irish data as source")
public class IrishData implements Callable<Integer> {

    static class Selection {
        @CommandLine.Option(names="--antigen-only", defaultValue = "false") boolean antigenOnly;
        @CommandLine.Option(names="--pcr-only", defaultValue = "false") boolean pcrOnly;
    }

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    Selection selection = new Selection();

    @Override
    public Integer call() throws Exception {
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        var antigenWeight = selection.pcrOnly ? 0 : 1;
        var pcrWeight = selection.antigenOnly ? 0 : 1;
        var estimateAntigen = !selection.antigenOnly;

        List<LabTests> labTests = csvMapper.readerFor(LabTests.class)
                .with(schema)
                .<LabTests>readValues(
                        Paths.get("../data/COVID-19_Laboratory_Testing_Time_Series.csv").toFile()).readAll();

        labTests.sort(Comparator.comparing(r -> r.timestamp));

        List<Antigen> antigens = csvMapper.readerFor(Antigen.class)
                .with(schema)
                .<Antigen>readValues(
                        Paths.get("../data/COVID-19_Antigen.csv").toFile()).readAll();

        antigens.sort(Comparator.comparing(r -> r.timestamp));

        List<Date> times;
        List<Number> values;

        {
            Date firstAntigen = parseTimestamp(antigens.get(0).timestamp);
            times = new ArrayList<>(labTests.size());
            values = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            Double[] antigenRatio = new Double[1];
            Predictor predictor = new Predictor();
            labTests.forEach(r -> {
                Date timestamp = parseTimestamp(r.timestamp);
                times.add(timestamp);
                long pcrPositives = r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives);
                long antigenPositives = antigens.stream()
                        .filter(x -> parseTimestamp(x.timestamp).toInstant()
                                .atOffset(ZoneOffset.UTC).toLocalDate()
                                .equals(timestamp.toInstant().atOffset(ZoneOffset.UTC)
                                        .toLocalDate()))
                        .map(x -> x.positives)
                        .findAny()
                        .map(p -> {
                            if (pcrPositives > 0) {
                                antigenRatio[0] = antigenRatio[0] == null
                                        ? p / pcrPositives
                                        : 0.9 * antigenRatio[0] + 0.1 * p / pcrPositives;
                                predictor.train(
                                        timestamp.toInstant().atOffset(ZoneOffset.UTC)
                                                .get(ChronoField.DAY_OF_WEEK),
                                        pcrPositives, p);
                            }
                            return p;
                        })
                        .orElseGet(() -> {
                            if (estimateAntigen && firstAntigen.getTime() < timestamp.getTime()) {
                                var predicted = Math.round(predictor.guess(
                                        timestamp.toInstant().atOffset(ZoneOffset.UTC)
                                                .get(ChronoField.DAY_OF_WEEK),
                                        pcrPositives)
                                );
                                var estimated = Math.round(antigenRatio[0] * pcrPositives);
                                System.out.printf("%s predicted = %d estimated = %d%n", timestamp, predicted,
                                        estimated);
                                return estimated;
                            } else {
                                return 0L;
                            }
                        });
                if (!estimateAntigen && antigenPositives == 0L) {
                    times.remove(values.size());
                } else {
                    values.add(pcrWeight * pcrPositives + antigenWeight * antigenPositives);
                }
                previous[0] = r;
            });
            System.out.println(predictor);
        }
        new VisualFit(times, values).show();
        return 0;
    }
}
