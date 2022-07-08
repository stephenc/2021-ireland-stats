package io.github.stephenc.visualfit;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @author Stephen Connolly
 */
public class Predictor {
    private static final double halfLifeConstant = 0.2;
    protected static final int PERIOD = 7;
    private final double[] histObs;
    private final double[] histEst;
    private final double[] scaleObs;
    private final double[] scaleEst;
    private final double[] ratio;

    private static double[] newArray() {
        var r = new double[PERIOD];
        Arrays.fill(r, Double.NaN);
        return r;
    }

    public Predictor() {
        this.histObs = newArray();
        this.histEst = newArray();
        this.scaleObs = newArray();
        this.scaleEst = newArray();
        this.ratio = newArray();
    }

    public void train(int dayOfWeek, double estimator, double observation) {
        var avgHistObs = avg(histObs);
        var stdHistObs = std(histObs);
        histObs[dayOfWeek % PERIOD] = observation;
        var scoreObs = Double.isFinite(avgHistObs) && Double.isFinite(stdHistObs)
                ? (observation - avgHistObs) / stdHistObs
                : Double.NaN;
        if (Double.isFinite(scoreObs)) {
            if (Double.isFinite(scaleObs[dayOfWeek % PERIOD])) {
                scaleObs[dayOfWeek % PERIOD] = (1.0-halfLifeConstant) * scaleObs[dayOfWeek % PERIOD] + halfLifeConstant * scoreObs;
            } else {
                scaleObs[dayOfWeek % PERIOD] = scoreObs;
            }
        }
        var avgHistEst = avg(histEst);
        var stdHistEst = std(histEst);
        histEst[dayOfWeek % PERIOD] = estimator;
        var scoreEst = Double.isFinite(avgHistEst) && Double.isFinite(stdHistEst)
                ? (estimator - avgHistEst) / stdHistEst
                : Double.NaN;
        if (Double.isFinite(scoreEst)) {
            if (Double.isFinite(scaleEst[dayOfWeek % PERIOD])) {
                scaleEst[dayOfWeek % PERIOD] = (1.0-halfLifeConstant) * (scoreEst / scaleEst[dayOfWeek % PERIOD]) + halfLifeConstant * scoreEst;
            } else {
                scaleEst[dayOfWeek % PERIOD] = scoreEst;
            }
        }
        var guess = scaleObs[dayOfWeek % PERIOD] * ratio[dayOfWeek % PERIOD] * scoreEst * stdHistObs + avgHistObs;
        if (Double.isFinite(scoreObs) && Double.isFinite(scoreEst)) {
            if (Double.isFinite(ratio[dayOfWeek % PERIOD])) {
                ratio[dayOfWeek % PERIOD] = (1.0-halfLifeConstant) * ratio[dayOfWeek % PERIOD] + halfLifeConstant * scoreObs / scoreEst;
            } else {
                ratio[dayOfWeek % PERIOD] = scoreObs / scoreEst;
            }
        }
        System.out.printf("%.1f vs %.1f => %.1f%%%n", observation, guess,
                (guess - observation) / observation * 100);
    }

    public double guess(int dayOfWeek, double estimator) {
        var avgHistObs = avg(histObs);
        var stdHistObs = std(histObs);
        var avgHistEst = avg(histEst);
        var stdHistEst = std(histEst);
        histEst[dayOfWeek % PERIOD] = estimator;
        var scoreEst = Double.isFinite(avgHistEst) && Double.isFinite(stdHistEst)
                ? (estimator - avgHistEst) / stdHistEst
                : Double.NaN;
        if (Double.isFinite(scoreEst)) {
            if (Double.isFinite(scaleEst[dayOfWeek % PERIOD])) {
                scaleEst[dayOfWeek % PERIOD] = (1.0-halfLifeConstant) * scaleEst[dayOfWeek % PERIOD] + halfLifeConstant * scoreEst;
            } else {
                scaleEst[dayOfWeek % PERIOD] = scoreEst;
            }
        }
        var guess = scaleObs[dayOfWeek % PERIOD] * ratio[dayOfWeek % PERIOD] * (scoreEst / scaleEst[dayOfWeek%PERIOD]) * stdHistObs + avgHistObs;
        return guess;
    }

