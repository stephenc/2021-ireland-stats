package io.github.stephenc.visualfit;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import io.github.stephenc.visualfit.data.Antigen;
import io.github.stephenc.visualfit.data.LabTests;
import io.github.stephenc.visualfit.data.OWIDTesting;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.markers.SeriesMarkers;

/**
 * @author Stephen Connolly
 */
public class VisualFit {
    protected static final OffsetDateTime START_DATE = OffsetDateTime.of(2022, 4, 15, 0, 0, 0, 0, ZoneOffset.UTC);
    protected static final OffsetDateTime END_DATE = OffsetDateTime.of(2022, 7, 15, 0, 0, 0, 0, ZoneOffset.UTC);
    private XChartPanel<XYChart> smoothed;
    private XChartPanel<XYChart> raw;
    private XChartPanel<XYChart> peaks;
    private XChartPanel<XYChart> residuals;
    private JPanel graphs;
    private JTextArea params;
    private JPanel root;

    private List<Peak> peakList = new ArrayList<>();

    private double baseline = 0;
    private List<Date> rawDates = new ArrayList<>();
    private List<Number> rawData = new ArrayList<>();
    private OffsetDateTime startDate = START_DATE;
    private OffsetDateTime endDate = END_DATE;
    private double smoothing = 3.0;

    private record Peak(LocalDate maximum, double height, double rate) {
    }

    public VisualFit() {
        $$$setupUI$$$();

        Pattern peakDef = Pattern.compile(
                "^\\s*(\\d{4}-\\d{2}-\\d{2})\\s*,\\s*(\\d+(?:.\\d*)?)\\s*,\\s*(\\d+(?:.\\d*)?)\\s*(?:#.*)?$");
        Pattern smoothingDef = Pattern.compile("^\s*smoothing\\s*=\\s*(\\d+(?:.\\d*)?)\\s*(?:#.*)?$");
        Pattern baselineDef = Pattern.compile("^\s*baseline\\s*=\\s*(\\d+(?:.\\d*)?)\\s*(?:#.*)?$");
        Pattern startDef = Pattern.compile("^\s*start\\s*=\\s*(\\d{4}-\\d{2}-\\d{2})\\s*(?:#.*)?$");
        Pattern endDef = Pattern.compile("^\s*end\\s*=\\s*(\\d{4}-\\d{2}-\\d{2})\\s*(?:#.*)?$");
        params.addKeyListener(new KeyAdapter() {

            @Override
            public void keyReleased(KeyEvent e) {
                keyTyped(e);
            }

            @Override
            public void keyTyped(KeyEvent e) {
                var peakList = params.getText()
                        .lines()
                        .map(peakDef::matcher)
                        .filter(Matcher::matches)
                        .map(peak -> new Peak(LocalDate.parse(peak.group(1)), Double.parseDouble(peak.group(2)),
                                Double.parseDouble(peak.group(3))))
                        .toList();
                var smoothing = params.getText()
                        .lines()
                        .map(smoothingDef::matcher)
                        .filter(Matcher::matches)
                        .findFirst()
                        .map(m -> Double.valueOf(m.group(1)))
                        .orElse(3.0);
                var baseline = params.getText()
                        .lines()
                        .map(baselineDef::matcher)
                        .filter(Matcher::matches)
                        .findFirst()
                        .map(m -> Double.valueOf(m.group(1)))
                        .orElse(0.0);
                var startDate = params.getText()
                        .lines()
                        .map(startDef::matcher)
                        .filter(Matcher::matches)
                        .findFirst()
                        .map(m -> LocalDate.parse(m.group(1)).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime())
                        .orElseGet(() -> rawDates.get(0).toInstant().atOffset(ZoneOffset.UTC));
                var endDate = params.getText()
                        .lines()
                        .map(endDef::matcher)
                        .filter(Matcher::matches)
                        .findFirst()
                        .map(m -> LocalDate.parse(m.group(1)).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime())
                        .orElseGet(() -> rawDates.get(rawDates.size() - 1).toInstant().atOffset(ZoneOffset.UTC));
                if (endDate.isBefore(startDate)) {
                    var x = startDate;
                    startDate = endDate;
                    endDate = x;
                }

                if (!VisualFit.this.peakList.equals(peakList)
                        || Math.abs(smoothing - VisualFit.this.smoothing) > 0.01
                        || Math.abs(baseline - VisualFit.this.baseline) > 1.0
                        || !startDate.equals(VisualFit.this.startDate)
                        || !endDate.equals(VisualFit.this.endDate)
                ) {
                    VisualFit.this.peakList = peakList;
                    VisualFit.this.baseline = baseline;
                    VisualFit.this.smoothing = smoothing;
                    VisualFit.this.startDate = startDate;
                    VisualFit.this.endDate = endDate;
                    SwingUtilities.invokeLater(VisualFit.this::updateGraphs);
                }
            }
        });
    }

