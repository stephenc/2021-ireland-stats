package io.github.stephenc.visualfit;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.apache.commons.lang3.StringUtils;
import org.knowm.xchart.AnnotationLine;
import org.knowm.xchart.AnnotationText;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.knowm.xchart.style.theme.Theme;

/**
 * @author Stephen Connolly
 */
public class VisualFit {
    protected static final OffsetDateTime START_DATE = OffsetDateTime.of(2022, 4, 15, 0, 0, 0, 0, ZoneOffset.UTC);
    protected static final OffsetDateTime END_DATE = OffsetDateTime.of(2022, 7, 15, 0, 0, 0, 0, ZoneOffset.UTC);
    private XChartPanel<MyXYChart> smoothed;
    private XChartPanel<MyXYChart> raw;
    private XChartPanel<MyXYChart> peaks;
    private XChartPanel<MyXYChart> residuals;
    private JPanel graphs;
    private JTextArea params;
    private JPanel root;

    private List<Peak> peakList = new ArrayList<>();

    private Map<LocalDate, Double> baselines = new TreeMap<>();
    private List<Date> rawDates = new ArrayList<>();
    private List<Number> rawData = new ArrayList<>();
    private OffsetDateTime startDate = START_DATE;
    private OffsetDateTime endDate = END_DATE;
    private double smoothing = 3.0;
    private Map<LocalDate, String> callouts = new TreeMap<>();

    private record Peak(LocalDate maximum, double height, double rate) {
    }