    @Override
    public String toString() {
        return "Predictor{" +
                "\n\tdayOfWeek\t" + IntStream.range(0,7).mapToObj(x -> DayOfWeek.of(x < 1 ? x + 7 : x)).map(x -> x.getDisplayName(
                TextStyle.SHORT, Locale.ENGLISH)).map(d -> String.format("%-8s", d)).collect(Collectors.joining("\t"))+
                "\n\thistObs  \t" + Arrays.stream(histObs).mapToObj(d -> String.format("%-8.0f", d))
                .collect(Collectors.joining("\t")) +
                "\n\thistEst  \t" + Arrays.stream(histEst).mapToObj(d -> String.format("%-8.0f", d))
                .collect(Collectors.joining("\t")) +
                "\n\tscaleObs \t" + Arrays.stream(scaleObs).mapToObj(d -> String.format("%-8.1f", d))
                .collect(Collectors.joining("\t")) +
                "\n\tscaleEst \t" + Arrays.stream(scaleEst).mapToObj(d -> String.format("%-8.1f", d))
                .collect(Collectors.joining("\t")) +
                "\n\tratio    \t" + Arrays.stream(ratio).mapToObj(d -> String.format("%-8.2f", d))
                .collect(Collectors.joining("\t")) +
                "\n}";
    }

    static double max(double[] v) {
        var m = v[0];
        for (var x : v) {
            if (Double.isFinite(x)) {
                m = Math.max(m, x);
            }
        }
        return m;
    }

    static double min(double[] v) {
        var m = v[0];
        for (var x : v) {
            if (Double.isFinite(x)) {
                m = Math.min(m, x);
            }
        }
        return m;
    }

    static double maxExceptIndex(double[] v, int index) {
        var m = v[0];
        for (int i = 0; i < v.length; i++) {
            if (i == index) {
                continue;
            }
            double x = v[i];
            if (Double.isFinite(x)) {
                m = Math.max(m, x);
            }
        }
        return m;
    }

    static double minExceptIndex(double[] v, int index) {
        var m = v[0];
        for (int i = 0; i < v.length; i++) {
            if (i == index) {
                continue;
            }
            double x = v[i];
            if (Double.isFinite(x)) {
                m = Math.min(m, x);
            }
        }
        return m;
    }

    static double avgExceptIndex(double[] v, int index) {
        var m = 0.0;
        var count = 0;
        for (int i = 0; i < v.length; i++) {
            if (i == index) {
                continue;
            }
            double x = v[i];
            if (Double.isFinite(x)) {
                m = m + x;
                count++;
            }
        }
        return count == 0 ? Double.NaN : m / count;
    }

    static double stdExceptIndex(double[] v, int index) {
        var m = 0.0;
        var m2 = 0.0;
        var count = 0;
        for (int i = 0; i < v.length; i++) {
            if (i == index) {
                continue;
            }
            double x = v[i];
            if (Double.isFinite(x)) {
                m = m + x;
                m2 = m2 + x * x;
                count++;
            }
        }
        return count < 2 ? Double.NaN : Math.sqrt((m2 - m * m / count) / (count - 1));
    }

    static double avg(double[] v) {
        var m = 0.0;
        var count = 0;
        for (int i = 0; i < v.length; i++) {
            double x = v[i];
            if (Double.isFinite(x)) {
                m = m + x;
                count++;
            }
        }
        return count == 0 ? Double.NaN : m / count;
    }

    static double std(double[] v) {
        var m = 0.0;
        var m2 = 0.0;
        var count = 0;
        for (int i = 0; i < v.length; i++) {
            double x = v[i];
            if (Double.isFinite(x)) {
                m = m + x;
                m2 = m2 + x * x;
                count++;
            }
        }
        return count < 2 ? Double.NaN : Math.sqrt((m2 - m * m / count) / (count - 1));
    }
}