    private void updateGraphs() {
        raw.getChart().getSeriesMap().clear();
        smoothed.getChart().getSeriesMap().clear();
        new ArrayList<>(peaks.getChart().getSeriesMap().keySet()).forEach(
                peaks.getChart()::removeSeries);
        new ArrayList<>(residuals.getChart().getSeriesMap().keySet()).forEach(
                residuals.getChart()::removeSeries);

        List<Date> xAxis = new ArrayList<>();
        List<Double> fit = new ArrayList<>();
        List<Double> residualValues = new ArrayList<>();
        {
            var i = startDate;
            while (!i.isAfter(endDate)) {
                var date = new Date(i.toInstant().toEpochMilli());
                xAxis.add(date);
                fit.add(baseline);

                var j = ChronoUnit.DAYS.between(rawDates.get(0).toInstant().atOffset(ZoneOffset.UTC),
                        i);
                if (j >= 0 && j < rawData.size()) {
                    var n = rawData.get((int) j);
                    if (n == null || n instanceof Double) {
                        residualValues.add((Double) n);
                    } else {
                        residualValues.add(n.doubleValue());
                    }
                } else {
                    residualValues.add(null);
                }

                i = i.plusDays(1);
            }
            while (residualValues.size() < fit.size()) {
                residualValues.add(null);
            }
        }

        Map<String, List<Double>> peakMap = new LinkedHashMap<>();
        for (int i = 0; i < peakList.size(); i++) {
            var name = String.format("Wave #%d", i + 1);
            List<Double> values = new ArrayList<>(xAxis.size());
            var peak = peakList.get(i);
            var max =
                    peak.maximum.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime().toInstant()
                            .toEpochMilli();
            var h = peak.height;
            var k = peak.rate;
            xAxis.forEach(date -> {
                var x = TimeUnit.MILLISECONDS.toDays(date.getTime() - max);
                var value = h * (Math.exp(-Math.exp(-k * x)) - Math.exp(-Math.exp(-k * (x - 1))));
                values.add(value >= 0.5 ? value : null);
            });
            peakMap.put(name, values);
            for (int j = 0; j < fit.size(); j++) {
                var value = values.get(j);
                if (value != null) {
                    fit.set(j, fit.get(j) + value);
                }
            }
        }
        int rawStart = rawDates.size();
        int rawEnd = 0;
        for (int i = 0; i < rawDates.size(); i++) {
            var when = rawDates.get(i).toInstant().atOffset(ZoneOffset.UTC);
            if (i < rawStart && !when.isBefore(startDate)) {
                rawStart = i;
            }
            if (i > rawEnd && !when.isAfter(endDate)) {
                rawEnd = i;
            }
        }

        peaks.getChart().addSeries("Fit", xAxis, fit)
                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                .setMarker(SeriesMarkers.NONE)
                .setLineStyle(SeriesLines.DASH_DOT);
        smoothed.getChart().addSeries("Fit", xAxis, replaceSmall(gaussianSmooth(fit, smoothing), 0.5))
                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                .setMarker(SeriesMarkers.NONE)
                .setLineStyle(SeriesLines.DASH_DOT);
        smoothed.getChart().addSeries("Actual", rawDates.subList(rawStart, rawEnd + 1),
                        gaussianSmooth(replaceNulls(rawData.subList(rawStart, rawEnd + 1), 0), smoothing))
                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                .setMarker(SeriesMarkers.NONE);
        raw.getChart().addSeries("Fit", xAxis, fit)
                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                .setMarker(SeriesMarkers.NONE)
                .setLineStyle(SeriesLines.DASH_DOT);
        raw.getChart()
                .addSeries("Actual", rawDates.subList(rawStart, rawEnd + 1), rawData.subList(rawStart, rawEnd + 1))
                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                .setMarker(SeriesMarkers.NONE);

        peakMap.forEach((name, values) -> {
            peaks.getChart().addSeries(name, xAxis, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
            smoothed.getChart().addSeries(name, xAxis, gaussianSmooth(replaceNulls(values, 0.0), smoothing))
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
            raw.getChart().addSeries(name, xAxis, values)
                    .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                    .setMarker(SeriesMarkers.NONE);
        });


        // these are the current values
        List<Double> smoothedResiduals = new ArrayList<>(gaussianSmooth(replaceNulls(residualValues, 0.0), smoothing));
        List<Double> smoothedFit = new ArrayList<>(gaussianSmooth(fit, smoothing));
        for (int j = 0; j < residualValues.size(); j++) {
            var value = residualValues.get(j);
            if (value != null) {
                residualValues.set(j, value - fit.get(j));
                smoothedResiduals.set(j, smoothedResiduals.get(j) - smoothedFit.get(j));
            } else {
                smoothedResiduals.set(j, null);
            }
        }
        residuals.getChart().addSeries("Residuals", xAxis, residualValues)
                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        residuals.getChart().addSeries("Smoothed", xAxis, smoothedResiduals)
                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line)
                .setMarker(SeriesMarkers.NONE);

        List.of(smoothed, raw, peaks, residuals).forEach(this::rescaleAndPaintChartPanel);
    }

