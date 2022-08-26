///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 11+
//JAVAC_OPTIONS -Xlint:unchecked
//DEPS com.fasterxml.jackson.core:jackson-databind:2.12.4
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.12.2
//DEPS org.apache.commons:commons-text:1.9
//DEPS org.knowm.xchart:xchart:3.8.1

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.markers.SeriesMarkers;

public class graphs {

    protected static final OffsetDateTime GIF_END_DATE = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC).plusDays(15);
    protected static final OffsetDateTime GIF_START_DATE = GIF_END_DATE.minusMonths(8);
    private static boolean guessAntigen = true;
    protected static final double FRACTION_OVER_40_YEARS_OLD = (2056616.0 / (2705249.0 + 2056616.0));
    protected static final double FRACTION_UNDER_40_YEARS_OLD = 1.0 - FRACTION_OVER_40_YEARS_OLD;

    private static boolean trendGuessing = true;

    public static void main(String... args) throws IOException {

        if (Stream.of(args).map(String::toLowerCase).anyMatch("--no-guess"::equals)) {
            guessAntigen = false;
        }
        if (guessAntigen) {
            if (Stream.of(args).map(String::toLowerCase).anyMatch("--no-trend"::equals)) {
                trendGuessing = false;
            }
            if (trendGuessing) {
                System.err.println("Missing Antigen Data will be substituted based on the historic ratio with PCR");
            } else {
                System.err.println("Missing Antigen Data will be substituted based on the PCR normalized for all pop");
            }
        }
        LocalDate graphDate = LocalDate.now().plusDays(Stream.of(args).filter(x -> x.matches("^-\\d+$")).findAny().map(Integer::valueOf).orElse(0));
        Instant cutOffDate = graphDate.atStartOfDay().plusDays(1).toInstant(ZoneOffset.UTC);
        Instant thirtyDaysAgo = graphDate.minusDays(30).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant sixtyDaysAgo = graphDate.minusDays(60).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant ninetyDaysAgo = graphDate.minusDays(90).atStartOfDay().toInstant(ZoneOffset.UTC);

        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        List<AcuteHospital> acuteHospitals = csvMapper.readerFor(AcuteHospital.class)
                .with(schema)
                .<AcuteHospital>readValues(
                        Paths.get("data/COVID-19_SDU_Acute_Hospital_Time_Series_Summary.csv").toFile()).readAll();
        acuteHospitals.sort(Comparator.comparing(r -> r.timestamp));
        acuteHospitals.removeIf(r -> parseTimestamp(r.timestamp).toInstant()
                .isAfter(cutOffDate));

        List<ICUHospital> icuHospitals = csvMapper.readerFor(ICUHospital.class)
                .with(schema)
                .<ICUHospital>readValues(
                        Paths.get("data/COVID-19_NOCA_ICUBIS_Historic_Time_Series.csv").toFile()).readAll();

        icuHospitals.sort(Comparator.comparing(r -> r.timestamp));
        icuHospitals.removeIf(r -> parseTimestamp(r.timestamp).toInstant()
                .isAfter(cutOffDate));

        List<LabTests> labTests = csvMapper.readerFor(LabTests.class)
                .with(schema)
                .<LabTests>readValues(
                        Paths.get("data/COVID-19_Laboratory_Testing_Time_Series.csv").toFile()).readAll();

        labTests.sort(Comparator.comparing(r -> r.timestamp));
        labTests.removeIf(r -> parseTimestamp(r.timestamp).toInstant()
                .isAfter(cutOffDate));

        List<Stats> stats = csvMapper.readerFor(Stats.class)
                .with(schema)
                .<Stats>readValues(
                        Paths.get("data/COVID-19_HPSC_Detailed_Statistics_Profile.csv").toFile()).readAll();

        stats.sort(Comparator.comparing(r -> r.timestamp));
        stats.removeIf(r -> parseTimestamp(r.timestamp).toInstant()
                .isAfter(cutOffDate));

        List<Antigen> antigens = csvMapper.readerFor(Antigen.class)
                .with(schema)
                .<Antigen>readValues(
                        Paths.get("data/COVID-19_Antigen.csv").toFile()).readAll();

        antigens.sort(Comparator.comparing(r -> r.timestamp));
        antigens.removeIf(r -> parseTimestamp(r.timestamp).toInstant()
                .isAfter(cutOffDate));

        List<WeeklyVax> weeklyVax = csvMapper.readerFor(WeeklyVax.class)
                .with(schema)
                .<WeeklyVax>readValues(
                        Paths.get("data/COVID-19_HSE_Weekly_Vaccination_Figures.csv").toFile()).readAll();

        weeklyVax.sort(Comparator.comparing(r -> r.week));
        weeklyVax.removeIf(r -> parseWeekNum(r.week).toInstant()
                .isAfter(cutOffDate));

        new File("./graphs").mkdirs();

        {
            XYChart chart = new XYChartBuilder()
                    .width(1200)
                    .height(675)
                    .theme(Styler.ChartTheme.Matlab)
                    .xAxisTitle("Date")
                    .yAxisTitle("Correlation Offset")
                    .title("How many days after gives the best pairwise Pearson correlation between windowed data sets")
                    .build();
            chart.getStyler().setDatePattern("dd-MMM-yyyy");

            List<Date> caseTimes = new ArrayList<>(labTests.size());
            List<Number> caseValues = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            labTests.forEach(r -> {
                caseTimes.add(parseTimestamp(r.timestamp));
                caseValues.add((r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives)) * 100.0 / (
                        r.totalLabsTotal - (previous[0] == null ? 0 : previous[0].totalLabsTotal)));
                previous[0] = r;
            });
            List<Date> hospitalTimes = new ArrayList<>(acuteHospitals.size());
            List<Number> hospitalValues = new ArrayList<>(acuteHospitals.size());
            acuteHospitals.forEach(r -> {
                hospitalTimes.add(parseTimestamp(r.timestamp));
                hospitalValues.add(r.admissionsCovidPositive);
            });
            List<Date> icuTimes = new ArrayList<>(acuteHospitals.size());
            List<Number> icuValues = new ArrayList<>(acuteHospitals.size());
            icuHospitals.forEach(r -> {
                icuTimes.add(parseTimestamp(r.timestamp));
                icuValues.add(r.admissionsCovidPositive);
            });
            List<Date> deathTimes = new ArrayList<>(acuteHospitals.size());
            List<Number> deathValues = new ArrayList<>(acuteHospitals.size());
            stats.forEach(r -> {
                deathTimes.add(parseTimestamp(r.timestamp));
                deathValues.add(r.deathsTodayDOD);
            });

            List<Date> dates = fillMissingDays(caseTimes);
            List<Number> cases = extractMatching(dates, caseTimes, caseValues);
            List<Number> admissions = extractMatching(dates, hospitalTimes, hospitalValues);
            List<Number> icus = extractMatching(dates, icuTimes, icuValues);
            List<Number> deaths = extractMatching(dates, deathTimes, deathValues);

            chart.addSeries("Hospital after cases", dates,
                            correlationOffsets(
                                    gaussianSmooth(replaceNulls(cases, 0), 5),
                                    gaussianSmooth(replaceNulls(admissions, 0), 5),
                                    60,
                                    15)
                    )
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);

            chart.addSeries("ICU after cases", dates,
                            correlationOffsets(
                                    gaussianSmooth(replaceNulls(cases, 0), 5),
                                    gaussianSmooth(replaceNulls(icus, 0), 5),
                                    60,
                                    15)
                    )
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);

            chart.addSeries("Deaths after cases", dates,
                            correlationOffsets(
                                    gaussianSmooth(replaceNulls(cases, 0), 5),
                                    gaussianSmooth(replaceNulls(deaths, 0), 5),
                                    60,
                                    30)
                    )
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);

            chart.addSeries("Deaths after icu", dates,
                            correlationOffsets(
                                    gaussianSmooth(replaceNulls(icus, 0), 5),
                                    gaussianSmooth(replaceNulls(deaths, 0), 5),
                                    60,
                                    20)
                    )
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);

            chart.addSeries("Deaths after hospital", dates,
                            correlationOffsets(
                                    gaussianSmooth(replaceNulls(admissions, 0), 5),
                                    gaussianSmooth(replaceNulls(deaths, 0), 5),
                                    60,
                                    20)
                    )
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);

            chart.addSeries("ICU after hospital", dates,
                            correlationOffsets(
                                    gaussianSmooth(replaceNulls(admissions, 0), 5),
                                    gaussianSmooth(replaceNulls(icus, 0), 5),
                                    60,
                                    15)
                    )
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);

            BitmapEncoder.saveBitmap(chart, "./graphs/correlation_offsets.png",
                    BitmapEncoder.BitmapFormat.PNG);
            System.err.println("./graphs/correlation_offsets.png");

            //System.exit(0);
        }

        XYChart chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Confirmed Cases")
                .title("Current Covid-19 Hospital cases")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(acuteHospitals.size());
            List<Number> values = new ArrayList<>(acuteHospitals.size());
            acuteHospitals.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values.add(r.currentConfirmedCovidPositive);
            });
            chart.addSeries("Current", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(acuteHospitals.size());
            List<Number> values = new ArrayList<>(acuteHospitals.size());
            acuteHospitals.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values.add(r.newCovidCasesCovid);
            });
            chart.addSeries("New", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(acuteHospitals.size());
            List<Number> values = new ArrayList<>(acuteHospitals.size());
            acuteHospitals.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values.add(r.admissionsCovidPositive);
            });
            chart.addSeries("Admissions", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(acuteHospitals.size());
            List<Number> values = new ArrayList<>(acuteHospitals.size());
            acuteHospitals.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values.add(r.dischargesCovidPositive == null ? null : -r.dischargesCovidPositive);
            });
            chart.addSeries("Discharges", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_SDU_Acute_Hospital_Time_Series_Summary.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_SDU_Acute_Hospital_Time_Series_Summary.png");

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Confirmed Cases")
                .title("Current Covid-19 Hospital cases")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(acuteHospitals.size());
            List<Number> values = new ArrayList<>(acuteHospitals.size());
            acuteHospitals.forEach(r -> {
                Date ts = parseTimestamp(r.timestamp);
                if (ts.getTime() >= thirtyDaysAgo.toEpochMilli()) {
                    times.add(ts);
                    values.add(r.currentConfirmedCovidPositive);
                }
            });
            chart.addSeries("Current", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(acuteHospitals.size());
            List<Number> values = new ArrayList<>(acuteHospitals.size());
            acuteHospitals.forEach(r -> {
                Date ts = parseTimestamp(r.timestamp);
                if (ts.getTime() >= thirtyDaysAgo.toEpochMilli()) {
                    times.add(ts);
                    values.add(r.newCovidCasesCovid);
                }
            });
            chart.addSeries("New", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(acuteHospitals.size());
            List<Number> values = new ArrayList<>(acuteHospitals.size());
            acuteHospitals.forEach(r -> {
                Date ts = parseTimestamp(r.timestamp);
                if (ts.getTime() >= thirtyDaysAgo.toEpochMilli()) {
                    times.add(ts);
                    values.add(r.admissionsCovidPositive);
                }
            });
            chart.addSeries("Admissions", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(acuteHospitals.size());
            List<Number> values = new ArrayList<>(acuteHospitals.size());
            acuteHospitals.forEach(r -> {
                Date ts = parseTimestamp(r.timestamp);
                if (ts.getTime() >= thirtyDaysAgo.toEpochMilli()) {
                    times.add(ts);
                    values.add(r.dischargesCovidPositive == null ? null : -r.dischargesCovidPositive);
                }
            });
            chart.addSeries("Discharges", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        chart.getStyler().setXAxisMin(thirtyDaysAgo.toEpochMilli() * 1.0);
        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_SDU_Acute_Hospital_Time_Series_Last30.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_SDU_Acute_Hospital_Time_Series_Last30.png");

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Confirmed Deaths")
                .title("Deaths with Covid-19 by day of death")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(stats.size());
            List<Number> values = new ArrayList<>(stats.size());
            stats.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values.add(r.deathsTodayDOD);
            });
            chart.addSeries("Deaths", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        // {
        //     List<Date> times = new ArrayList<>(stats.size());
        //     List<Number> values = new ArrayList<>(stats.size());
        //     stats.forEach(r -> {
        //         times.add(parseTimestamp(r.timestamp));
        //         values.add(r.confirmedCovidDeaths);
        //     });
        //     chart.addSeries("Reporting", times, values)
        //             .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        // }

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Deaths.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Deaths.png");


        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Confirmed Deaths")
                .title("Deaths with Covid-19 by day of death")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(stats.size());
            List<Number> values = new ArrayList<>(stats.size());
            stats.forEach(r -> {
                Date ts = parseTimestamp(r.timestamp);
                if (ts.getTime() >= thirtyDaysAgo.toEpochMilli()) {
                    times.add(ts);
                    values.add(r.deathsTodayDOD);
                }
            });
            chart.addSeries("Deaths", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        chart.getStyler().setXAxisMin(thirtyDaysAgo.toEpochMilli() * 1.0);
        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Deaths_Last30.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Deaths_Last30.png");


        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Number of people")
                .title("New weekly partial vaccination numbers")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.partialAge80Plus);
            });
            chart.addSeries("80yo+ partial vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.partialAge70To79);
            });
            chart.addSeries("70-79yo partial vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.partialAge60To69);
            });
            chart.addSeries("60-69yo partial vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.partialAge50To59);
            });
            chart.addSeries("50-59yo partial vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.partialAge40To49);
            });
            chart.addSeries("40-49yo partial vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.partialAge30To39);
            });
            chart.addSeries("30-39yo partial vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.partialAge20To29);
            });
            chart.addSeries("20-29yo partial vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.partialAge10To19);
            });
            chart.addSeries("10-19yo partial vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.partialAge0To9);
            });
            chart.addSeries("0-9yo partial vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        chart.getStyler().setXAxisMin(parseTimestamp(stats.get(0).timestamp).toInstant().toEpochMilli() * 1.0);

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Vaccine_rollout.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Vaccine_rollout.png");

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Number of people")
                .title("New weekly vaccination numbers")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.totalWeeklyVaccines);
            });
            chart.addSeries("Total", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        chart.getStyler().setXAxisMin(parseTimestamp(stats.get(0).timestamp).toInstant().toEpochMilli() * 1.0);

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Vaccine_rollout_totals.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Vaccine_rollout_totals.png");

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Number of people")
                .title("New weekly full vaccination numbers")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.fullyAge80Plus);
            });
            chart.addSeries("80yo+ fully vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.fullyAge70To79);
            });
            chart.addSeries("70-79yo fully vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.fullyAge60To69);
            });
            chart.addSeries("60-69yo fully vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.fullyAge50To59);
            });
            chart.addSeries("50-59yo fully vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.fullyAge40To49);
            });
            chart.addSeries("40-49yo fully vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.fullyAge30To39);
            });
            chart.addSeries("30-39yo fully vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.fullyAge20To29);
            });
            chart.addSeries("20-29yo fully vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.fullyAge10To19);
            });
            chart.addSeries("10-19yo fully vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(weeklyVax.size());
            List<Number> values = new ArrayList<>(weeklyVax.size());
            weeklyVax.forEach(r -> {
                times.add(parseWeekNum(r.week));
                values.add(r.fullyAge0To9);
            });
            chart.addSeries("0-9yo fully vax", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        chart.getStyler().setXAxisMin(parseTimestamp(stats.get(0).timestamp).toInstant().toEpochMilli() * 1.0);

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Vaccine_rollout_full.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Vaccine_rollout_full.png");

        CategoryChart chart2 = new CategoryChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Number of cases")
                .title("Covid-19 Hospital cases breakdown")
                .build();
        chart2.getStyler().setDatePattern("dd-MMM-yyyy");
        chart2.getStyler().setXAxisLabelRotation(90);
        chart2.getStyler().setStacked(true);

        {
            List<Date> times = new ArrayList<>(acuteHospitals.size());
            List<Number> values1 = new ArrayList<>(acuteHospitals.size());
            List<Number> values2 = new ArrayList<>(acuteHospitals.size());
            List<Number> values3 = new ArrayList<>(acuteHospitals.size());
            OffsetDateTime cutOff = OffsetDateTime.now().minus(Period.of(0, 0, 28));
            String threshold = cutOff.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            acuteHospitals.stream().filter(r -> r.timestamp.compareTo(threshold) > 0).forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values1.add(r.admissionsCovidPositive);
                if (r.admissionsCovidPositive != null && r.newCovidCasesCovid != null && r.newCovidCasesCovid > 0) {
                    values2.add(r.newCovidCasesCovid - r.admissionsCovidPositive);
                } else {
                    values2.add(null);
                }
                values3.add(r.newCovidCasesCovid);
            });
            chart2.addSeries("Admissions", times, values1)
                    .setMarker(SeriesMarkers.NONE);
            chart2.addSeries("Non-Admissions", times, values2)
                    .setMarker(SeriesMarkers.NONE);
        }

        BitmapEncoder.saveBitmap(chart2, "./graphs/COVID-19_SDU_Acute_Hospital_Time_Series_New_cases_breakdown.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_SDU_Acute_Hospital_Time_Series_New_cases_breakdown.png");

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("% of new cases in hospital")
                .title("Covid-19 Hospital admissions as a fraction of new cases in Hospital")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");
        chart.getStyler().setYAxisMax(100.0);
        chart.getStyler().setYAxisMin(0.0);

        {
            List<Date> times = new ArrayList<>(acuteHospitals.size());
            List<Number> values = new ArrayList<>(acuteHospitals.size());
            acuteHospitals.stream().filter(r -> r.timestamp.compareTo("2021/07") > 0).forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                if (r.admissionsCovidPositive != null && r.newCovidCasesCovid != null && r.newCovidCasesCovid > 0) {
                    values.add(r.admissionsCovidPositive * 100.0 / r.newCovidCasesCovid);
                } else {
                    values.add(null);
                }
            });
            chart.addSeries("Admissions", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_SDU_Acute_Hospital_Time_Series_New_Vs_Admissions.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_SDU_Acute_Hospital_Time_Series_New_Vs_Admissions.png");

        chart.getStyler().setXAxisMin(sixtyDaysAgo.toEpochMilli() * 1.0);
        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_SDU_Acute_Hospital_Time_Series_New_Vs_Admissions_Last60.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_SDU_Acute_Hospital_Time_Series_New_Vs_Admissions_Last60.png");


        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Confirmed Cases")
                .title("Current Covid-19 ICU cases")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(icuHospitals.size());
            List<Number> values = new ArrayList<>(icuHospitals.size());
            icuHospitals.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values.add(r.currentConfirmedCovidPositive);
            });
            chart.addSeries("Current", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(icuHospitals.size());
            List<Number> values = new ArrayList<>(icuHospitals.size());
            icuHospitals.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values.add(r.admissionsCovidPositive);
            });
            chart.addSeries("Admissions", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(icuHospitals.size());
            List<Number> values = new ArrayList<>(icuHospitals.size());
            icuHospitals.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values.add(r.dischargesCovidPositive == null ? null : -r.dischargesCovidPositive);
            });
            chart.addSeries("Discharges", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_NOCA_ICUBIS_Historic_Time_Series.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_NOCA_ICUBIS_Historic_Time_Series.png");


        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Confirmed Cases")
                .title("Current Covid-19 ICU cases")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(icuHospitals.size());
            List<Number> values = new ArrayList<>(icuHospitals.size());
            icuHospitals.forEach(r -> {
                Date ts = parseTimestamp(r.timestamp);
                if (ts.getTime() >= thirtyDaysAgo.toEpochMilli()) {
                    times.add(ts);
                    values.add(r.currentConfirmedCovidPositive);
                }
            });
            chart.addSeries("Current", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(icuHospitals.size());
            List<Number> values = new ArrayList<>(icuHospitals.size());
            icuHospitals.forEach(r -> {
                Date ts = parseTimestamp(r.timestamp);
                if (ts.getTime() >= thirtyDaysAgo.toEpochMilli()) {
                    times.add(ts);
                    values.add(r.admissionsCovidPositive);
                }
            });
            chart.addSeries("Admissions", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(icuHospitals.size());
            List<Number> values = new ArrayList<>(icuHospitals.size());
            icuHospitals.forEach(r -> {
                Date ts = parseTimestamp(r.timestamp);
                if (ts.getTime() >= thirtyDaysAgo.toEpochMilli()) {
                    times.add(ts);
                    values.add(r.dischargesCovidPositive == null ? null : -r.dischargesCovidPositive);
                }
            });
            chart.addSeries("Discharges", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_NOCA_ICUBIS_Historic_Time_Series_Last30.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_NOCA_ICUBIS_Historic_Time_Series_Last30.png");

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Confirmed Cases")
                .title("Current Covid-19 cases in Hospital vs in ICU")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(acuteHospitals.size());
            List<Number> values = new ArrayList<>(acuteHospitals.size());
            acuteHospitals.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values.add(r.currentConfirmedCovidPositive);
            });
            chart.addSeries("Hospitalized", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        {
            List<Date> times = new ArrayList<>(icuHospitals.size());
            List<Number> values = new ArrayList<>(icuHospitals.size());
            icuHospitals.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values.add(r.currentConfirmedCovidPositive);
            });
            chart.addSeries("ICU", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_ICU_Vs_All_Hospitalized.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_ICU_Vs_All_Hospitalized.png");

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("% of cases in hospital")
                .title("Current Covid-19 cases in Hospital vs in ICU")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");
        chart.getStyler().setYAxisMax(100.0);
        chart.getStyler().setYAxisMin(0.0);

        {
            List<Date> times = new ArrayList<>(acuteHospitals.size());
            List<Number> values = new ArrayList<>(acuteHospitals.size());
            int i1 = 0;
            int i2 = 0;
            while (i1 < acuteHospitals.size()) {
                AcuteHospital v1 = acuteHospitals.get(i1);
                while (i2 < icuHospitals.size() && icuHospitals.get(i2).timestamp.compareTo(v1.timestamp) < 0) {
                    i2++;
                }
                if (i2 >= icuHospitals.size()) {
                    break;
                }
                ICUHospital v2 = icuHospitals.get(i2);
                times.add(parseTimestamp(v1.timestamp));
                values.add(v2.currentConfirmedCovidPositive * 100.0 / v1.currentConfirmedCovidPositive);
                i1++;
            }
            chart.addSeries("ICU", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Hospitalized_Fraction_ICU.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Hospitalized_Fraction_ICU.png");

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
         * COVID-19_Laboratory_Testing_Time_Series
         * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Number of Positive samples tested")
                .title("Laboratory PCR Tests and Antigen Tests")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            Date firstAntigen = parseTimestamp(antigens.get(0).timestamp);
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> values = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            Double[] antigenRatio = new Double[1];
            labTests.forEach(r -> {
                Date timestamp = parseTimestamp(r.timestamp);
                //Date timestamp = new Date(parseTimestamp(r.timestamp).toInstant().minus(1, java.time.temporal
                // .ChronoUnit.DAYS).toEpochMilli());
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
                            }
                            return p;
                        })
                        .orElseGet(() -> {
                            if (guessAntigen && firstAntigen.getTime() < timestamp.getTime()) {
                                return trendGuessing
                                        ? Math.round(antigenRatio[0] * pcrPositives)
                                        : Math.round(pcrPositives / FRACTION_OVER_40_YEARS_OLD
                                                * FRACTION_UNDER_40_YEARS_OLD);
                            } else {
                                return 0L;
                            }
                        });
                values.add(pcrPositives + antigenPositives);
                previous[0] = r;
            });
            chart.addSeries(guessAntigen ? "Total (est)" : "Total", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
            System.out.printf("Ratio comparison: trend ratio = %.3f population ratio = %.3f%n", antigenRatio[0],
                    FRACTION_UNDER_40_YEARS_OLD/ FRACTION_OVER_40_YEARS_OLD);
        }
        {
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> values = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            labTests.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                //times.add(new Date(parseTimestamp(r.timestamp).toInstant().minus(1, java.time.temporal.ChronoUnit
                // .DAYS).toEpochMilli()));
                values.add(r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives));
                previous[0] = r;
            });
            chart.addSeries("PCR Labs", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE).setLineStyle(
                            new BasicStroke(2F, 0, 1, 10.0F, new float[] {5.0F}, 0.0F));
        }
        {
            List<Date> times = new ArrayList<>(antigens.size());
            List<Number> values = new ArrayList<>(antigens.size());
            antigens.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values.add(r.positives);
            });
            chart.addSeries("Antigen", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE).setLineStyle(
                            new BasicStroke(2F, 0, 1, 10.0F, new float[] {5.0F}, 0.0F));
        }
        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Laboratory_Testing_Time_Series.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Laboratory_Testing_Time_Series.png");

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Number of Positive samples tested")
                .title("Laboratory PCR Tests and Antigen Tests")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            Date firstAntigen = parseTimestamp(antigens.get(0).timestamp);
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> values = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            Double[] antigenRatio = new Double[1];
            labTests.forEach(r -> {
                Date timestamp = parseTimestamp(r.timestamp);
                //Date timestamp = new Date(parseTimestamp(r.timestamp).toInstant().minus(1, java.time.temporal
                // .ChronoUnit.DAYS).toEpochMilli());
                if (timestamp.getTime() >= thirtyDaysAgo.toEpochMilli()) {
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
                                }
                                return p;
                            })
                            .orElseGet(() -> {
                                if (guessAntigen && firstAntigen.getTime() < timestamp.getTime()) {
                                    return trendGuessing
                                            ? Math.round(antigenRatio[0] * pcrPositives)
                                            : Math.round(pcrPositives / FRACTION_OVER_40_YEARS_OLD
                                                    * FRACTION_UNDER_40_YEARS_OLD);
                                } else {
                                    return 0L;
                                }
                            });
                    values.add(pcrPositives + antigenPositives);
                }
                previous[0] = r;
            });
            chart.addSeries(guessAntigen ? "Total (est)" : "Total", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }
        {
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> values = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            labTests.forEach(r -> {
                Date ts = parseTimestamp(r.timestamp);
                if (ts.getTime() >= thirtyDaysAgo.toEpochMilli()) {
                    times.add(ts);
                    //times.add(new Date(parseTimestamp(r.timestamp).toInstant().minus(1, java.time.temporal.ChronoUnit
                    // .DAYS).toEpochMilli()));
                    values.add(r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives));
                }
                previous[0] = r;
            });
            chart.addSeries("PCR Labs", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE).setLineStyle(
                            new BasicStroke(2F, 0, 1, 10.0F, new float[] {5.0F}, 0.0F));
        }
        {
            List<Date> times = new ArrayList<>(antigens.size());
            List<Number> values = new ArrayList<>(antigens.size());
            antigens.forEach(r -> {
                Date ts = parseTimestamp(r.timestamp);
                if (ts.getTime() >= thirtyDaysAgo.toEpochMilli()) {
                    times.add(ts);
                    values.add(r.positives);
                }
            });
            chart.addSeries("Antigen", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE).setLineStyle(
                            new BasicStroke(2F, 0, 1, 10.0F, new float[] {5.0F}, 0.0F));
        }
        chart.getStyler().setXAxisMin(thirtyDaysAgo.toEpochMilli() * 1.0);

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Laboratory_Testing_Time_Series_Last30.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Laboratory_Testing_Time_Series_Last30.png");

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
         * COVID-19_Hospital_Vs_External_Testing_Time_Series
         * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Number of samples tested")
                .title("Laboratory PCR Tests by testing laboratory")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> valuesH = new ArrayList<>(labTests.size());
            List<Number> valuesN = new ArrayList<>(labTests.size());
            List<Number> valuesP = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            labTests.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                //times.add(new Date(parseTimestamp(r.timestamp).toInstant().minus(1, java.time.temporal.ChronoUnit
                // .DAYS).toEpochMilli()));
                valuesH.add(r.totalLabsFromHospitals - (previous[0] == null ? 0 : previous[0].totalLabsFromHospitals));
                valuesN.add(r.totalLabsNotFromHospitals - (previous[0] == null ? 0 : previous[0].totalLabsNotFromHospitals));
                valuesP.add(
                        r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives));
                previous[0] = r;
            });
            chart.addSeries("Hospital", times, valuesH)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
            chart.addSeries("Non-Hospital", times, valuesN)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
            chart.addSeries("Positives (all source)", times, valuesP)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }
        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Hospital_Vs_External_Testing_Time_Series.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Hospital_Vs_External_Testing_Time_Series.png");

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Number of samples tested")
                .title("Laboratory PCR Tests by testing laboratory")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> valuesH = new ArrayList<>(labTests.size());
            List<Number> valuesN = new ArrayList<>(labTests.size());
            List<Number> valuesP = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            labTests.forEach(r -> {
                Date ts = parseTimestamp(r.timestamp);
                if (ts.getTime() >= thirtyDaysAgo.toEpochMilli()) {
                    times.add(ts);
                    //times.add(new Date(parseTimestamp(r.timestamp).toInstant().minus(1, java.time.temporal.ChronoUnit
                    // .DAYS).toEpochMilli()));
                    valuesH.add(
                            r.totalLabsFromHospitals - (previous[0] == null ? 0 : previous[0].totalLabsFromHospitals));
                    valuesN.add(r.totalLabsNotFromHospitals - (previous[0] == null
                            ? 0
                            : previous[0].totalLabsNotFromHospitals));
                    valuesP.add(
                            r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives));
                }
                previous[0] = r;
            });
            chart.addSeries("Hospital", times, valuesH)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
            chart.addSeries("Non-Hospital", times, valuesN)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
            chart.addSeries("Positives (all source)", times, valuesP)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }
        chart.getStyler().setXAxisMin(thirtyDaysAgo.toEpochMilli() * 1.0);

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Hospital_Vs_External_Testing_Time_Series_Last30.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Hospital_Vs_External_Testing_Time_Series_Last30.png");

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
         * COVID-19_Number_of_tests_Time_Series
         * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Number of samples tested")
                .title("Laboratory Testing Load")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            Date firstAntigen = parseTimestamp(antigens.get(0).timestamp);
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> values = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            labTests.forEach(r -> {
                Date timestamp = parseTimestamp(r.timestamp);
                //Date timestamp = new Date(parseTimestamp(r.timestamp).toInstant().minus(1, java.time.temporal
                // .ChronoUnit.DAYS).toEpochMilli());
                times.add(timestamp);
                long pcrTests = r.totalLabsTotal - (previous[0] == null ? 0 : previous[0].totalLabsTotal);
                values.add(pcrTests);
                previous[0] = r;
            });
            chart.addSeries("Number of PCR tests", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }
        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Number_of_tests_Time_Series.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Number_of_tests_Time_Series.png");

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Number of samples tested")
                .title("Laboratory Testing Load")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            Date firstAntigen = parseTimestamp(antigens.get(0).timestamp);
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> values = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            labTests.forEach(r -> {
                Date timestamp = parseTimestamp(r.timestamp);
                //Date timestamp = new Date(parseTimestamp(r.timestamp).toInstant().minus(1, java.time.temporal
                // .ChronoUnit.DAYS).toEpochMilli());
                if (timestamp.getTime() >= thirtyDaysAgo.toEpochMilli()) {
                    times.add(timestamp);
                    long pcrTests = r.totalLabsTotal - (previous[0] == null ? 0 : previous[0].totalLabsTotal);
                    values.add(pcrTests);
                }
                previous[0] = r;
            });
            chart.addSeries("Number of PCR tests", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }
        chart.getStyler().setXAxisMin(thirtyDaysAgo.toEpochMilli() * 1.0);

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Number_of_tests_Time_Series_Last30.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Number_of_tests_Time_Series_Last30.png");


        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("% samples tested PCR Positives")
                .title("Laboratory PCR Tests")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> values = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            labTests.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values.add((r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives)) * 100.0 / (
                        r.totalLabsTotal - (previous[0] == null ? 0 : previous[0].totalLabsTotal)));
                previous[0] = r;
            });
            chart.addSeries("Labs", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }
        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Laboratory_Testing_Percent_Positive.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Laboratory_Testing_Percent_Positive.png");

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("% samples tested PCR Positives")
                .title("Laboratory PCR Tests")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> values = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            labTests.forEach(r -> {
                Date ts = parseTimestamp(r.timestamp);
                if (ts.getTime() >= thirtyDaysAgo.toEpochMilli()) {
                    times.add(ts);
                    values.add(
                            (r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives)) * 100.0
                                    / (
                                    r.totalLabsTotal - (previous[0] == null ? 0 : previous[0].totalLabsTotal)));
                }
                previous[0] = r;
            });
            chart.addSeries("Labs", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }
        chart.getStyler().setXAxisMin(thirtyDaysAgo.toEpochMilli() * 1.0);

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Laboratory_Testing_Percent_Positive_Last30.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Laboratory_Testing_Percent_Positive_Last30.png");

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
         * COVID-19_Number_of_tests_Time_Series_Smoothed
         * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Number of samples tested")
                .title("Laboratory Testing Load (smoothed)")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            Date firstAntigen = parseTimestamp(antigens.get(0).timestamp);
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> values = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            labTests.forEach(r -> {
                Date timestamp = parseTimestamp(r.timestamp);
                //Date timestamp = new Date(parseTimestamp(r.timestamp).toInstant().minus(1, java.time.temporal
                // .ChronoUnit.DAYS).toEpochMilli());
                times.add(timestamp);
                long pcrTests = r.totalLabsTotal - (previous[0] == null ? 0 : previous[0].totalLabsTotal);
                values.add(pcrTests);
                previous[0] = r;
            });
            chart.addSeries("Number of PCR tests", times, gaussianSmooth(values, 3))
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }
        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Number_of_tests_Time_Series_Smoothed.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Number_of_tests_Time_Series_Smoothed.png");

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Number of samples tested (smoothed)")
                .title("Laboratory Testing Load")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            Date firstAntigen = parseTimestamp(antigens.get(0).timestamp);
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> values = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            labTests.forEach(r -> {
                Date timestamp = parseTimestamp(r.timestamp);
                //Date timestamp = new Date(parseTimestamp(r.timestamp).toInstant().minus(1, java.time.temporal
                // .ChronoUnit.DAYS).toEpochMilli());
                if (timestamp.getTime() >= ninetyDaysAgo.toEpochMilli()) {
                    times.add(timestamp);
                    long pcrTests = r.totalLabsTotal - (previous[0] == null ? 0 : previous[0].totalLabsTotal);
                    values.add(pcrTests);
                }
                previous[0] = r;
            });
            chart.addSeries("Number of PCR tests", times, gaussianSmooth(values, 3))
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }
        chart.getStyler().setXAxisMin(ninetyDaysAgo.toEpochMilli() * 1.0);

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Number_of_tests_Time_Series_Smoothed_Last90.png",
                BitmapEncoder.BitmapFormat.PNG);
        System.err.println("./graphs/COVID-19_Number_of_tests_Time_Series_Smoothed_Last90.png");

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
         * COVID-19_Labs_Hospitalized_ICU
         * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

        try (ImageOutputStream output = new FileImageOutputStream(
                new File("./graphs/COVID-19_Labs_Hospitalized_ICU.gif"))) {
            GifSequenceWriter writer = null;
            try {
                for (double sigma = 0.0; sigma <= 20.0; sigma += 0.5) {
                    chart = new XYChartBuilder()
                            .width(1200)
                            .height(675)
                            .theme(Styler.ChartTheme.Matlab)
                            .xAxisTitle("Date")
                            .yAxisTitle("Relative to Jan 2021 wave maximum")
                            .title(String.format(
                                    "Comparisons after applying Gaussian weighted smoothing (sigma=%.1f days)",
                                    sigma))
                            .build();
                    chart.getStyler().setDatePattern("dd-MMM-yyyy");
                    chart.getStyler().setYAxisMax(4.0);
                    chart.getStyler().setYAxisMin(0.0);
//                    chart.getStyler().setXAxisMin(
//                            (double) (OffsetDateTime.of(2020, 12, 15, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
//                                    .toEpochMilli()));
//                    chart.getStyler().setXAxisMax(
//                            (double) (OffsetDateTime.of(2021, 2, 15, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
//                                    .toEpochMilli()));

                    {
                        List<Date> times = new ArrayList<>(labTests.size());
                        List<Number> values = new ArrayList<>(labTests.size());
                        LabTests[] previous = new LabTests[1];
                        labTests.forEach(r -> {
                            times.add(parseTimestamp(r.timestamp));
                            values.add((r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives)) * 4.0
                                    / (
                                    r.totalLabsTotal - (previous[0] == null ? 0 : previous[0].totalLabsTotal)));
                            previous[0] = r;
                        });
                        chart.addSeries("% Labs Positive", times, gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

                    {
                        Date firstAntigen = parseTimestamp(antigens.get(0).timestamp);
                        List<Date> times = new ArrayList<>(labTests.size());
                        List<Number> values = new ArrayList<>(labTests.size());
                        LabTests[] previous = new LabTests[1];
                        Long max = labTests.stream().filter(r -> r.timestamp.compareTo("2021/04") < 0).map(r -> {
                            long delta = previous[0] == null
                                    ? r.totalPositives
                                    : r.totalPositives - previous[0].totalPositives;
                            previous[0] = r;
                            return delta;
                        }).max(Long::compareTo).orElse(1L);
                        previous[0] = null;
                        Double[] antigenRatio = new Double[1];
                        labTests.forEach(r -> {
                            Date timestamp = parseTimestamp(r.timestamp);
                            times.add(timestamp);
                            long pcrPositives =
                                    r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives);
                            Long antigenPositives = antigens.stream()
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
                                        }
                                        return p;
                                    })
                                    .orElseGet(() -> {
                                        if (guessAntigen && firstAntigen.getTime() < timestamp.getTime()) {
                                            return trendGuessing
                                                    ? Math.round(antigenRatio[0] * pcrPositives)
                                                    : Math.round(pcrPositives / FRACTION_OVER_40_YEARS_OLD
                                                            * FRACTION_UNDER_40_YEARS_OLD);
                                        } else {
                                            return 0L;
                                        }
                                    });
                            double value = pcrPositives + antigenPositives;
                            values.add(value / max);

                            previous[0] = r;
                        });
                        chart.addSeries("Labs and Antigen Positives", times,
                                        gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

                    {
                        List<Date> times = new ArrayList<>(acuteHospitals.size());
                        List<Number> values = new ArrayList<>(acuteHospitals.size());
                        Long max = acuteHospitals.stream().filter(r -> r.admissionsCovidPositive != null)
                                .map(r -> r.admissionsCovidPositive).max(Long::compareTo).orElse(1L);
                        acuteHospitals.forEach(r -> {
                            times.add(new Date(parseTimestamp(r.timestamp).getTime()));
                            values.add(
                                    r.admissionsCovidPositive == null ? null : r.admissionsCovidPositive * 1.0 / max);
                        });
                        chart.addSeries("New Covid admissions to Hospital", times,
                                        gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }


                    {
                        List<Date> times = new ArrayList<>(icuHospitals.size());
                        List<Number> values = new ArrayList<>(icuHospitals.size());
                        Long max = icuHospitals.stream().filter(r -> r.admissionsCovidPositive != null)
                                .map(r -> r.admissionsCovidPositive).max(Long::compareTo).orElse(1L);
                        icuHospitals.forEach(r -> {
                            times.add(new Date(parseTimestamp(r.timestamp).getTime()));
                            values.add(
                                    r.admissionsCovidPositive == null ? null : r.admissionsCovidPositive * 1.0 / max);
                        });
                        chart.addSeries("New Covid admissions to ICU", times,
                                        gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

//                    {
//                        List<Date> times = new ArrayList<>(acuteHospitals.size());
//                        List<Number> values = new ArrayList<>(acuteHospitals.size());
//                        Long max = acuteHospitals.stream().filter(r -> r.admissionsCovidPositive != null && r
//                        .newCovidCasesCovid  != null)
//                                .map(r -> r.newCovidCasesCovid - r.admissionsCovidPositive).max(Long::compareTo)
//                                .orElse(1L);
//                        acuteHospitals.forEach(r -> {
//                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(9)));
//                            values.add(
//                                    r.admissionsCovidPositive == null || r.newCovidCasesCovid == null ? null : (r
//                                    .newCovidCasesCovid - r.admissionsCovidPositive) * 1.0 / max);
//                        });
//                        chart.addSeries("New Covid detected in Hospital (9 days later)", times,
//                                        gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }
//
//                    {
//                        List<Date> times = new ArrayList<>(acuteHospitals.size());
//                        List<Number> values = new ArrayList<>(acuteHospitals.size());
//                        Long max = acuteHospitals.stream().filter(r -> r.dischargesCovidPositive != null)
//                                .map(r -> r.dischargesCovidPositive).max(Long::compareTo).orElse(1L);
//                        acuteHospitals.forEach(r -> {
//                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(17)));
//                            values.add(
//                                    r.dischargesCovidPositive == null ? null : r.dischargesCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("Covid positive discharges from Hospital (17 days later)", times,
//                                        gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }
//
//                    {
//                        List<Date> times = new ArrayList<>(icuHospitals.size());
//                        List<Number> values = new ArrayList<>(icuHospitals.size());
//                        Long max = icuHospitals.stream().filter(r -> r.dischargesCovidPositive != null)
//                                .map(r -> r.dischargesCovidPositive).max(Long::compareTo).orElse(1L);
//                        icuHospitals.forEach(r -> {
//                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(17)));
//                            values.add(
//                                    r.dischargesCovidPositive == null ? null : r.dischargesCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("ICU discharges (17 days later)", times,
//                                        gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }

                    //                    {
//                        List<Date> times = new ArrayList<>(acuteHospitals.size());
//                        List<Number> values = new ArrayList<>(acuteHospitals.size());
//                        Long max = acuteHospitals.stream().filter(r -> r.currentConfirmedCovidPositive != null)
//                                .map(r -> r.currentConfirmedCovidPositive).max(Long::compareTo).orElse(1L);
//                        acuteHospitals.forEach(r -> {
//                            times.add(parseTimestamp(r.timestamp));
//                            values.add(r.currentConfirmedCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("Hospital Occuopancy", times, gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }

//                    {
//                        List<Date> times = new ArrayList<>(icuHospitals.size());
//                        List<Number> values = new ArrayList<>(icuHospitals.size());
//                        Long max = icuHospitals.stream().filter(r -> r.currentConfirmedCovidPositive != null)
//                                .map(r -> r.currentConfirmedCovidPositive).max(Long::compareTo).orElse(1L);
//                        icuHospitals.forEach(r -> {
//                            times.add(parseTimestamp(r.timestamp));
//                            values.add(r.currentConfirmedCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("ICU Occupancy", times, gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }

                    {
                        List<Date> times = new ArrayList<>(icuHospitals.size());
                        List<Number> values = new ArrayList<>(icuHospitals.size());
                        Long max = stats.stream().filter(r -> r.deathsTodayDOD != null)
                                .map(r -> r.deathsTodayDOD).max(Long::compareTo).orElse(1L);
                        stats.forEach(r -> {
                            times.add(new Date(parseTimestamp(r.timestamp).getTime()));
                            values.add(r.deathsTodayDOD == null ? null : r.deathsTodayDOD * 1.0 / max);
                        });
                        chart.addSeries("Deaths", times,
                                        gaussianSmooth(replaceNulls(values, 0), Math.max(sigma, 0)))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

                    BufferedImage image = BitmapEncoder.getBufferedImage(chart);
                    if (writer == null) {
                        writer = new GifSequenceWriter(output, image.getType(), 750, true);
                    }
                    writer.writeToSequence(image);
                    if (Math.abs(sigma - 3) < 0.01) {
                        BitmapEncoder.saveBitmap(chart,
                                String.format("./graphs/COVID-19_Labs_Hospitalized_ICU-%04.1f.png", sigma),
                                BitmapEncoder.BitmapFormat.PNG);
                    }
                }
            } finally {
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
            }
        }
        System.err.println("./graphs/COVID-19_Labs_Hospitalized_ICU.gif");

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
         * COVID-19_Labs_Hospitalized_ICU_Last90
         * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

        try (ImageOutputStream output = new FileImageOutputStream(
                new File("./graphs/COVID-19_Labs_Hospitalized_ICU_Last90.gif"))) {
            GifSequenceWriter writer = null;
            try {
                for (double sigma = 0.0; sigma <= 20.0; sigma += 0.5) {
                    chart = new XYChartBuilder()
                            .width(1200)
                            .height(675)
                            .theme(Styler.ChartTheme.Matlab)
                            .xAxisTitle("Date")
                            .yAxisTitle("Relative to Jan 2021 wave maximum")
                            .title(String.format(
                                    "Comparisons after applying Gaussian weighted smoothing (sigma=%.1f days)",
                                    sigma))
                            .build();
                    chart.getStyler().setDatePattern("dd-MMM-yyyy");
                    chart.getStyler().setXAxisMin(ninetyDaysAgo.toEpochMilli() * 1.0);
                    chart.getStyler().setYAxisMax(2.0);
                    chart.getStyler().setYAxisMin(0.0);
//                    chart.getStyler().setXAxisMin(
//                            (double) (OffsetDateTime.of(2020, 12, 15, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
//                                    .toEpochMilli()));
//                    chart.getStyler().setXAxisMax(
//                            (double) (OffsetDateTime.of(2021, 2, 15, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
//                                    .toEpochMilli()));

                    {
                        List<Date> times = new ArrayList<>(labTests.size());
                        List<Number> values = new ArrayList<>(labTests.size());
                        LabTests[] previous = new LabTests[1];
                        labTests.forEach(r -> {
                            times.add(parseTimestamp(r.timestamp));
                            values.add((r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives)) * 4.0
                                    / (
                                    r.totalLabsTotal - (previous[0] == null ? 0 : previous[0].totalLabsTotal)));
                            previous[0] = r;
                        });
                        chart.addSeries("% Labs Positive", times, gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

                    {
                        Date firstAntigen = parseTimestamp(antigens.get(0).timestamp);
                        List<Date> times = new ArrayList<>(labTests.size());
                        List<Number> values = new ArrayList<>(labTests.size());
                        LabTests[] previous = new LabTests[1];
                        Long max = labTests.stream().filter(r -> r.timestamp.compareTo("2021/04") < 0).map(r -> {
                            long delta = previous[0] == null
                                    ? r.totalPositives
                                    : r.totalPositives - previous[0].totalPositives;
                            previous[0] = r;
                            return delta;
                        }).max(Long::compareTo).orElse(1L);
                        previous[0] = null;
                        Double[] antigenRatio = new Double[1];
                        labTests.forEach(r -> {
                            Date timestamp = parseTimestamp(r.timestamp);
                            times.add(timestamp);
                            long pcrPositives =
                                    r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives);
                            Long antigenPositives = antigens.stream()
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
                                        }
                                        return p;
                                    })
                                    .orElseGet(() -> {
                                        if (guessAntigen && firstAntigen.getTime() < timestamp.getTime()) {
                                            return trendGuessing
                                                    ? Math.round(antigenRatio[0] * pcrPositives)
                                                    : Math.round(pcrPositives / FRACTION_OVER_40_YEARS_OLD
                                                            * FRACTION_UNDER_40_YEARS_OLD);
                                        } else {
                                            return 0L;
                                        }
                                    });
                            double value = pcrPositives + antigenPositives;
                            values.add(value / max);

                            previous[0] = r;
                        });
                        chart.addSeries("Labs and Antigen Positives", times,
                                        gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

                    {
                        List<Date> times = new ArrayList<>(acuteHospitals.size());
                        List<Number> values = new ArrayList<>(acuteHospitals.size());
                        Long max = acuteHospitals.stream().filter(r -> r.admissionsCovidPositive != null)
                                .map(r -> r.admissionsCovidPositive).max(Long::compareTo).orElse(1L);
                        acuteHospitals.forEach(r -> {
                            times.add(new Date(parseTimestamp(r.timestamp).getTime()));
                            values.add(
                                    r.admissionsCovidPositive == null ? null : r.admissionsCovidPositive * 1.0 / max);
                        });
                        chart.addSeries("New Covid admissions to Hospital", times,
                                        gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }


                    {
                        List<Date> times = new ArrayList<>(icuHospitals.size());
                        List<Number> values = new ArrayList<>(icuHospitals.size());
                        Long max = icuHospitals.stream().filter(r -> r.admissionsCovidPositive != null)
                                .map(r -> r.admissionsCovidPositive).max(Long::compareTo).orElse(1L);
                        icuHospitals.forEach(r -> {
                            times.add(new Date(parseTimestamp(r.timestamp).getTime()));
                            values.add(
                                    r.admissionsCovidPositive == null ? null : r.admissionsCovidPositive * 1.0 / max);
                        });
                        chart.addSeries("New Covid admissions to ICU", times,
                                        gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

//                    {
//                        List<Date> times = new ArrayList<>(acuteHospitals.size());
//                        List<Number> values = new ArrayList<>(acuteHospitals.size());
//                        Long max = acuteHospitals.stream().filter(r -> r.admissionsCovidPositive != null && r
//                        .newCovidCasesCovid  != null)
//                                .map(r -> r.newCovidCasesCovid - r.admissionsCovidPositive).max(Long::compareTo)
//                                .orElse(1L);
//                        acuteHospitals.forEach(r -> {
//                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(9)));
//                            values.add(
//                                    r.admissionsCovidPositive == null || r.newCovidCasesCovid == null ? null : (r
//                                    .newCovidCasesCovid - r.admissionsCovidPositive) * 1.0 / max);
//                        });
//                        chart.addSeries("New Covid detected in Hospital (9 days later)", times,
//                                        gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }
//
//                    {
//                        List<Date> times = new ArrayList<>(acuteHospitals.size());
//                        List<Number> values = new ArrayList<>(acuteHospitals.size());
//                        Long max = acuteHospitals.stream().filter(r -> r.dischargesCovidPositive != null)
//                                .map(r -> r.dischargesCovidPositive).max(Long::compareTo).orElse(1L);
//                        acuteHospitals.forEach(r -> {
//                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(17)));
//                            values.add(
//                                    r.dischargesCovidPositive == null ? null : r.dischargesCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("Covid positive discharges from Hospital (17 days later)", times,
//                                        gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }
//
//                    {
//                        List<Date> times = new ArrayList<>(icuHospitals.size());
//                        List<Number> values = new ArrayList<>(icuHospitals.size());
//                        Long max = icuHospitals.stream().filter(r -> r.dischargesCovidPositive != null)
//                                .map(r -> r.dischargesCovidPositive).max(Long::compareTo).orElse(1L);
//                        icuHospitals.forEach(r -> {
//                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(17)));
//                            values.add(
//                                    r.dischargesCovidPositive == null ? null : r.dischargesCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("ICU discharges (17 days later)", times,
//                                        gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }

                    //                    {
//                        List<Date> times = new ArrayList<>(acuteHospitals.size());
//                        List<Number> values = new ArrayList<>(acuteHospitals.size());
//                        Long max = acuteHospitals.stream().filter(r -> r.currentConfirmedCovidPositive != null)
//                                .map(r -> r.currentConfirmedCovidPositive).max(Long::compareTo).orElse(1L);
//                        acuteHospitals.forEach(r -> {
//                            times.add(parseTimestamp(r.timestamp));
//                            values.add(r.currentConfirmedCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("Hospital Occuopancy", times, gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }

//                    {
//                        List<Date> times = new ArrayList<>(icuHospitals.size());
//                        List<Number> values = new ArrayList<>(icuHospitals.size());
//                        Long max = icuHospitals.stream().filter(r -> r.currentConfirmedCovidPositive != null)
//                                .map(r -> r.currentConfirmedCovidPositive).max(Long::compareTo).orElse(1L);
//                        icuHospitals.forEach(r -> {
//                            times.add(parseTimestamp(r.timestamp));
//                            values.add(r.currentConfirmedCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("ICU Occupancy", times, gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }

                    {
                        List<Date> times = new ArrayList<>(icuHospitals.size());
                        List<Number> values = new ArrayList<>(icuHospitals.size());
                        Long max = stats.stream().filter(r -> r.deathsTodayDOD != null)
                                .map(r -> r.deathsTodayDOD).max(Long::compareTo).orElse(1L);
                        stats.forEach(r -> {
                            times.add(new Date(parseTimestamp(r.timestamp).getTime()));
                            values.add(r.deathsTodayDOD == null ? null : r.deathsTodayDOD * 1.0 / max);
                        });
                        chart.addSeries("Deaths", times,
                                        gaussianSmooth(replaceNulls(values, 0), Math.max(sigma, 0)))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

                    BufferedImage image = BitmapEncoder.getBufferedImage(chart);
                    if (writer == null) {
                        writer = new GifSequenceWriter(output, image.getType(), 750, true);
                    }
                    writer.writeToSequence(image);
                    if (Math.abs(sigma - 3) < 0.01) {
                        BitmapEncoder.saveBitmap(chart,
                                String.format("./graphs/COVID-19_Labs_Hospitalized_ICU_Last90-%04.1f.png", sigma),
                                BitmapEncoder.BitmapFormat.PNG);
                    }
                }
            } finally {
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
            }
        }
        System.err.println("./graphs/COVID-19_Labs_Hospitalized_ICU_Last90.gif");

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
         * COVID-19_Labs_Hospitalized_ICU_Timeshifted
         * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

        try (ImageOutputStream output = new FileImageOutputStream(
                new File("./graphs/COVID-19_Labs_Hospitalized_ICU_Timeshifted.gif"))) {
            GifSequenceWriter writer = null;
            try {
                for (double sigma = 0.0; sigma <= 20.0; sigma += 0.5) {
                    chart = new XYChartBuilder()
                            .width(1200)
                            .height(675)
                            .theme(Styler.ChartTheme.Matlab)
                            .xAxisTitle("Date")
                            .yAxisTitle("Relative to Jan 2021 wave maximum")
                            .title(String.format(
                                    "Comparisons after applying Gaussian weighted smoothing (sigma=%.1f days)",
                                    sigma))
                            .build();
                    chart.getStyler().setDatePattern("dd-MMM-yyyy");
                    chart.getStyler().setYAxisMax(4.0);
                    chart.getStyler().setYAxisMin(0.0);
//                    chart.getStyler().setXAxisMin(
//                            (double) (OffsetDateTime.of(2020, 12, 15, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
//                                    .toEpochMilli()));
//                    chart.getStyler().setXAxisMax(
//                            (double) (OffsetDateTime.of(2021, 2, 15, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
//                                    .toEpochMilli()));

                    {
                        List<Date> times = new ArrayList<>(labTests.size());
                        List<Number> values = new ArrayList<>(labTests.size());
                        LabTests[] previous = new LabTests[1];
                        labTests.forEach(r -> {
                            times.add(parseTimestamp(r.timestamp));
                            values.add((r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives)) * 4.0
                                    / (
                                    r.totalLabsTotal - (previous[0] == null ? 0 : previous[0].totalLabsTotal)));
                            previous[0] = r;
                        });
                        chart.addSeries("% Labs Positive", times, gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

                    {
                        Date firstAntigen = parseTimestamp(antigens.get(0).timestamp);
                        List<Date> times = new ArrayList<>(labTests.size());
                        List<Number> values = new ArrayList<>(labTests.size());
                        LabTests[] previous = new LabTests[1];
                        Long max = labTests.stream().filter(r -> r.timestamp.compareTo("2021/04") < 0).map(r -> {
                            long delta = previous[0] == null
                                    ? r.totalPositives
                                    : r.totalPositives - previous[0].totalPositives;
                            previous[0] = r;
                            return delta;
                        }).max(Long::compareTo).orElse(1L);
                        previous[0] = null;
                        Double[] antigenRatio = new Double[1];
                        labTests.forEach(r -> {
                            Date timestamp = parseTimestamp(r.timestamp);
                            times.add(timestamp);
                            long pcrPositives =
                                    r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives);
                            Long antigenPositives = antigens.stream()
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
                                        }
                                        return p;
                                    })
                                    .orElseGet(() -> {
                                        if (guessAntigen && firstAntigen.getTime() < timestamp.getTime()) {
                                            return trendGuessing
                                                    ? Math.round(antigenRatio[0] * pcrPositives)
                                                    : Math.round(pcrPositives / FRACTION_OVER_40_YEARS_OLD
                                                            * FRACTION_UNDER_40_YEARS_OLD);
                                        } else {
                                            return 0L;
                                        }
                                    });
                            double value = pcrPositives + antigenPositives;
                            values.add(value / max);

                            previous[0] = r;
                        });
                        chart.addSeries("Labs and Antigen Positives", times,
                                        gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

                    {
                        List<Date> times = new ArrayList<>(acuteHospitals.size());
                        List<Number> values = new ArrayList<>(acuteHospitals.size());
                        Long max = acuteHospitals.stream().filter(r -> r.admissionsCovidPositive != null)
                                .map(r -> r.admissionsCovidPositive).max(Long::compareTo).orElse(1L);
                        acuteHospitals.forEach(r -> {
                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(10)));
                            values.add(
                                    r.admissionsCovidPositive == null ? null : r.admissionsCovidPositive * 1.0 / max);
                        });
                        chart.addSeries("New Covid admissions to Hospital\n(10 days later)", times,
                                        gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }


                    {
                        List<Date> times = new ArrayList<>(icuHospitals.size());
                        List<Number> values = new ArrayList<>(icuHospitals.size());
                        Long max = icuHospitals.stream().filter(r -> r.admissionsCovidPositive != null)
                                .map(r -> r.admissionsCovidPositive).max(Long::compareTo).orElse(1L);
                        icuHospitals.forEach(r -> {
                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(8)));
                            values.add(
                                    r.admissionsCovidPositive == null ? null : r.admissionsCovidPositive * 1.0 / max);
                        });
                        chart.addSeries("New Covid admissions to ICU\n(8 days later)", times,
                                        gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

//                    {
//                        List<Date> times = new ArrayList<>(acuteHospitals.size());
//                        List<Number> values = new ArrayList<>(acuteHospitals.size());
//                        Long max = acuteHospitals.stream().filter(r -> r.admissionsCovidPositive != null && r
//                        .newCovidCasesCovid  != null)
//                                .map(r -> r.newCovidCasesCovid - r.admissionsCovidPositive).max(Long::compareTo)
//                                .orElse(1L);
//                        acuteHospitals.forEach(r -> {
//                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(9)));
//                            values.add(
//                                    r.admissionsCovidPositive == null || r.newCovidCasesCovid == null ? null : (r
//                                    .newCovidCasesCovid - r.admissionsCovidPositive) * 1.0 / max);
//                        });
//                        chart.addSeries("New Covid detected in Hospital\n(9 days later)", times,
//                                        gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }
//
//                    {
//                        List<Date> times = new ArrayList<>(acuteHospitals.size());
//                        List<Number> values = new ArrayList<>(acuteHospitals.size());
//                        Long max = acuteHospitals.stream().filter(r -> r.dischargesCovidPositive != null)
//                                .map(r -> r.dischargesCovidPositive).max(Long::compareTo).orElse(1L);
//                        acuteHospitals.forEach(r -> {
//                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(17)));
//                            values.add(
//                                    r.dischargesCovidPositive == null ? null : r.dischargesCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("Covid positive discharges from Hospital (17 days later)", times,
//                                        gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }
//
//                    {
//                        List<Date> times = new ArrayList<>(icuHospitals.size());
//                        List<Number> values = new ArrayList<>(icuHospitals.size());
//                        Long max = icuHospitals.stream().filter(r -> r.dischargesCovidPositive != null)
//                                .map(r -> r.dischargesCovidPositive).max(Long::compareTo).orElse(1L);
//                        icuHospitals.forEach(r -> {
//                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(17)));
//                            values.add(
//                                    r.dischargesCovidPositive == null ? null : r.dischargesCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("ICU discharges (17 days later)", times,
//                                        gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }

                    //                    {
//                        List<Date> times = new ArrayList<>(acuteHospitals.size());
//                        List<Number> values = new ArrayList<>(acuteHospitals.size());
//                        Long max = acuteHospitals.stream().filter(r -> r.currentConfirmedCovidPositive != null)
//                                .map(r -> r.currentConfirmedCovidPositive).max(Long::compareTo).orElse(1L);
//                        acuteHospitals.forEach(r -> {
//                            times.add(parseTimestamp(r.timestamp));
//                            values.add(r.currentConfirmedCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("Hospital Occuopancy", times, gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }

//                    {
//                        List<Date> times = new ArrayList<>(icuHospitals.size());
//                        List<Number> values = new ArrayList<>(icuHospitals.size());
//                        Long max = icuHospitals.stream().filter(r -> r.currentConfirmedCovidPositive != null)
//                                .map(r -> r.currentConfirmedCovidPositive).max(Long::compareTo).orElse(1L);
//                        icuHospitals.forEach(r -> {
//                            times.add(parseTimestamp(r.timestamp));
//                            values.add(r.currentConfirmedCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("ICU Occupancy", times, gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }

                    {
                        List<Date> times = new ArrayList<>(icuHospitals.size());
                        List<Number> values = new ArrayList<>(icuHospitals.size());
                        Long max = stats.stream().filter(r -> r.deathsTodayDOD != null)
                                .map(r -> r.deathsTodayDOD).max(Long::compareTo).orElse(1L);
                        stats.forEach(r -> {
                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(20)));
                            values.add(r.deathsTodayDOD == null ? null : r.deathsTodayDOD * 1.0 / max);
                        });
                        chart.addSeries("Deaths\n(20 days later)", times,
                                        gaussianSmooth(replaceNulls(values, 0), Math.max(sigma, 0)))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

                    BufferedImage image = BitmapEncoder.getBufferedImage(chart);
                    if (writer == null) {
                        writer = new GifSequenceWriter(output, image.getType(), 750, true);
                    }
                    writer.writeToSequence(image);
                    if (Math.abs(sigma - 3) < 0.01) {
                        BitmapEncoder.saveBitmap(chart,
                                String.format("./graphs/COVID-19_Labs_Hospitalized_ICU_Timeshifted-%04.1f.png", sigma),
                                BitmapEncoder.BitmapFormat.PNG);
                    }
                }
            } finally {
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
            }
        }
        System.err.println("./graphs/COVID-19_Labs_Hospitalized_ICU_Timeshifted.gif");

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
         * COVID-19_Labs_Hospitalized_ICU_Timeshifted_Last90
         * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

        try (ImageOutputStream output = new FileImageOutputStream(
                new File("./graphs/COVID-19_Labs_Hospitalized_ICU_Timeshifted_Last90.gif"))) {
            GifSequenceWriter writer = null;
            try {
                for (double sigma = 0.0; sigma <= 20.0; sigma += 0.5) {
                    chart = new XYChartBuilder()
                            .width(1200)
                            .height(675)
                            .theme(Styler.ChartTheme.Matlab)
                            .xAxisTitle("Date")
                            .yAxisTitle("Relative to Jan 2021 wave maximum")
                            .title(String.format(
                                    "Comparisons after applying Gaussian weighted smoothing (sigma=%.1f days)",
                                    sigma))
                            .build();
                    chart.getStyler().setDatePattern("dd-MMM-yyyy");
                    chart.getStyler().setXAxisMin(ninetyDaysAgo.toEpochMilli() * 1.0);
                    chart.getStyler().setYAxisMax(2.0);
                    chart.getStyler().setYAxisMin(0.0);
//                    chart.getStyler().setXAxisMin(
//                            (double) (OffsetDateTime.of(2020, 12, 15, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
//                                    .toEpochMilli()));
//                    chart.getStyler().setXAxisMax(
//                            (double) (OffsetDateTime.of(2021, 2, 15, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
//                                    .toEpochMilli()));

                    {
                        List<Date> times = new ArrayList<>(labTests.size());
                        List<Number> values = new ArrayList<>(labTests.size());
                        LabTests[] previous = new LabTests[1];
                        labTests.forEach(r -> {
                            times.add(parseTimestamp(r.timestamp));
                            values.add((r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives)) * 4.0
                                    / (
                                    r.totalLabsTotal - (previous[0] == null ? 0 : previous[0].totalLabsTotal)));
                            previous[0] = r;
                        });
                        chart.addSeries("% Labs Positive", times, gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

                    {
                        Date firstAntigen = parseTimestamp(antigens.get(0).timestamp);
                        List<Date> times = new ArrayList<>(labTests.size());
                        List<Number> values = new ArrayList<>(labTests.size());
                        LabTests[] previous = new LabTests[1];
                        Long max = labTests.stream().filter(r -> r.timestamp.compareTo("2021/04") < 0).map(r -> {
                            long delta = previous[0] == null
                                    ? r.totalPositives
                                    : r.totalPositives - previous[0].totalPositives;
                            previous[0] = r;
                            return delta;
                        }).max(Long::compareTo).orElse(1L);
                        previous[0] = null;
                        Double[] antigenRatio = new Double[1];
                        labTests.forEach(r -> {
                            Date timestamp = parseTimestamp(r.timestamp);
                            times.add(timestamp);
                            long pcrPositives =
                                    r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives);
                            Long antigenPositives = antigens.stream()
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
                                        }
                                        return p;
                                    })
                                    .orElseGet(() -> {
                                        if (guessAntigen && firstAntigen.getTime() < timestamp.getTime()) {
                                            return trendGuessing
                                                    ? Math.round(antigenRatio[0] * pcrPositives)
                                                    : Math.round(pcrPositives / FRACTION_OVER_40_YEARS_OLD
                                                            * FRACTION_UNDER_40_YEARS_OLD);
                                        } else {
                                            return 0L;
                                        }
                                    });
                            double value = pcrPositives + antigenPositives;
                            values.add(value / max);

                            previous[0] = r;
                        });
                        chart.addSeries("Labs and Antigen Positives", times,
                                        gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

                    {
                        List<Date> times = new ArrayList<>(acuteHospitals.size());
                        List<Number> values = new ArrayList<>(acuteHospitals.size());
                        Long max = acuteHospitals.stream().filter(r -> r.admissionsCovidPositive != null)
                                .map(r -> r.admissionsCovidPositive).max(Long::compareTo).orElse(1L);
                        acuteHospitals.forEach(r -> {
                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(10)));
                            values.add(
                                    r.admissionsCovidPositive == null ? null : r.admissionsCovidPositive * 1.0 / max);
                        });
                        chart.addSeries("New Covid admissions to Hospital\n(10 days later)", times,
                                        gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }


                    {
                        List<Date> times = new ArrayList<>(icuHospitals.size());
                        List<Number> values = new ArrayList<>(icuHospitals.size());
                        Long max = icuHospitals.stream().filter(r -> r.admissionsCovidPositive != null)
                                .map(r -> r.admissionsCovidPositive).max(Long::compareTo).orElse(1L);
                        icuHospitals.forEach(r -> {
                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(8)));
                            values.add(
                                    r.admissionsCovidPositive == null ? null : r.admissionsCovidPositive * 1.0 / max);
                        });
                        chart.addSeries("New Covid admissions to ICU\n(8 days later)", times,
                                        gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

//                    {
//                        List<Date> times = new ArrayList<>(acuteHospitals.size());
//                        List<Number> values = new ArrayList<>(acuteHospitals.size());
//                        Long max = acuteHospitals.stream().filter(r -> r.admissionsCovidPositive != null && r
//                        .newCovidCasesCovid  != null)
//                                .map(r -> r.newCovidCasesCovid - r.admissionsCovidPositive).max(Long::compareTo)
//                                .orElse(1L);
//                        acuteHospitals.forEach(r -> {
//                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(9)));
//                            values.add(
//                                    r.admissionsCovidPositive == null || r.newCovidCasesCovid == null ? null : (r
//                                    .newCovidCasesCovid - r.admissionsCovidPositive) * 1.0 / max);
//                        });
//                        chart.addSeries("New Covid detected in Hospital\n(9 days later)", times,
//                                        gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }
//
//                    {
//                        List<Date> times = new ArrayList<>(acuteHospitals.size());
//                        List<Number> values = new ArrayList<>(acuteHospitals.size());
//                        Long max = acuteHospitals.stream().filter(r -> r.dischargesCovidPositive != null)
//                                .map(r -> r.dischargesCovidPositive).max(Long::compareTo).orElse(1L);
//                        acuteHospitals.forEach(r -> {
//                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(17)));
//                            values.add(
//                                    r.dischargesCovidPositive == null ? null : r.dischargesCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("Covid positive discharges from Hospital (17 days later)", times,
//                                        gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }
//
//                    {
//                        List<Date> times = new ArrayList<>(icuHospitals.size());
//                        List<Number> values = new ArrayList<>(icuHospitals.size());
//                        Long max = icuHospitals.stream().filter(r -> r.dischargesCovidPositive != null)
//                                .map(r -> r.dischargesCovidPositive).max(Long::compareTo).orElse(1L);
//                        icuHospitals.forEach(r -> {
//                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(17)));
//                            values.add(
//                                    r.dischargesCovidPositive == null ? null : r.dischargesCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("ICU discharges (17 days later)", times,
//                                        gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }

                    //                    {
//                        List<Date> times = new ArrayList<>(acuteHospitals.size());
//                        List<Number> values = new ArrayList<>(acuteHospitals.size());
//                        Long max = acuteHospitals.stream().filter(r -> r.currentConfirmedCovidPositive != null)
//                                .map(r -> r.currentConfirmedCovidPositive).max(Long::compareTo).orElse(1L);
//                        acuteHospitals.forEach(r -> {
//                            times.add(parseTimestamp(r.timestamp));
//                            values.add(r.currentConfirmedCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("Hospital Occuopancy", times, gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }

//                    {
//                        List<Date> times = new ArrayList<>(icuHospitals.size());
//                        List<Number> values = new ArrayList<>(icuHospitals.size());
//                        Long max = icuHospitals.stream().filter(r -> r.currentConfirmedCovidPositive != null)
//                                .map(r -> r.currentConfirmedCovidPositive).max(Long::compareTo).orElse(1L);
//                        icuHospitals.forEach(r -> {
//                            times.add(parseTimestamp(r.timestamp));
//                            values.add(r.currentConfirmedCovidPositive * 1.0 / max);
//                        });
//                        chart.addSeries("ICU Occupancy", times, gaussianSmooth(replaceNulls(values, 0), sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE);
//                    }

                    {
                        List<Date> times = new ArrayList<>(icuHospitals.size());
                        List<Number> values = new ArrayList<>(icuHospitals.size());
                        Long max = stats.stream().filter(r -> r.deathsTodayDOD != null)
                                .map(r -> r.deathsTodayDOD).max(Long::compareTo).orElse(1L);
                        stats.forEach(r -> {
                            times.add(new Date(parseTimestamp(r.timestamp).getTime() - TimeUnit.DAYS.toMillis(20)));
                            values.add(r.deathsTodayDOD == null ? null : r.deathsTodayDOD * 1.0 / max);
                        });
                        chart.addSeries("Deaths\n(20 days later)", times,
                                        gaussianSmooth(replaceNulls(values, 0), Math.max(sigma, 0)))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

                    BufferedImage image = BitmapEncoder.getBufferedImage(chart);
                    if (writer == null) {
                        writer = new GifSequenceWriter(output, image.getType(), 750, true);
                    }
                    writer.writeToSequence(image);
                    if (Math.abs(sigma - 3) < 0.01) {
                        BitmapEncoder.saveBitmap(chart,
                                String.format("./graphs/COVID-19_Labs_Hospitalized_ICU_Timeshifted_Last90-%04.1f.png", sigma),
                                BitmapEncoder.BitmapFormat.PNG);
                    }
                }
            } finally {
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
            }
        }
        System.err.println("./graphs/COVID-19_Labs_Hospitalized_ICU_Timeshifted_Last90.gif");

        /* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
         * COVID-19_Labs_This_Year
         * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

        try (ImageOutputStream output = new FileImageOutputStream(
                new File("./graphs/COVID-19_Labs_This_Year.gif"))) {
            GifSequenceWriter writer = null;
            try {
                for (double sigma = 0.0; sigma <= 20.0; sigma += 0.5) {
                    chart = new XYChartBuilder()
                            .width(1200)
                            .height(675)
                            .theme(Styler.ChartTheme.Matlab)
                            .xAxisTitle("Date")
                            .yAxisTitle("Fraction of tests that are positive")
                            .title(String.format(
                                    "Comparisons after applying Gaussian weighted smoothing (sigma=%.1f days)",
                                    sigma))
                            .build();
                    chart.getStyler().setDatePattern("dd-MMM-yyyy");
                    chart.getStyler().setYAxisMax(0.6);
                    chart.getStyler().setYAxisMin(0.0);
                    chart.getStyler().setXAxisMin((double) (GIF_START_DATE.toInstant().toEpochMilli()));
                    chart.getStyler().setXAxisMax((double) (GIF_END_DATE.toInstant().toEpochMilli()));

                    {
                        List<Date> times = new ArrayList<>(labTests.size());
                        List<Date> times2 = new ArrayList<>(labTests.size());
                        List<Number> values = new ArrayList<>(labTests.size());
                        List<Number> values2 = new ArrayList<>(labTests.size());
                        List<Number> values3 = new ArrayList<>(labTests.size());
                        LabTests[] previous = new LabTests[1];
                        labTests.forEach(r -> {
                            Date timestamp = parseTimestamp(r.timestamp);
                            times.add(timestamp);
                            times2.add(new Date(timestamp.getTime() + TimeUnit.DAYS.toMillis(365)));
                            double value =
                                    (r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives)) * 1.0
                                            / (
                                            r.totalLabsTotal - (previous[0] == null ? 0 : previous[0].totalLabsTotal));
                            values.add(value);
                            values2.add(value);
                            values3.add(value * 2.7);
                            previous[0] = r;
                        });
                        chart.addSeries("This year", times, gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }


                    BufferedImage image = BitmapEncoder.getBufferedImage(chart);
                    if (writer == null) {
                        writer = new GifSequenceWriter(output, image.getType(), 750, true);
                    }
                    writer.writeToSequence(image);
                    if (Math.abs(sigma - 3) < 0.01) {
                        BitmapEncoder.saveBitmap(chart,
                                String.format("./graphs/COVID-19_Labs_This_Year-%04.1f.png", sigma),
                                BitmapEncoder.BitmapFormat.PNG);
                    }
                }
            } finally {
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
            }
        }
        System.err.println("./graphs/COVID-19_Labs_This_Year.gif");

        try (ImageOutputStream output = new FileImageOutputStream(
                new File("./graphs/COVID-19_Labs_Vs_Last_Year.gif"))) {
            GifSequenceWriter writer = null;
            try {
                for (double sigma = 0.0; sigma <= 20.0; sigma += 0.5) {
                    chart = new XYChartBuilder()
                            .width(1200)
                            .height(675)
                            .theme(Styler.ChartTheme.Matlab)
                            .xAxisTitle("Date")
                            .yAxisTitle("Fraction of tests that are positive")
                            .title(String.format(
                                    "Comparisons after applying Gaussian weighted smoothing (sigma=%.1f days)",
                                    sigma))
                            .build();
                    chart.getStyler().setDatePattern("dd-MMM-yyyy");
                    chart.getStyler().setYAxisMax(0.6);
                    chart.getStyler().setYAxisMin(0.0);
                    chart.getStyler().setXAxisMin((double) (GIF_START_DATE.toInstant().toEpochMilli()));
                    chart.getStyler().setXAxisMax((double) (GIF_END_DATE.toInstant().toEpochMilli()));

                    {
                        List<Date> times = new ArrayList<>(labTests.size());
                        List<Date> times2 = new ArrayList<>(labTests.size());
                        List<Number> values = new ArrayList<>(labTests.size());
                        List<Number> values2 = new ArrayList<>(labTests.size());
                        List<Number> values3 = new ArrayList<>(labTests.size());
                        LabTests[] previous = new LabTests[1];
                        labTests.forEach(r -> {
                            Date timestamp = parseTimestamp(r.timestamp);
                            times.add(timestamp);
                            times2.add(new Date(timestamp.getTime() + TimeUnit.DAYS.toMillis(365)));
                            double value =
                                    (r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives)) * 1.0
                                            / (
                                            r.totalLabsTotal - (previous[0] == null ? 0 : previous[0].totalLabsTotal));
                            values.add(value);
                            values2.add(value);
                            values3.add(value * 2.7);
                            previous[0] = r;
                        });
                        chart.addSeries("This year", times, gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                        chart.addSeries("Last year", times2, gaussianSmooth(replaceNulls(values2, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                        chart.addSeries("Last year x2.7", times2, gaussianSmooth(replaceNulls(values3, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE)
                                .setLineStyle(SeriesLines.DASH_DOT);
                    }


                    BufferedImage image = BitmapEncoder.getBufferedImage(chart);
                    if (writer == null) {
                        writer = new GifSequenceWriter(output, image.getType(), 750, true);
                    }
                    writer.writeToSequence(image);
                    if (Math.abs(sigma - 3) < 0.01) {
                        BitmapEncoder.saveBitmap(chart,
                                String.format("./graphs/COVID-19_Labs_Vs_Last_Year-%04.1f.png", sigma),
                                BitmapEncoder.BitmapFormat.PNG);
                    }
                }
            } finally {
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
            }
        }
        System.err.println("./graphs/COVID-19_Labs_Vs_Last_Year.gif");

        try (ImageOutputStream output = new FileImageOutputStream(
                new File("./graphs/COVID-19_Positives_Vs_Last_Year.gif"))) {
            GifSequenceWriter writer = null;
            try {
                for (double sigma = 0.0; sigma <= 20.0; sigma += 0.5) {
                    chart = new XYChartBuilder()
                            .width(1200)
                            .height(675)
                            .theme(Styler.ChartTheme.Matlab)
                            .xAxisTitle("Date")
                            .yAxisTitle(guessAntigen ? "Normalized cases (est)" : "Normalized cases")
                            .title(String.format(
                                    "Comparisons after applying Gaussian weighted smoothing (sigma=%.1f days)",
                                    sigma))
                            .build();
                    chart.getStyler().setDatePattern("dd-MMM");
                    chart.getStyler().setXAxisMin((double) (GIF_START_DATE.toInstant().toEpochMilli()));
                    chart.getStyler().setXAxisMax((double) (GIF_END_DATE.toInstant().toEpochMilli()));
                    chart.getStyler().setYAxisMax(30000.0);
                    chart.getStyler().setYAxisMin(-500.0);

                    {
                        Date firstAntigen = parseTimestamp(antigens.get(0).timestamp);
                        List<Date> times = new ArrayList<>(labTests.size());
                        List<Date> times2 = new ArrayList<>(labTests.size());
                        List<Date> times4 = new ArrayList<>(labTests.size());
                        List<Date> times6 = new ArrayList<>(labTests.size());
                        List<Number> values = new ArrayList<>(labTests.size());
                        List<Number> values2 = new ArrayList<>(labTests.size());
                        List<Number> values3 = new ArrayList<>(labTests.size());
                        List<Number> values3a = new ArrayList<>(labTests.size());
                        List<Number> values3p = new ArrayList<>(labTests.size());
                        List<Number> values3p2 = new ArrayList<>(labTests.size());
                        List<Number> values4 = new ArrayList<>(labTests.size());
                        List<Number> values6 = new ArrayList<>(labTests.size());
                        LabTests[] previous = new LabTests[1];
                        Double[] antigenRatio = new Double[1];
                        labTests.forEach(r -> {
                            Date timestamp = parseTimestamp(r.timestamp);
                            times.add(timestamp);
                            times2.add(new Date(timestamp.getTime() + TimeUnit.DAYS.toMillis(365)));
                            times4.add(new Date(timestamp.getTime() - TimeUnit.DAYS.toMillis(76)));
                            times6.add(new Date(timestamp.getTime() + TimeUnit.DAYS.toMillis(365 + 365)));
                            long pcrPositives =
                                    r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives);
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
                                        }
                                        return p;
                                    })
                                    .orElseGet(() -> {
                                        if (guessAntigen && firstAntigen.getTime() < timestamp.getTime()) {
                                            return trendGuessing
                                                    ? Math.round(antigenRatio[0] * pcrPositives)
                                                    : Math.round(pcrPositives / FRACTION_OVER_40_YEARS_OLD
                                                            * FRACTION_UNDER_40_YEARS_OLD);
                                        } else {
                                            return 0L;
                                        }
                                    });
                            double value = pcrPositives + antigenPositives;
                            values.add(value);
                            values2.add((value - 300) * 4 + 4000);
                            values3.add(Math.max(pcrPositives, antigenPositives));
                            values3a.add(antigenPositives);
                            values3p.add(pcrPositives);
                            values3p2.add(pcrPositives + (firstAntigen.getTime() < timestamp.getTime() ?
                                    (trendGuessing ? pcrPositives * antigenRatio[0] : pcrPositives / FRACTION_OVER_40_YEARS_OLD * FRACTION_UNDER_40_YEARS_OLD) : 0));
                            values4.add((value - 7000) * 1.8 + 4000);
                            values6.add(value * 10 + 4000);
                            previous[0] = r;
                        });
                        chart.addSeries("2021-2022 (#)x1", times, gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                        chart.addSeries("2020-2021 (#-300)x4+4000", times2,
                                        gaussianSmooth(replaceNulls(values2, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                        chart.addSeries("2021-2022 (#)x1\nlower bound", times,
                                        gaussianSmooth(replaceNulls(values3, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                        if (guessAntigen) {
                            chart.addSeries("2021-2022 (PCR only est)x1", times,
                                            gaussianSmooth(replaceNulls(values3p2, 0), sigma))
                                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                    .setMarker(SeriesMarkers.NONE)
                                    .setLineStyle(SeriesLines.DASH_DASH);
                        }
                        // chart.addSeries("2019-2020 (#)x10+4000", times6, gaussianSmooth(replaceNulls(values6, 0),
                        // sigma))
                        //         .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                        //         .setMarker(SeriesMarkers.NONE);
//                        chart.addSeries("2021-2022 (#)x1\nAntigen", times, gaussianSmooth(replaceNulls(values3a, 0)
//                        , sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE)
//                                .setLineStyle(SeriesLines.DOT_DOT);
//                        chart.addSeries("2021-2022 (#)x1\nPCR", times, gaussianSmooth(replaceNulls(values3p, 0),
//                        sigma))
//                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
//                                .setMarker(SeriesMarkers.NONE)
//                                .setLineStyle(SeriesLines.DASH_DASH);
                        //     chart.addSeries("2022 Feb-Mar (#-7000)x1.8+4000", times4, gaussianSmooth(replaceNulls
                        //     (values4, 0), sigma))
                        //             .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                        //             .setMarker(SeriesMarkers.NONE);
                    }

                    BufferedImage image = BitmapEncoder.getBufferedImage(chart);
                    if (writer == null) {
                        writer = new GifSequenceWriter(output, image.getType(), 750, true);
                    }
                    writer.writeToSequence(image);
                    if (Math.abs(sigma - 3) < 0.01) {
                        BitmapEncoder.saveBitmap(chart,
                                String.format("./graphs/COVID-19_Positives_Vs_Last_Year-%04.1f.png", sigma),
                                BitmapEncoder.BitmapFormat.PNG);
                    }
                }
            } finally {
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
            }
            System.err.println("./graphs/COVID-19_Positives_Vs_Last_Year.gif");
        }


    }

    private static Date parseTimestamp(String v1) {
        return new Date(
                OffsetDateTime.parse(v1.replace('/', '-').replace(' ', 'T').replace("+00", "Z"))
                        .toInstant().toEpochMilli());
    }

    private static Date parseWeekNum(String v1) {
        int year = Integer.parseInt(v1.substring(0, 4));
        int week = Integer.parseInt(v1.substring(6));
        return new Date(OffsetDateTime.of(year, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .toInstant().toEpochMilli());
    }

    public static List<Number> replaceNulls(List<? extends Number> y, Number replacement) {
        return y.stream().map(n -> n == null ? replacement : n)
                .collect(Collectors.toCollection(() -> new ArrayList<>(y.size())));
    }

    public static List<Double> gaussianSmooth(List<? extends Number> y, double sigma) {
        sigma = Math.abs(sigma);
        // this is the index after which the weighting function is less than 1e-14 of the midpointy value
        // in other words, there is no point smoothing beyond this as the contribution will never add anything
        // significant
        int indexWidth = (int) Math.floor(Math.sqrt(-2.0 * Math.log(1e-14)) * sigma);
        if (indexWidth < 1) {
            // the second and subsequent points are less than the precision of a Double in total contributions
            // so no smoothing will occur
            return y.stream().map(Number::doubleValue).collect(Collectors.toList());
        }
        double[] weights = new double[indexWidth];
        double sum = 0;
        for (int x = 0; x < weights.length; x++) {
            weights[x] = 1.0 / sigma / Math.sqrt(2 * Math.PI) * Math.exp(-0.5 * x * x / sigma / sigma);
            sum += weights[x];
        }
        sum = sum * 2 - weights[0];
        for (int x = 0; x < weights.length; x++) {
            weights[x] = weights[x] / sum;
        }
        int yLength = y.size();
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < yLength; i++) {
            if (i > weights.length && i < yLength - weights.length) {
                double v = weights[0] * y.get(i).doubleValue();
                for (int j = 1; j < weights.length; j++) {
                    v = v + weights[j] * (y.get(i - j).doubleValue() + y.get(i + j).doubleValue());
                }
                result.add(i, v);
            } else {
                double s = weights[0];
                double v = weights[0] * y.get(i).doubleValue();
                for (int j = 1; j < weights.length; j++) {
                    if (i + j < yLength) {
                        s = s + weights[j];
                        v = v + weights[j] * y.get(i + j).doubleValue();
                    }
                    if (i - j >= 0) {
                        s = s + weights[j];
                        v = v + weights[j] * y.get(i - j).doubleValue();
                    }
                }
                result.add(i, v / s);
            }
        }
        return result;
    }

    public static double pearsonCorrelationCoefficient(List<? extends Number> x, List<? extends Number> y) {
        int n = x.size();
        if (n != y.size()) {
            throw new IllegalArgumentException("Expected two lists of equal length");
        }
        if (n <= 1) {
            throw new IllegalArgumentException("Expected lists with more than one element");
        }
        double sX = 0.0;
        double sY = 0.0;
        double sXY = 0.0;
        double sXX = 0.0;
        double sYY = 0.0;
        for (int i = 0; i < n; i++) {
            Number vX = x.get(i);
            Number vY = y.get(i);
            sX += vX == null ? 0.0 : vX.doubleValue();
            sXX += vX == null ? 0.0 : vX.doubleValue() * vX.doubleValue();
            sY += vY == null ? 0.0 : vY.doubleValue();
            sYY += vY == null ? 0.0 : vY.doubleValue() * vY.doubleValue();
            sXY += vX == null || vY == null ? 0.0 : vX.doubleValue() * vY.doubleValue();
        }
        return (n * sXY - sX * sY) / Math.sqrt(n * sXX - sX * sX) / Math.sqrt(n * sYY - sY * sY);
    }

    public static List<Date> fillMissingDays(List<Date> dates) {
        Date min = dates.stream().min(Date::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("supplied dates empty"));
        Date max = dates.stream().max(Date::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("supplied dates empty"));
        LocalDate start = min.toInstant().atOffset(ZoneOffset.UTC).toLocalDate();
        LocalDate end = max.toInstant().atOffset(ZoneOffset.UTC).toLocalDate();

        int days = (int) (end.toEpochDay() - start.toEpochDay() + 1);
        List<Date> result = new ArrayList<>(days);
        for (int offset = 0; offset < days; offset++) {
            result.add(new Date(start.plusDays(offset).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()));
        }
        return result;
    }

    public static <T extends Number> List<T> extractMatching(List<Date> outputDates, List<Date> inputDates,
                                                             List<T> input) {
        List<T> result = new ArrayList<>(outputDates.size());
        int j = 0;
        OUTPUT:
        for (int i = 0; i < outputDates.size(); i++) {
            LocalDate outputDate = outputDates.get(i).toInstant().atOffset(ZoneOffset.UTC).toLocalDate();
            while (j < inputDates.size()) {
                LocalDate inputDate = inputDates.get(j).toInstant().atOffset(ZoneOffset.UTC).toLocalDate();
                if (inputDate.equals(outputDate)) {
                    result.add(input.get(j));
                    continue OUTPUT;
                }
                if (inputDate.compareTo(outputDate) > 0) {
                    break;
                }
                j++;
            }
            result.add(null);
        }
        return result;
    }

    public static List<Integer> correlationOffsets(List<? extends Number> v1, List<? extends Number> v2,
                                                   int matchingLength, int matchingWindow) {
        if (v1.size() != v2.size()) {
            throw new IllegalArgumentException("Mismatched input lengths");
        }
        List<Integer> result = new ArrayList<>(v1.size());
        for (int i = 0; i < matchingWindow + matchingLength / 2; i++) {
            result.add(null);
        }
        for (int i = matchingWindow + matchingLength / 2;
             i < v1.size() - matchingWindow - (matchingLength - matchingLength / 2); i++) {
            int start = i - matchingLength / 2;
            List<? extends Number> source = v1.subList(start, start + matchingLength);
            Double maxCorr = null;
            Integer maxOffset = null;
            for (int j = -matchingWindow; j < matchingWindow; j++) {
                List<? extends Number> target = v2.subList(start + j, start + j + matchingLength);
                double corr = pearsonCorrelationCoefficient(source, target);
                if (maxCorr == null || maxCorr < corr) {
                    maxCorr = corr;
                    maxOffset = j;
                }
            }
            result.add(maxOffset);
        }
        for (int i = 0; i < matchingWindow + (matchingLength - matchingLength / 2); i++) {
            result.add(null);
        }
        return result;
    }

    public static class AcuteHospital {
        @JsonProperty("X")
        public Double x;
        @JsonProperty("Y")
        public Double y;
        @JsonProperty("OBJECTID")
        public String objectId;
        @JsonProperty("Date")
        public String timestamp;
        @JsonProperty("SUM_number_of_confirmed_covid_1")
        public Long currentConfirmedCovidPositive;
        @JsonProperty("SUM_no_new_admissions_covid19_p")
        public Long admissionsCovidPositive;
        @JsonProperty("SUM_no_discharges_covid19_posit")
        public Long dischargesCovidPositive;
        @JsonProperty("SUM_number_of_new_covid_19_cases_co")
        public Long newCovidCasesCovid;
    }

    public static class ICUHospital {
        @JsonProperty("OBJECTID")
        public String objectId;
        @JsonProperty("extract")
        public String timestamp;
        @JsonProperty("ncovidconf")
        public Long currentConfirmedCovidPositive;
        @JsonProperty("ndischcovidconf")
        public Long dischargesCovidPositive;
        @JsonProperty("adcconf")
        public Long admissionsCovidPositive;
    }

    public static class Antigen {
        @JsonProperty("DateOfData")
        public String timestamp;
        @JsonProperty("RegisteredPositiveAntigenFigure")
        public Long positives;
    }

    public static class LabTests {
        @JsonProperty("Date_HPSC")
        public String timestamp;
        @JsonProperty("Hospitals")
        public Long totalLabsFromHospitals;
        @JsonProperty("TotalLabs")
        public Long totalLabsTotal;
        @JsonProperty("NonHospitals")
        public Long totalLabsNotFromHospitals;
        @JsonProperty("Positive")
        public Long totalPositives;
        @JsonProperty("PRate")
        public Double positivityRate;
        @JsonProperty("Test24")
        public Long testsIn24h;
        @JsonProperty("Test7")
        public Long testsIn7d;
        @JsonProperty("Pos7")
        public Long positivesIn7d;
        @JsonProperty("PosR7")
        public Double positivityRateIn7d;
        @JsonProperty("FID")
        public String objectId;
    }

    public static class Stats {
        @JsonProperty("X")
        public Double x;
        @JsonProperty("Y")
        public Double y;
        @JsonProperty("Date")
        public String timestamp;
        @JsonProperty("ConfirmedCovidCases")
        public Long confirmedCovidCases;
        @JsonProperty("TotalConfirmedCovidCases")
        public Long totalConfirmedCovidCases;
        @JsonProperty("ConfirmedCovidDeaths")
        public Long confirmedCovidDeaths;
        @JsonProperty("TotalCovidDeaths")
        public Long totalCovidDeaths;
        @JsonProperty("StatisticsProfileDate")
        public String statisticsProfileDate;
        @JsonProperty("CovidCasesConfirmed")
        public Long covidCasesConfirmed;
        @JsonProperty("HospitalisedCovidCases")
        public Long hospitalisedCovidCases;
        @JsonProperty("RequiringICUCovidCases")
        public Long requiringICUCovidCases;
        @JsonProperty("HealthcareWorkersCovidCases")
        public Long healthcareWorkersCovidCases;
        @JsonProperty("ClustersNotified")
        public Long clustersNotified;
        @JsonProperty("HospitalisedAged5")
        public Long hospitalisedAged5;
        @JsonProperty("HospitalisedAged5to14")
        public Long hospitalisedAged5to14;
        @JsonProperty("HospitalisedAged15to24")
        public Long hospitalisedAged15to24;
        @JsonProperty("HospitalisedAged25to34")
        public Long hospitalisedAged25to34;
        @JsonProperty("HospitalisedAged35to44")
        public Long hospitalisedAged35to44;
        @JsonProperty("HospitalisedAged45to54")
        public Long hospitalisedAged45to54;
        @JsonProperty("HospitalisedAged55to64")
        public Long hospitalisedAged55to64;
        @JsonProperty("Male")
        public Long male;
        @JsonProperty("Female")
        public Long female;
        @JsonProperty("Unknown")
        public Long unknown;
        @JsonProperty("Aged1to4")
        public Long aged1to4;
        @JsonProperty("Aged5to14")
        public Long qged5to14;
        @JsonProperty("Aged15to24")
        public Long aged15to24;
        @JsonProperty("Aged25to34")
        public Long aged25to34;
        @JsonProperty("Aged35to44")
        public Long aged35to44;
        @JsonProperty("Aged45to54")
        public Long aged45to54;
        @JsonProperty("Aged55to64")
        public Long aged55to64;
        @JsonProperty("Median_Age")
        public Long medianAge;
        @JsonProperty("CommunityTransmission")
        public Long communityTransmission;
        @JsonProperty("CloseContact")
        public Long closeContact;
        @JsonProperty("TravelAbroad")
        public Long travelAbroad;
        @JsonProperty("FID")
        public Long objectId;
        @JsonProperty("HospitalisedAged65to74")
        public Long hospitalisedAged65to74;
        @JsonProperty("HospitalisedAged75to84")
        public Long hospitalisedAged75to84;
        @JsonProperty("HospitalisedAged85up")
        public Long hospitalisedAged85up;
        @JsonProperty("Aged65to74")
        public Long aged65to74;
        @JsonProperty("Aged75to84")
        public Long aged75to84;
        @JsonProperty("Aged85up")
        public Long aged85up;
        @JsonProperty("DeathsCumulative_DOD")
        public Long deathsCumulativeDOD;
        @JsonProperty("DeathsToday_DOD")
        public Long deathsTodayDOD;
        @JsonProperty("SevenDayAvg_Cases")
        public Long severDayAvgCases;
    }

    public static class WeeklyVax {
        @JsonProperty("X")
        public Double x;
        @JsonProperty("Y")
        public Double y;
        @JsonProperty("ExtractDate")
        public String timestamp;
        @JsonProperty("Week")
        public String week;
        @JsonProperty("TotalweeklyVaccines")
        public Long totalWeeklyVaccines;
        @JsonProperty("Male")
        public Long male;
        @JsonProperty("Female")
        public Long female;
        @JsonProperty("NA")
        public Long na;
        @JsonProperty("Moderna")
        public Long moderna;
        @JsonProperty("Pfizer")
        public Long pfizer;
        @JsonProperty("Janssen")
        public Long janssen;
        @JsonProperty("AstraZeneca")
        public Long astraZeneca;
        @JsonProperty("Partial_Age0to9")
        public Long partialAge0To9;
        @JsonProperty("Partial_Age10to19")
        public Long partialAge10To19;
        @JsonProperty("Partial_Age20to29")
        public Long partialAge20To29;
        @JsonProperty("Partial_Age30to39")
        public Long partialAge30To39;
        @JsonProperty("Partial_Age40to49")
        public Long partialAge40To49;
        @JsonProperty("Partial_Age50to59")
        public Long partialAge50To59;
        @JsonProperty("Partial_Age60to69")
        public Long partialAge60To69;
        @JsonProperty("Partial_Age70to79")
        public Long partialAge70To79;
        @JsonProperty("Partial_Age80_")
        public Long partialAge80Plus;
        @JsonProperty("Partial_NA")
        public Long partialAgeNA;
        @JsonProperty("ParCum_Age0to9")
        public Long partialCumulativeAge0To9;
        @JsonProperty("ParCum_Age10to19")
        public Long partialCumulativeAge10To19;
        @JsonProperty("ParCum_Age20to29")
        public Long partialCumulativeAge20To29;
        @JsonProperty("ParCum_Age30to39")
        public Long partialCumulativeAge30To39;
        @JsonProperty("ParCum_Age40to49")
        public Long partialCumulativeAge40To49;
        @JsonProperty("ParCum_Age50to59")
        public Long partialCumulativeAge50To59;
        @JsonProperty("ParCum_Age60to69")
        public Long partialCumulativeAge60To69;
        @JsonProperty("ParCum_Age70to79")
        public Long partialCumulativeAge70To79;
        @JsonProperty("ParCum_80_")
        public Long partialCumulativeAge80Plus;
        @JsonProperty("ParCum_NA")
        public Long partialCumulativeAgeNA;
        @JsonProperty("ParPer_Age0to9")
        public Double partialPercentAge0To9;
        @JsonProperty("ParPer_Age10to19")
        public Double partialPercentAge10To19;
        @JsonProperty("ParPer_Age20to29")
        public Double partialPercentAge20To29;
        @JsonProperty("ParPer_Age30to39")
        public Double partialPercentAge30To39;
        @JsonProperty("ParPer_Age40to49")
        public Double partialPercentAge40To49;
        @JsonProperty("ParPer_Age50to59")
        public Double partialPercentAge50To59;
        @JsonProperty("ParPer_Age60to69")
        public Double partialPercentAge60To69;
        @JsonProperty("ParPer_Age70to79")
        public Double partialPercentAge70To79;
        @JsonProperty("ParPer_80_")
        public Double partialPercentAge80Plus;
        @JsonProperty("ParPer_NA")
        public String partialPercentAgeNA;
        @JsonProperty("Fully_Age0to9")
        public Long fullyAge0To9;
        @JsonProperty("Fully_Age10to19")
        public Long fullyAge10To19;
        @JsonProperty("Fully_Age20to29")
        public Long fullyAge20To29;
        @JsonProperty("Fully_Age30to39")
        public Long fullyAge30To39;
        @JsonProperty("Fully_Age40to49")
        public Long fullyAge40To49;
        @JsonProperty("Fully_Age50to59")
        public Long fullyAge50To59;
        @JsonProperty("Fully_Age60to69")
        public Long fullyAge60To69;
        @JsonProperty("Fully_Age70to79")
        public Long fullyAge70To79;
        @JsonProperty("Fully_Age80_")
        public Long fullyAge80Plus;
        @JsonProperty("Fully_NA")
        public Long fullyAgeNA;
        @JsonProperty("FullyCum_Age0to9")
        public Long fullyCumulativeAge0To9;
        @JsonProperty("FullyCum_Age10to19")
        public Long fullyCumulativeAge10To19;
        @JsonProperty("FullyCum_Age20to29")
        public Long fullyCumulativeAge20To29;
        @JsonProperty("FullyCum_Age30to39")
        public Long fullyCumulativeAge30To39;
        @JsonProperty("FullyCum_Age40to49")
        public Long fullyCumulativeAge40To49;
        @JsonProperty("FullyCum_Age50to59")
        public Long fullyCumulativeAge50To59;
        @JsonProperty("FullyCum_Age60to69")
        public Long fullyCumulativeAge60To69;
        @JsonProperty("FullyCum_Age70to79")
        public Long fullyCumulativeAge70To79;
        @JsonProperty("FullyCum_80_")
        public Long fullyCumulative80Plus;
        @JsonProperty("FullyCum_NA")
        public Long fullyCumulativeNA;
        @JsonProperty("FullyPer_Age0to9")
        public Double fullyPercentAge0To9;
        @JsonProperty("FullyPer_Age10to19")
        public Double fullyPercentAge10To19;
        @JsonProperty("FullyPer_Age20to29")
        public Double fullyPercentAge20To29;
        @JsonProperty("FullyPer_Age30to39")
        public Double fullyPercentAge30To39;
        @JsonProperty("FullyPer_Age40to49")
        public Double fullyPercentAge40To49;
        @JsonProperty("FullyPer_Age50to59")
        public Double fullyPercentAge50To59;
        @JsonProperty("FullyPer_Age60to69")
        public Double fullyPercentAge60To69;
        @JsonProperty("FullyPer_Age70to79")
        public Double fullyPercentAge70To79;
        @JsonProperty("FullyPer_80_")
        public Double fullyPercentAge80Plus;
        @JsonProperty("FullyPer_NA")
        public String fullyPercentAgeNA;
        @JsonProperty("ObjectId")
        public String oid;
    }

    public static class GifSequenceWriter {

        protected ImageWriter writer;
        protected ImageWriteParam params;
        protected IIOMetadata metadata;

        public GifSequenceWriter(ImageOutputStream out, int imageType, int delay, boolean loop) throws IOException {
            writer = ImageIO.getImageWritersBySuffix("gif").next();
            params = writer.getDefaultWriteParam();

            ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(imageType);
            metadata = writer.getDefaultImageMetadata(imageTypeSpecifier, params);

            configureRootMetadata(delay, loop);

            writer.setOutput(out);
            writer.prepareWriteSequence(null);
        }

        private void configureRootMetadata(int delay, boolean loop) throws IIOInvalidTreeException {
            String metaFormatName = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

            IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
            graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
            graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
            graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
            graphicsControlExtensionNode.setAttribute("delayTime", Integer.toString(delay / 10));
            graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0");

            IIOMetadataNode commentsNode = getNode(root, "CommentExtensions");
            commentsNode.setAttribute("CommentExtension", "Created by: https://twitter.com/connolly_s");

            IIOMetadataNode appExtensionsNode = getNode(root, "ApplicationExtensions");
            IIOMetadataNode child = new IIOMetadataNode("ApplicationExtension");
            child.setAttribute("applicationID", "NETSCAPE");
            child.setAttribute("authenticationCode", "2.0");

            int loopContinuously = loop ? 0 : 1;
            child.setUserObject(
                    new byte[] {0x1, (byte) (loopContinuously & 0xFF), (byte) ((loopContinuously >> 8) & 0xFF)});
            appExtensionsNode.appendChild(child);
            metadata.setFromTree(metaFormatName, root);
        }

        private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
            int nNodes = rootNode.getLength();
            for (int i = 0; i < nNodes; i++) {
                if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                    return (IIOMetadataNode) rootNode.item(i);
                }
            }
            IIOMetadataNode node = new IIOMetadataNode(nodeName);
            rootNode.appendChild(node);
            return (node);
        }

        public void writeToSequence(RenderedImage img) throws IOException {
            writer.writeToSequence(new IIOImage(img, null, metadata), params);
        }

        public void close() throws IOException {
            writer.endWriteSequence();
        }

    }
}
