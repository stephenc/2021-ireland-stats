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
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
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
            OffsetDateTime cutOff = OffsetDateTime.now().minus(Period.of(0,0,28));
            String threshold = cutOff.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            acuteHospitals.stream().filter(r -> r.timestamp.compareTo(threshold) > 0).forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values1.add(r.admissionsCovidPositive);
                if (r.admissionsCovidPositive != null && r.newCovidCasesCovid != null && r.newCovidCasesCovid > 0) {
                    values2.add(r.newCovidCasesCovid-r.admissionsCovidPositive);
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

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-COVID-19_NOCA_ICUBIS_Historic_Time_Series.png",
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

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-COVID-19_ICU_Vs_All_Hospitalized.png",
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
                if (i2 >= icuHospitals.size()) break;
                ICUHospital v2 = icuHospitals.get(i2);
                times.add(parseTimestamp(v1.timestamp));
                values.add(v2.currentConfirmedCovidPositive * 100.0/ v1.currentConfirmedCovidPositive);
                i1++;
            }
            chart.addSeries("ICU", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }

        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-COVID-19_Hospitalized_Fraction_ICU.png",
                BitmapEncoder.BitmapFormat.PNG);

        chart = new XYChartBuilder()
                .width(1200)
                .height(675)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle("Number of PCR Positive samples tested")
                .title("Laboratory PCR Tests")
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");

        {
            List<Date> times = new ArrayList<>(labTests.size());
            List<Number> values = new ArrayList<>(labTests.size());
            LabTests[] previous = new LabTests[1];
            labTests.forEach(r -> {
                times.add(parseTimestamp(r.timestamp));
                values.add(r.totalPositives - (previous[0]==null?0: previous[0].totalPositives));
                previous[0] = r;
            });
            chart.addSeries("Labs", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }
        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Laboratory_Testing_Time_Series.png",
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
                values.add((r.totalPositives - (previous[0]==null?0: previous[0].totalPositives)) * 100.0 / (r.totalLabsTotal - (previous[0]==null ? 0 : previous[0].totalLabsTotal)));
                previous[0] = r;
            });
            chart.addSeries("Labs", times, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        }
        BitmapEncoder.saveBitmap(chart, "./graphs/COVID-19_Laboratory_Testing_Percent_Positive.png",
                BitmapEncoder.BitmapFormat.PNG);


    }

    private static Date parseTimestamp(String v1) {
        return new Date(
                OffsetDateTime.parse(v1.replace('/', '-').replace(' ', 'T').replace("+00", "Z"))
                        .toInstant().toEpochMilli());
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
}