    private void rescaleAndPaintChartPanel(XChartPanel<XYChart> p) {
        p.getChart().getStyler().setXAxisMin((double) (startDate.toInstant().toEpochMilli()));
        p.getChart().getStyler().setXAxisMax((double) (endDate.toInstant().toEpochMilli()));
        p.repaint();
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

    public static List<Number> replaceSmall(List<? extends Number> y, Number threshold) {
        return y.stream().map(n -> n == null ? null : Math.abs(n.doubleValue()) < threshold.doubleValue() ? null : n)
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

    public static void main(String[] args) throws IOException {
        var visualFit = new VisualFit();

        if (args.length == 0 || "irl".equalsIgnoreCase(args[0]) || "irl-pcr".equalsIgnoreCase(args[0])) {
            CsvMapper csvMapper = new CsvMapper();
            CsvSchema schema = CsvSchema.emptySchema().withHeader();
            var antigenWeight = "irl-pcr".equalsIgnoreCase(args[0]) ? 0 : 1;

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


            {
                Date firstAntigen = parseTimestamp(antigens.get(0).timestamp);
                List<Date> times = new ArrayList<>(labTests.size());
                List<Number> values = new ArrayList<>(labTests.size());
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
                                            timestamp.toInstant().atOffset(ZoneOffset.UTC).get(ChronoField.DAY_OF_WEEK),
                                            pcrPositives, p);
                                }
                                return p;
                            })
                            .orElseGet(() -> {
                                if (firstAntigen.getTime() < timestamp.getTime()) {
                                    var predicted = Math.round(predictor.guess(
                                            timestamp.toInstant().atOffset(ZoneOffset.UTC).get(ChronoField.DAY_OF_WEEK),
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
                    values.add(pcrPositives + antigenWeight * antigenPositives);
                    previous[0] = r;
                });
                System.out.println(predictor);
                visualFit.rawDates = times;
                visualFit.rawData = values;
            }
        } else if ("0".equals(args[0])) {
            List<Date> times = new ArrayList<>();
            List<Number> values = new ArrayList<>();
            for (int i = 0; i < 365; i++) {
                times.add(new Date(
                        LocalDate.of(2022, 1, 1).plusDays(i).atStartOfDay().atOffset(ZoneOffset.UTC).toInstant()
                                .toEpochMilli()));
                values.add(
                        10000 * (Math.exp(-Math.exp((-0.05 * (i - 180)))) - Math.exp(-Math.exp((-0.05 * (i - 181))))));
            }
            visualFit.rawDates = times;
            visualFit.rawData = values;
        } else if (args[0].toLowerCase(Locale.ROOT).endsWith(".csv") && Files.exists(Paths.get(args[0]))) {
            CsvMapper csvMapper = new CsvMapper();
            List<List<String>> data = csvMapper.readerForListOf(String.class)
                    .with(CsvParser.Feature.WRAP_AS_ARRAY)
                    .<List<String>>readValues(Paths.get(args[0]).toFile()).readAll();
            List<Date> times = new ArrayList<>();
            List<Number> values = new ArrayList<>();
            data.forEach(r -> {
                Date timestamp = parseTimestamp(r.get(0) + " 00:00:00+00");
                times.add(timestamp);
                values.add(Double.valueOf(r.get(1)));
            });
            visualFit.rawDates = times;
            visualFit.rawData = values;

        } else {
            CsvMapper csvMapper = new CsvMapper();
            CsvSchema schema = CsvSchema.emptySchema().withHeader();
            List<OWIDTesting> tests = csvMapper.readerFor(OWIDTesting.class)
                    .with(schema)
                    .<OWIDTesting>readValues(
                            Paths.get("covid-testing-all-observations.csv").toFile()).readAll();
            tests.removeIf(r -> !args[0].equalsIgnoreCase(r.iso));
            tests.sort(Comparator.comparing(r -> r.timestamp));
            List<Date> times = new ArrayList<>();
            List<Number> values = new ArrayList<>();
            tests.forEach(r -> {
                Date timestamp = parseTimestamp(r.timestamp + " 00:00:00+00");
                times.add(timestamp);
                values.add(r.daily);
            });
            visualFit.rawDates = times;
            visualFit.rawData = values;
        }

        visualFit.startDate = visualFit.rawDates.get(0).toInstant().atOffset(ZoneOffset.UTC);
        visualFit.endDate = visualFit.rawDates.get(visualFit.rawDates.size() - 1).toInstant().atOffset(ZoneOffset.UTC);
        visualFit.updateGraphs();

        JFrame frame = new JFrame("VisualFit");

        frame.setContentPane(visualFit.root);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

    }

    public void createUIComponents() {
        smoothed = new XChartPanel<>(newChart("Smoothed", "Number of Positive samples tested", null));
        raw = new XChartPanel<>(newChart("Raw", "Number of Positive samples tested", null));
        peaks = new XChartPanel<>(newChart("Fit", "Estimate", null));
        residuals = new XChartPanel<>(newChart("Residuals", "Number of Positive samples tested", null));
    }

    private XYChart newChart(String chartTitle, String yAxisTitle, Double yAxisMax) {
        var smoothed = new XYChartBuilder()
                .width(940)
                .height(480)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle(yAxisTitle)
                .title(chartTitle)
                .build();
        smoothed.getStyler().setDatePattern("dd-MMM-yyyy");
        smoothed.getStyler().setXAxisMin((double) (START_DATE.toInstant().toEpochMilli()));
        smoothed.getStyler().setXAxisMax((double) (END_DATE.toInstant().toEpochMilli()));
        if (yAxisMax != null) {
            smoothed.getStyler().setYAxisMax(yAxisMax);
            smoothed.getStyler().setYAxisMin(-500.0);
        }
        return smoothed;
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT edit this method OR call it in your
     * code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        root = new JPanel();
        root.setLayout(new GridBagLayout());
        graphs = new JPanel();
        graphs.setLayout(new GridBagLayout());
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 100.0;
        gbc.weighty = 75.0;
        gbc.fill = GridBagConstraints.BOTH;
        root.add(graphs, gbc);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 50.0;
        gbc.weighty = 50.0;
        graphs.add(smoothed, gbc);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 50.0;
        gbc.weighty = 50.0;
        graphs.add(peaks, gbc);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 50.0;
        gbc.weighty = 50.0;
        graphs.add(residuals, gbc);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 50.0;
        gbc.weighty = 50.0;
        graphs.add(raw, gbc);
        final JPanel spacer1 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.VERTICAL;
        root.add(spacer1, gbc);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 100.0;
        gbc.weighty = 25.0;
        gbc.fill = GridBagConstraints.BOTH;
        root.add(panel1, gbc);
        params = new JTextArea();
        params.setRows(10);
        params.setText("baseline=0\n# yyyy-mm-dd, height, rate\n");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 100.0;
        gbc.weighty = 100.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel1.add(params, gbc);
        final JPanel spacer2 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.VERTICAL;
        root.add(spacer2, gbc);
        final JPanel spacer3 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        root.add(spacer3, gbc);
        final JPanel spacer4 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        root.add(spacer4, gbc);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return root;
    }

}
