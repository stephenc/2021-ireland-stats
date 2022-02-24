///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 11+
//JAVAC_OPTIONS -Xlint:unchecked
//DEPS com.fasterxml.jackson.core:jackson-databind:2.12.4
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.12.2
//DEPS org.apache.commons:commons-text:1.9
//DEPS org.knowm.xchart:xchart:3.8.0

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
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
import org.knowm.xchart.style.markers.SeriesMarkers;

public class graphs {

    public static void main(String... args) throws IOException {

        Instant thirtyDaysAgo = LocalDate.now().minusDays(30).atStartOfDay().toInstant(ZoneOffset.UTC);

        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        List<AcuteHospital> acuteHospitals = csvMapper.readerFor(AcuteHospital.class)
                .with(schema)
                .<AcuteHospital>readValues(
                        Paths.get("data/COVID-19_SDU_Acute_Hospital_Time_Series_Summary.csv").toFile()).readAll();
        acuteHospitals.sort(Comparator.comparing(r -> r.timestamp));

        List<ICUHospital> icuHospitals = csvMapper.readerFor(ICUHospital.class)
                .with(schema)
                .<ICUHospital>readValues(
                        Paths.get("data/COVID-19_NOCA_ICUBIS_Historic_Time_Series.csv").toFile()).readAll();

        icuHospitals.sort(Comparator.comparing(r -> r.timestamp));

        List<LabTests> labTests = csvMapper.readerFor(LabTests.class)
                .with(schema)
                .<LabTests>readValues(
                        Paths.get("data/COVID-19_Laboratory_Testing_Time_Series.csv").toFile()).readAll();

        labTests.sort(Comparator.comparing(r -> r.timestamp));

        List<Stats> stats = csvMapper.readerFor(Stats.class)
                .with(schema)
                .<Stats>readValues(
                        Paths.get("data/COVID-19_HPSC_Detailed_Statistics_Profile.csv").toFile()).readAll();

        stats.sort(Comparator.comparing(r -> r.timestamp));

        List<Antigen> antigens = csvMapper.readerFor(Antigen.class)
                .with(schema)
                .<Antigen>readValues(
                        Paths.get("data/COVID-19_Antigen.csv").toFile()).readAll();

        antigens.sort(Comparator.comparing(r -> r.timestamp));

        new File("./graphs").mkdirs();

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

        chart.getStyler().setXAxisMin(thirtyDaysAgo.toEpochMilli() * 1.0);
        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_SDU_Acute_Hospital_Time_Series_Last30.png",
                BitmapEncoder.BitmapFormat.PNG);


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

        chart.getStyler().setXAxisMin(thirtyDaysAgo.toEpochMilli() * 1.0);

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_NOCA_ICUBIS_Historic_Time_Series_Last30.png",
                BitmapEncoder.BitmapFormat.PNG);


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

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Number of PCR Positive samples tested")
                .title("Laboratory PCR Tests and Antigen Tests")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> values = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            labTests.forEach(r -> {
                Date timestamp = parseTimestamp(r.timestamp);
                times.add(timestamp);
                values.add(r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives) + antigens.stream()
                        .filter(x -> parseTimestamp(x.timestamp).toInstant()
                                .atOffset(ZoneOffset.UTC).toLocalDate()
                                .equals(timestamp.toInstant().atOffset(ZoneOffset.UTC)
                                        .toLocalDate()))
                        .map(x -> x.positives)
                        .findAny()
                        .orElse(0L));
                previous[0] = r;
            });
            chart.addSeries("Total", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }
        {
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> values = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            labTests.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
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

        chart.getStyler().setXAxisMin(thirtyDaysAgo.toEpochMilli() * 1.0);

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Laboratory_Testing_Time_Series_Last30.png",
                BitmapEncoder.BitmapFormat.PNG);


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

        chart.getStyler().setXAxisMin(thirtyDaysAgo.toEpochMilli() * 1.0);

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Laboratory_Testing_Percent_Positive_Last30.png",
                BitmapEncoder.BitmapFormat.PNG);

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
                    chart.getStyler().setYAxisMax(2.2);
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
                        List<Date> times = new ArrayList<>(acuteHospitals.size());
                        List<Number> values = new ArrayList<>(acuteHospitals.size());
                        Long max = acuteHospitals.stream().filter(r -> r.admissionsCovidPositive != null)
                                .map(r -> r.admissionsCovidPositive).max(Long::compareTo).orElse(1L);
                        acuteHospitals.forEach(r -> {
                            times.add(parseTimestamp(r.timestamp));
                            values.add(
                                    r.admissionsCovidPositive == null ? null : r.admissionsCovidPositive * 1.0 / max);
                        });
                        chart.addSeries("New Covid admissions to Hospital", times,
                                        gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

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

                    {
                        List<Date> times = new ArrayList<>(icuHospitals.size());
                        List<Number> values = new ArrayList<>(icuHospitals.size());
                        Long max = icuHospitals.stream().filter(r -> r.admissionsCovidPositive != null)
                                .map(r -> r.admissionsCovidPositive).max(Long::compareTo).orElse(1L);
                        icuHospitals.forEach(r -> {
                            times.add(parseTimestamp(r.timestamp));
                            values.add(
                                    r.admissionsCovidPositive == null ? null : r.admissionsCovidPositive * 1.0 / max);
                        });
                        chart.addSeries("New Covid admissions to ICU", times,
                                        gaussianSmooth(replaceNulls(values, 0), sigma))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

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
                        Long max = stats.stream().filter(r -> r.confirmedCovidDeaths != null)
                                .map(r -> r.confirmedCovidDeaths).max(Long::compareTo).orElse(1L);
                        stats.forEach(r -> {
                            times.add(parseTimestamp(r.timestamp));
                            values.add(r.confirmedCovidDeaths == null ? null : r.confirmedCovidDeaths * 1.0 / max);
                        });
                        chart.addSeries("Deaths", times, gaussianSmooth(replaceNulls(values, 0), Math.max(sigma, 5)))
                                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                                .setMarker(SeriesMarkers.NONE);
                    }

                    BufferedImage image = BitmapEncoder.getBufferedImage(chart);
                    if (writer == null) {
                        writer = new GifSequenceWriter(output, image.getType(), 500, true);
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
    }

    private static Date parseTimestamp(String v1) {
        return new Date(
                OffsetDateTime.parse(v1.replace('/', '-').replace(' ', 'T').replace("+00", "Z"))
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