    public void show() {
        startDate = rawDates.get(0).toInstant().atOffset(ZoneOffset.UTC);
        endDate = rawDates.get(rawDates.size() - 1).toInstant().atOffset(ZoneOffset.UTC);
        updateGraphs();

        JFrame frame = new JFrame("VisualFit");

        frame.setContentPane(root);
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateGraphs();
            }
        });
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    public VisualFit(List<Date> times, List<Number> values) {
        $$$setupUI$$$();

        rawDates = times;
        rawData = values;

        Pattern peakDef = Pattern.compile(
                "^\\s*(\\d{4}-\\d{2}-\\d{2})\\s*,\\s*(\\d+(?:.\\d*)?)\\s*,\\s*(\\d+(?:.\\d*)?)\\s*(?:#.*)?$");
        Pattern smoothingDef = Pattern.compile("^\s*smoothing\\s*=\\s*(\\d+(?:.\\d*)?)\\s*(?:#.*)?$");
        Pattern baselineDef = Pattern.compile(
                "^\s*baseline\\s*=\\s*(\\d+(?:\\.\\d*)?)(?:\\s*,?\\s*(\\d{4}-\\d{2}-\\d{2})|[0-9-]+)?\\s*(?:#.*)?$");
        Pattern startDef = Pattern.compile("^\s*start\\s*=\\s*(\\d{4}-\\d{2}-\\d{2})\\s*(?:#.*)?$");
        Pattern endDef = Pattern.compile("^\s*end\\s*=\\s*(\\d{4}-\\d{2}-\\d{2})\\s*(?:#.*)?$");
        Pattern calloutDef = Pattern.compile("^\s*callout=\\s*(\\d{4}-\\d{2}-\\d{2})\\s*,?\\s*([^# ][^#]*)?(?:#.*)?$");
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
                var callouts = params.getText()
                        .lines()
                        .map(calloutDef::matcher)
                        .filter(Matcher::matches)
                        .map(callout -> new AbstractMap.SimpleImmutableEntry<LocalDate, String>(
                                LocalDate.parse(callout.group(1)),
                                StringUtils.defaultString(callout.group(2))))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                var smoothing = params.getText()
                        .lines()
                        .map(smoothingDef::matcher)
                        .filter(Matcher::matches)
                        .findFirst()
                        .map(m -> Double.valueOf(m.group(1)))
                        .orElse(3.0);
                AtomicBoolean firstBaseline = new AtomicBoolean(true);
                var baselines = params.getText()
                        .lines()
                        .map(baselineDef::matcher)
                        .filter(Matcher::matches)
                        .map(baseline ->
                                new AbstractMap.SimpleImmutableEntry<LocalDate, Double>(
                                        baseline.group(2) != null
                                                ? LocalDate.parse(baseline.group(2))
                                                : firstBaseline.compareAndSet(true, false)
                                                        ? LocalDate.MIN
                                                        : LocalDate.MAX,
                                        Double.valueOf(baseline.group(1))
                                )
                        )
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> x, TreeMap::new));
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
                        || !equivalent(VisualFit.this.baselines, baselines)
                        || !startDate.equals(VisualFit.this.startDate)
                        || !endDate.equals(VisualFit.this.endDate)
                        || !callouts.equals(VisualFit.this.callouts)
                ) {
                    VisualFit.this.peakList = peakList;
                    VisualFit.this.baselines = baselines;
                    VisualFit.this.smoothing = smoothing;
                    VisualFit.this.startDate = startDate;
                    VisualFit.this.endDate = endDate;
                    VisualFit.this.callouts.clear();
                    VisualFit.this.callouts.putAll(callouts);
                    SwingUtilities.invokeLater(VisualFit.this::updateGraphs);
                }
            }
        });
    }

    private boolean equivalent(Map<LocalDate, Double> a, Map<LocalDate, Double> b) {
        if (!a.keySet().equals(b.keySet())) {
            return false;
        }
        for (Map.Entry<LocalDate, Double> e : a.entrySet()) {
            if (Math.abs(b.get(e.getKey()) - e.getValue()) > 1.0) {
                return false;
            }
        }
        return true;
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
        Iterator<Map.Entry<LocalDate, Double>> baseline = baselines.entrySet().iterator();
        Map.Entry<LocalDate, Double> previousBaseline =
                baseline.hasNext() ? baseline.next() : new AbstractMap.SimpleImmutableEntry<>(LocalDate.MIN, 0.0);
        Map.Entry<LocalDate, Double> nextBaseline =
                baseline.hasNext()
                        ? baseline.next()
                        : new AbstractMap.SimpleImmutableEntry<>(LocalDate.MAX, previousBaseline.getValue());

        {
            var i = startDate;
            while (!i.isAfter(endDate)) {
                var date = new Date(i.toInstant().toEpochMilli());
                xAxis.add(date);
                while (baseline.hasNext() && nextBaseline.getKey().isBefore(i.toLocalDate())) {
                    previousBaseline = nextBaseline;
                    nextBaseline = baseline.next();
                }
                var x1 = previousBaseline.getKey().toEpochDay();
                var x2 = nextBaseline.getKey().toEpochDay();
                var xc = i.toLocalDate().toEpochDay();
                var yc = nextBaseline.getKey().isBefore(i.toLocalDate())
                        ? nextBaseline.getValue()
                        : (nextBaseline.getValue() - previousBaseline.getValue()) / (x2 - x1) * (xc - x1)
                                + previousBaseline.getValue();
                fit.add(yc);

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
        var avgBaseline = fit.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        var baselineValues = new ArrayList<>(fit);

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
                values.add(value >= 1.0 ? value : null);
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
        peaks.getChart().addSeries("Baseline", xAxis, baselineValues)
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

        List.of(smoothed, raw, peaks, residuals).forEach(c -> c.getChart().clearAnnotations());
        callouts.forEach((date, text) -> {
            var x = date.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime().toInstant().toEpochMilli();
            smoothed.getChart().addAnnotation(new AnnotationLine(x, true, false));
            raw.getChart().addAnnotation(new AnnotationLine(x, true, false));
            peaks.getChart().addAnnotation(new AnnotationLine(x, true, false));
            residuals.getChart().addAnnotation(new AnnotationLine(x, true, false));
            if (StringUtils.isNotBlank(text)) {
                var y1 = smoothed.getChart().getStyler().getYAxisMax();
                var y2 = smoothed.getChart().getStyler().getYAxisMin();
                if (y1 == null) {
                    y1 = avgBaseline;
                }
                if (y2 == null) {
                    y2 = 0.0;
                }
                smoothed.getChart().addAnnotation(new AnnotationText(text, x, y2 + (y1 - y2) * 0.9, false));
                raw.getChart().addAnnotation(new AnnotationText(text, x, y2 + (y1 - y2) * 0.9, false));
                peaks.getChart().addAnnotation(new AnnotationText(text, x, y2 + (y1 - y2) * 0.9, false));
                y1 = residuals.getChart().getStyler().getYAxisMax();
                y2 = residuals.getChart().getStyler().getYAxisMin();
                if (y1 == null) {
                    y1 = avgBaseline;
                }
                if (y2 == null) {
                    y2 = 0.0;
                }
                residuals.getChart().addAnnotation(new AnnotationText(text, x, y2 + (y1 - y2) * 0.9, false));
            }
        });

        List.of(smoothed, raw, peaks, residuals).forEach(this::rescaleAndPaintChartPanel);
    }

    private void rescaleAndPaintChartPanel(XChartPanel<MyXYChart> p) {
        p.getChart().setWidth(graphs.getVisibleRect().width / 2);
        p.getChart().setHeight(graphs.getVisibleRect().height / 2);
        p.getChart().getStyler().setXAxisMin((double) (startDate.toInstant().toEpochMilli()));
        p.getChart().getStyler().setXAxisMax((double) (endDate.toInstant().toEpochMilli()));
        p.setSize(new Dimension(p.getChart().getWidth(), p.getChart().getHeight()));
        p.setPreferredSize(new Dimension(p.getChart().getWidth(), p.getChart().getHeight()));
        graphs.revalidate();
        graphs.repaint();
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

    public void createUIComponents() {
        smoothed = new XChartPanel<>(newChart("Smoothed", "Number of Positive samples tested", null));
        raw = new XChartPanel<>(newChart("Raw", "Number of Positive samples tested", null));
        peaks = new XChartPanel<>(newChart("Fit", "Estimate", null));
        residuals = new XChartPanel<>(newChart("Residuals", "Number of Positive samples tested", null));
    }

    private MyXYChart newChart(String chartTitle, String yAxisTitle, Double yAxisMax) {
        var chart = new MyXYChartBuilder()
                .width(940)
                .height(480)
                .theme(Styler.ChartTheme.Matlab)
                .xAxisTitle("Date")
                .yAxisTitle(yAxisTitle)
                .title(chartTitle)
                .build();
        chart.getStyler().setDatePattern("dd-MMM-yyyy");
        chart.getStyler().setXAxisMin((double) (START_DATE.toInstant().toEpochMilli()));
        chart.getStyler().setXAxisMax((double) (END_DATE.toInstant().toEpochMilli()));
        if (yAxisMax != null) {
            chart.getStyler().setYAxisMax(yAxisMax);
            chart.getStyler().setYAxisMin(-500.0);
        }
        chart.getStyler().setCursorEnabled(true);
        return (MyXYChart) chart;
    }

    public static class MyXYChart extends XYChart {
        public MyXYChart(int width, int height) {
            super(width, height);
        }

        public MyXYChart(int width, int height, Theme theme) {
            super(width, height, theme);
        }

        public MyXYChart(int width, int height, Styler.ChartTheme chartTheme) {
            super(width, height, chartTheme);
        }

        public MyXYChart(XYChartBuilder chartBuilder) {
            super(chartBuilder);
        }

        public void clearAnnotations() {
            annotations.clear();
        }

        @Override
        protected void setWidth(int width) {
            super.setWidth(width);
        }

        @Override
        protected void setHeight(int height) {
            super.setHeight(height);
        }
    }

    public static class MyXYChartBuilder extends XYChartBuilder {
        @Override
        public MyXYChart build() {
            return new MyXYChart(this);
        }
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
