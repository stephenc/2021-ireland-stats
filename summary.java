///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 11+
//JAVAC_OPTIONS -Xlint:unchecked
//DEPS com.fasterxml.jackson.core:jackson-databind:2.12.4
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.12.2
//DEPS org.apache.commons:commons-text:1.9

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class summary {

    private static final double[] T_TEST_CI90 = {
            Double.MAX_VALUE,
            12.71, 4.303, 3.182, 2.776, 2.571,
            2.447, 2.365, 2.306, 2.262, 2.228,
            2.201, 2.179, 2.160, 2.145, 2.131,
            2.120, 2.110, 2.101, 2.093, 2.086,
            2.080, 2.074, 2.069, 2.064, 2.060,
            2.056, 2.052, 2.048, 2.045, 2.042
            };

    static class Recent<T extends Number> extends ArrayList<T> {
        final int maxSize;
        T next;

        Recent(int maxSize) {
            super(maxSize+1);
            this.maxSize = maxSize;
        }

        @Override
        public boolean add(T t) {
            if (size()+1 >  maxSize) {
                removeRange(0, size()+1 - maxSize);
            }
            return super.add(t);
        }

        public void setNext(T next) {
            this.next = next;
        }

        public T getNext() {
            return next;
        }

        public Double getMean() {
            return size() < 1 ? null : stream().mapToDouble(Number::doubleValue).sum() / size();
        }

        public Double getStdDev() {
            Double mean = getMean();
            return size() < 2 ? null : Math.sqrt(stream().mapToDouble(Number::doubleValue).map(x->(x-mean)*(x-mean)).sum() / (size()- 1));
        }
    }

    public static void main(String... args) throws IOException, InterruptedException {

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

        boolean tweeting = false;
        LocalDate argDate = LocalDate.now();
        for (String arg : args) {
            switch (arg.toLowerCase(Locale.ENGLISH)) {
                case "--tweet":
                case "tweet":
                    tweeting = true;
                    break;
                default:
                    if (arg.startsWith("-")) {
                        argDate = LocalDate.now().minusDays(Integer.parseInt(arg.substring(1)));
                    } else {
                        argDate = LocalDate.parse(arg);
                    }
            }
        }
        LocalDate summaryDate = argDate;
        StringBuffer message = new StringBuffer();
        Recent<Double> pcrPositivityHistory = new Recent<>(30);
        Recent<Long> pcrPositiveCountHistory = new Recent<>(30);
        boolean canDoStats = false;
        {
            Long pcrCount = null;
            Long pcrPrevCount = null;
            Double[] antigenRatio = new Double[1];
            {
                LabTests[] previous = new LabTests[1];
                labTests.forEach(r -> {
                    OffsetDateTime timestamp = parseTimestamp(r.timestamp);
                    long pcrPositives = r.totalPositives - (previous[0] == null ? 0 : previous[0].totalPositives);
                    antigens.stream()
                            .filter(x -> parseTimestamp(x.timestamp).toInstant()
                                    .atOffset(ZoneOffset.UTC).toLocalDate()
                                    .equals(timestamp.toInstant().atOffset(ZoneOffset.UTC)
                                            .toLocalDate()))
                            .map(x -> x.positives)
                            .findAny()
                            .ifPresent(p -> {
                                if (pcrPositives > 0) {
                                    antigenRatio[0] = antigenRatio[0] == null
                                            ? p / pcrPositives
                                            : 0.9 * antigenRatio[0] + 0.1 * p / pcrPositives;
                                }
                            });
                    previous[0] = r;
                });
            }
            {
                LabTests current = null;
                LabTests previous = null;
                LabTests ref = null;
                for (LabTests r : labTests) {
                    ref = previous;
                    previous = current;
                    current = r;
                    if (previous != null && ref != null) {
                        pcrPositivityHistory.add((previous.totalPositives-ref.totalPositives)*100.0/(previous.totalLabsTotal-ref.totalLabsTotal));
                        pcrPositiveCountHistory.add(previous.totalPositives - ref.totalPositives);
                    }
                    if (parseTimestamp(r.timestamp).toLocalDate().equals(summaryDate)) {
                        break;
                    }
                }
                if (current != null &&previous != null) {
                    pcrPositiveCountHistory.setNext(current.totalPositives - previous.totalPositives);
                    pcrPositivityHistory.setNext((current.totalPositives - previous.totalPositives)*100.0/(current.totalLabsTotal-previous.totalLabsTotal));
                }

                long currentPositives =
                        current == null || previous == null ? 0 : current.totalPositives - previous.totalPositives;
                long previousPositives =
                        previous == null || ref == null ? 0 : previous.totalPositives - ref.totalPositives;
                long currentTotalTests =
                        current == null || previous == null ? 0 : current.totalLabsTotal - previous.totalLabsTotal;
                long previousTotalTests =
                        previous == null || ref == null ? 0 : previous.totalLabsTotal - ref.totalLabsTotal;
                double currentPositivity = current == null ? 0 : currentPositives * 100.0 / currentTotalTests;
                double previousPositivity = previous == null ? 0 : previousPositives * 100.0 / previousTotalTests;

                message.append("Ireland \uD83C\uDDEE\uD83C\uDDEA  PCR lab tests " + summaryDate + "\n");
                message.append('\n');
                boolean haveToday =
                        current != null && parseTimestamp(current.timestamp).toLocalDate().equals(summaryDate);
                boolean haveYesterday = previous != null &&
                        parseTimestamp(previous.timestamp).toLocalDate().equals(summaryDate.minusDays(1));
                boolean haveRef = ref != null &&
                        parseTimestamp(ref.timestamp).toLocalDate().equals(summaryDate.minusDays(2));
                if (haveToday && haveYesterday && haveRef) {
                    message.append(String.format("\u2022 Positives %s %d%n",
                            compareStr(previousPositives, currentPositives),
                            currentPositives));
                    message.append(String.format("\u2022 Tests %s %d%n",
                            compareStr(previousTotalTests, currentTotalTests),
                            currentTotalTests));
                    message.append(String.format("\u2022 Positivity %s %.1f%%%n",
                            compareStr(previousPositivity, currentPositivity),
                            currentPositivity));
                } else if (haveToday && haveYesterday) {
                    message.append(String.format("\u2022 No comparisons, data for %s not currently available%n",
                            summaryDate.minusDays(1)));
                    message.append(String.format("\u2022 Positives %d%n",
                            currentPositives));
                    message.append(String.format("\u2022 Tests %d%n",
                            currentTotalTests));
                    message.append(String.format("\u2022 Positivity %.1f%%%n",
                            currentPositivity));
                } else {
                    if (tweeting) {
                        System.err.println("No PCR data available, aborting tweets");
                        System.exit(1);
                    }
                    message.append("\u2022 Data not currently available" + "\n");
                }
                message.append('\n');
                pcrCount = current == null || previous == null ? null : currentPositives;
                pcrPrevCount = previous == null || ref == null ? null : previous.totalPositives - ref.totalPositives;
                canDoStats = haveToday;
            }

            Long antigenCount = null;
            Long antigenPrevCount = null;
            {
                Antigen current = antigens.stream()
                        .filter(r -> parseTimestamp(r.timestamp).toLocalDate().equals(summaryDate))
                        .findFirst().orElse(null);
                Antigen previous = antigens.stream()
                        .filter(r -> parseTimestamp(r.timestamp).toLocalDate().equals(summaryDate.minusDays(1)))
                        .findFirst().orElse(null);

                boolean haveToday =
                        current != null && parseTimestamp(current.timestamp).toLocalDate().equals(summaryDate);
                boolean haveYesterday = previous != null &&
                        parseTimestamp(previous.timestamp).toLocalDate().equals(summaryDate.minusDays(1));
                if (haveToday && haveYesterday) {
                    message.append(String.format("\u2022 Antigen +ve %s %d%n",
                            compareStr(previous.positives, current.positives),
                            current.positives));
                    message.append('\n');
                } else if (haveToday) {
                    message.append(String.format("\u2022 Antigen +ve %d%n",
                            current.positives));
                    message.append('\n');
                } else {
                    message.append("\u2022 Antigen data not currently available" + "\n");
                    message.append('\n');
                }
                antigenCount = haveToday ? current.positives : null;
                antigenPrevCount = haveYesterday ? previous.positives : null;
            }
            Long prevTotal = pcrPrevCount != null
                    ? (antigenPrevCount != null
                    ? pcrPrevCount + antigenPrevCount
                    : Math.round(pcrPrevCount / 2056616.0 * (2705249.0 + 2056616.0))) : null;
            Long prevTotal2 = pcrPrevCount != null
                    ? (antigenPrevCount != null
                    ? pcrPrevCount + antigenPrevCount
                    : Math.round(pcrPrevCount * (1 + antigenRatio[0]))) : null;
            if (pcrCount != null) {
                if (antigenCount == null) {
                    long projectedTotal = Math.round(pcrCount / 2056616.0 * (2705249.0 + 2056616.0));
                    if (Boolean.getBoolean("pop")) {
                        if (prevTotal != null) {
                            message.append(String.format("\u2022 PROJECTED total %s %d%n",
                                    compareStr(prevTotal, projectedTotal),
                                    projectedTotal));
                        } else {
                            message.append(String.format("\u2022 PROJECTED total %d%n", projectedTotal));
                        }
                        message.append('\n');
                        message.append(
                                "NOTE: Projection based on PCR positives extrapolated to whole population" + "\n");
                    } else {
                        long projectedTotal2 = Math.round(pcrCount * (1 + antigenRatio[0]));
                        if (prevTotal2 != null) {
                            message.append(String.format("\u2022 PROJECTED total %s %d%n",
                                    compareStr(prevTotal2, projectedTotal2),
                                    projectedTotal2));
                        } else {
                            message.append(String.format("\u2022 PROJECTED total %d%n", projectedTotal2));
                        }
                        message.append('\n');
                        message.append(String.format(
                                "NOTE: Projection based on recent historical ratio PCR:Antigen of 1:%.3f%n",
                                antigenRatio[0]));
                    }
                } else {
                    if (prevTotal != null) {
                        message.append(String.format("\u2022 Total %s %d%n", compareStr(prevTotal,
                                        pcrCount + antigenCount),
                                pcrCount + antigenCount));
                    } else {
                        message.append(String.format("\u2022 Total %d%n", pcrCount + antigenCount));
                    }
                }
            }
        }

        String lastTweet = null;
        if (tweeting) {
            lastTweet = sendTweet(
                    message,
                    lastTweet,
                    Paths.get("graphs/COVID-19_Laboratory_Testing_Percent_Positive.png"),
                    Paths.get("graphs/COVID-19_Laboratory_Testing_Percent_Positive_Last30.png"),
                    Paths.get("graphs/COVID-19_Laboratory_Testing_Time_Series.png"),
                    Paths.get("graphs/COVID-19_Laboratory_Testing_Time_Series_Last30.png")
            );
        }
        message.append('\n');
        System.out.println(message);
        message.setLength(0);

        if (canDoStats) {
            {
                message.append("Ireland \uD83C\uDDEE\uD83C\uDDEA  PCR lab tests statistics (P<0.05) " + summaryDate + "\n");
                message.append("\n");
                {
                    Long x = pcrPositiveCountHistory.getNext();
                    Double xm = pcrPositiveCountHistory.getMean();
                    Double xs = pcrPositiveCountHistory.getStdDev();
                    if (x != null && xm != null && xs != null) {
                        double t = (x - xm) / xs;
                        if (Math.abs(t) >= T_TEST_CI90[pcrPositiveCountHistory.size()]) {
                            message.append(String.format("\u2022 PCR positives at %d: statistically ", x));
                            if (t > 0) {
                                message.append("greater ");
                            } else {
                                message.append("less ");
                            }
                            message.append(String.format("than prev %d days: %.2f±%.2f ",
                                    pcrPositiveCountHistory.size(), xm, xs));
                        } else {
                            message.append(String.format(
                                    "\u2022 PCR positives %d: not statistically different ", x));
                            message.append(String.format("than prev %d days: %.1f±%.1f ",
                                    pcrPositiveCountHistory.size(), xm, xs));
                        }
                    } else {
                        message.append("\u2022 Insufficient data to analyse PCR positives");
                    }
                    message.append("\n");
                }
                {
                    Double x = pcrPositivityHistory.getNext();
                    Double xm = pcrPositivityHistory.getMean();
                    Double xs = pcrPositivityHistory.getStdDev();
                    if (x != null && xm != null && xs != null) {
                        double t = (x - xm) / xs;
                        if (Math.abs(t) >= T_TEST_CI90[pcrPositivityHistory.size()]) {
                            message.append(String.format("\u2022 PCR positivity at %.1f%%: statistically ", x));
                            if (t > 0) {
                                message.append("greater ");
                            } else {
                                message.append("less ");
                            }
                            message.append(String.format("than prev %d days: %.2f±%.2f ",
                                    pcrPositivityHistory.size(), xm, xs));
                        } else {
                            message.append(String.format(
                                    "\u2022 PCR positivity %.1f%%: not statistically different ", x));
                            message.append(String.format("than prev %d days: %.1f±%.1f ",
                                    pcrPositivityHistory.size(), xm, xs));
                        }
                    } else {
                        message.append("\u2022 Insufficient data to analyse PCR positivity");
                    }
                }
            }

            if (tweeting) {
                lastTweet = sendTweet(
                        message,
                        lastTweet
                );
            }
            message.append('\n');
            System.out.println(message);
            message.setLength(0);
        }

        canDoStats = false;
        Recent<Long> hospitalOccupancyHistory = new Recent<>(30);
        Recent<Long> hospitalAdmissionHistory = new Recent<>(30);
        Recent<Long> hospitalPostAdmissionHistory = new Recent<>(30);
        {
            AcuteHospital current = null;
            AcuteHospital previous = null;
            for (AcuteHospital h: acuteHospitals) {
                previous = current;
                current = h;
                if (parseTimestamp(h.timestamp).toLocalDate().equals(summaryDate)) {
                    hospitalOccupancyHistory.setNext(current.currentConfirmedCovidPositive);
                    hospitalAdmissionHistory.setNext(current.admissionsCovidPositive);
                    if (current.newCovidCasesCovid != null && current.admissionsCovidPositive != null)
                    hospitalPostAdmissionHistory.setNext(current.newCovidCasesCovid - current.admissionsCovidPositive);
                    break;
                } else {
                    if (previous != null) {
                        hospitalOccupancyHistory.add(previous.currentConfirmedCovidPositive);
                        hospitalAdmissionHistory.add(previous.admissionsCovidPositive);
                        if (previous.newCovidCasesCovid != null && previous.admissionsCovidPositive != null)
                            hospitalPostAdmissionHistory.add(previous.newCovidCasesCovid - previous.admissionsCovidPositive);
                    }
                }
            }
            LocalDate previousLevelDate = null;
            AcuteHospital previousLevel = null;
            boolean rising = false;
            if (current != null && current.currentConfirmedCovidPositive != null) {
                for (int i = acuteHospitals.size() - 1; i > 0; i--) {
                    AcuteHospital level = acuteHospitals.get(i);
                    LocalDate levelDate = parseTimestamp(level.timestamp).toLocalDate();
                    if (!levelDate.isBefore(summaryDate)) {
                        continue;
                    }
                    if (level.currentConfirmedCovidPositive != null) {
                        if (level.currentConfirmedCovidPositive < current.currentConfirmedCovidPositive) {
                            previousLevelDate = levelDate;
                            previousLevel = level;
                            break;
                        }
                        if (level.currentConfirmedCovidPositive.equals(current.currentConfirmedCovidPositive)) {
                            if (rising) {
                                previousLevelDate = levelDate;
                                previousLevel = level;
                                break;
                            }
                        } else {
                            rising = true;
                        }
                    }
                }
            }

            message.append("Ireland \uD83C\uDDEE\uD83C\uDDEA  Covid Hospital general " + summaryDate + "\n");
            message.append('\n');
            boolean haveToday = current != null && parseTimestamp(current.timestamp).toLocalDate().equals(summaryDate);
            boolean haveYesterday = previous != null &&
                    parseTimestamp(previous.timestamp).toLocalDate().equals(summaryDate.minusDays(1));
            if (haveToday && haveYesterday) {
                message.append(String.format("\u2022 Occupancy with +ve %s %d%n",
                        compareStr(previous.currentConfirmedCovidPositive, current.currentConfirmedCovidPositive),
                        current.currentConfirmedCovidPositive));
                if (previous.admissionsCovidPositive != null && current.admissionsCovidPositive != null) {
                    message.append(String.format("\u2022 Admission with +ve %s %d%n",
                            compareStr(previous.admissionsCovidPositive, current.admissionsCovidPositive),
                            current.admissionsCovidPositive));
                    message.append(String.format("\u2022 Post admission +ve %s %d%n",
                            compareStr(
                                    previous.newCovidCasesCovid - previous.admissionsCovidPositive,
                                    current.newCovidCasesCovid - current.admissionsCovidPositive),
                            current.newCovidCasesCovid - current.admissionsCovidPositive));
                } else if (current.admissionsCovidPositive != null) {
                    message.append(String.format("\u2022 Admission with +ve %d%n",
                            current.admissionsCovidPositive));
                    message.append(String.format("\u2022 Post admission +ve %d%n",
                            current.newCovidCasesCovid - current.admissionsCovidPositive));
                }
            } else if (haveToday) {
                message.append(String.format("\u2022 No comparisons, data for %s not currently available%n",
                        summaryDate.minusDays(1)));
                message.append(String.format("\u2022 Occupancy with +ve %d%n",
                        current.currentConfirmedCovidPositive));
                message.append(String.format("\u2022 Admission with +ve %d%n",
                        current.admissionsCovidPositive));
                message.append(String.format("\u2022 Post admission +ve %d%n",
                        current.newCovidCasesCovid - current.admissionsCovidPositive));
            } else {
                message.append("\u2022 Data not currently available" + "\n");
            }
            if (haveToday && previousLevel != null && previousLevelDate.until(summaryDate, ChronoUnit.DAYS) > 7) {
                message.append(String.format(
                        "%nNOTE: Occupancy is now the lowest it has been for %d days since %s when it was %d%n",
                        previousLevelDate.until(summaryDate, ChronoUnit.DAYS),
                        previousLevelDate,
                        previousLevel.currentConfirmedCovidPositive
                ));
            }
            canDoStats = haveToday;
        }

        if (tweeting) {
            lastTweet = sendTweet(
                    message,
                    lastTweet,
                    Paths.get("graphs/COVID-19_SDU_Acute_Hospital_Time_Series_Last30.png"),
                    Paths.get("graphs/COVID-19_SDU_Acute_Hospital_Time_Series_New_cases_breakdown.png"),
                    Paths.get("graphs/COVID-19_SDU_Acute_Hospital_Time_Series_Summary.png")
            );
        }
        message.append('\n');
        System.out.println(message);
        message.setLength(0);

        if (canDoStats) {
            {
                message.append("Ireland \uD83C\uDDEE\uD83C\uDDEA  Covid Hospital General statistics (P<0.05) " + summaryDate + "\n");
                message.append("\n");
                {
                    Long x = hospitalOccupancyHistory.getNext();
                    Double xm = hospitalOccupancyHistory.getMean();
                    Double xs = hospitalOccupancyHistory.getStdDev();
                    if (x != null && xm != null && xs != null) {
                        double t = (x - xm) / xs;
                        if (Math.abs(t) >= T_TEST_CI90[hospitalOccupancyHistory.size()]) {
                            message.append(String.format("\u2022 Occupancy %d: statistically ", x));
                            if (t > 0) {
                                message.append("greater ");
                            } else {
                                message.append("less ");
                            }
                            message.append(String.format("than prev %d days: %.2f±%.2f ",
                                    hospitalOccupancyHistory.size(), xm, xs));
                        } else {
                            message.append(String.format(
                                    "\u2022 Occupancy %d: not statistically different ", x));
                            message.append(String.format("than prev %d days: %.1f±%.1f ",
                                    hospitalOccupancyHistory.size(), xm, xs));
                        }
                    } else {
                        message.append("\u2022 Insufficient data to analyse Occupancy");
                    }
                    message.append("\n");
                }
               {
                    Long x = hospitalAdmissionHistory.getNext();
                    Double xm = hospitalAdmissionHistory.getMean();
                    Double xs = hospitalAdmissionHistory.getStdDev();
                    if (x != null && xm != null && xs != null) {
                        double t = (x - xm) / xs;
                        if (Math.abs(t) >= T_TEST_CI90[hospitalAdmissionHistory.size()]) {
                            message.append(String.format("\u2022 Admissions %d: statistically ", x));
                            if (t > 0) {
                                message.append("greater ");
                            } else {
                                message.append("less ");
                            }
                            message.append(String.format("than prev %d days: %.2f±%.2f ",
                                    hospitalAdmissionHistory.size(), xm, xs));
                        } else {
                            message.append(String.format(
                                    "\u2022 Admissions %d: not statistically different ", x));
                            message.append(String.format("than prev %d days: %.1f±%.1f ",
                                    hospitalAdmissionHistory.size(), xm, xs));
                        }
                    } else {
                        message.append("\u2022 Insufficient data to analyse Admissions");
                    }
                    message.append("\n");
                }
            }

            if (tweeting) {
                lastTweet = sendTweet(
                        message,
                        lastTweet
                );
            }
            message.append('\n');
            System.out.println(message);
            message.setLength(0);

            {
                message.append("Ireland \uD83C\uDDEE\uD83C\uDDEA  Covid Hospital General statistics (P<0.05) " + summaryDate + " (continued)\n");
                message.append("\n");
                {
                    Long x = hospitalPostAdmissionHistory.getNext();
                    Double xm = hospitalPostAdmissionHistory.getMean();
                    Double xs = hospitalPostAdmissionHistory.getStdDev();
                    if (x != null && xm != null && xs != null) {
                        double t = (x - xm) / xs;
                        if (Math.abs(t) >= T_TEST_CI90[hospitalPostAdmissionHistory.size()]) {
                            message.append(String.format("\u2022 Post admission %d: statistically ", x));
                            if (t > 0) {
                                message.append("greater ");
                            } else {
                                message.append("less ");
                            }
                            message.append(String.format("than prev %d days: %.2f±%.2f ",
                                    hospitalPostAdmissionHistory.size(), xm, xs));
                        } else {
                            message.append(String.format(
                                    "\u2022 Post admission %d: not statistically different ", x));
                            message.append(String.format("than prev %d days: %.1f±%.1f ",
                                    hospitalPostAdmissionHistory.size(), xm, xs));
                        }
                    } else {
                        message.append("\u2022 Insufficient data to analyse Post admission");
                    }
                    message.append("\n");
                }
            }

            if (tweeting) {
                lastTweet = sendTweet(
                        message,
                        lastTweet
                );
            }
            message.append('\n');
            System.out.println(message);
            message.setLength(0);
        }

        canDoStats = false;
        Recent<Long> icuOccupancyHistory = new Recent<>(30);
        Recent<Long> icuAdmissionHistory = new Recent<>(30);
        {
            ICUHospital current = null;
            ICUHospital previous = null;
            for (ICUHospital h : icuHospitals) {
                previous = current;
                current = h;
                if (parseTimestamp(h.timestamp).toLocalDate().equals(summaryDate)) {
                    icuOccupancyHistory.setNext(current.currentConfirmedCovidPositive);
                    icuAdmissionHistory.setNext(current.admissionsCovidPositive);
                    break;
                } else {
                    if (previous != null) {
                        icuOccupancyHistory.add(previous.currentConfirmedCovidPositive);
                        icuAdmissionHistory.add(previous.admissionsCovidPositive);
                    }
                }
            }
            LocalDate previousLevelDate = null;
            ICUHospital previousLevel = null;
            boolean rising = false;
            if (current != null && current.currentConfirmedCovidPositive != null) {
                for (int i = icuHospitals.size() - 1; i > 0; i--) {
                    ICUHospital level = icuHospitals.get(i);
                    LocalDate levelDate = parseTimestamp(level.timestamp).toLocalDate();
                    if (!levelDate.isBefore(summaryDate)) {
                        continue;
                    }
                    if (level.currentConfirmedCovidPositive != null) {
                        if (level.currentConfirmedCovidPositive < current.currentConfirmedCovidPositive) {
                            previousLevelDate = levelDate;
                            previousLevel = level;
                            break;
                        }
                        if (level.currentConfirmedCovidPositive.equals(current.currentConfirmedCovidPositive)) {
                            if (rising) {
                                previousLevelDate = levelDate;
                                previousLevel = level;
                                break;
                            }
                        } else {
                            rising = true;
                        }
                    }
                }
            }

            message.append("Ireland \uD83C\uDDEE\uD83C\uDDEA  Covid Hospital ICU " + summaryDate + "\n");
            message.append('\n');
            boolean haveToday = current != null && parseTimestamp(current.timestamp).toLocalDate().equals(summaryDate);
            boolean haveYesterday = previous != null && parseTimestamp(previous.timestamp).toLocalDate()
                    .equals(summaryDate.minusDays(1));
            if (haveToday && haveYesterday) {
                message.append(String.format("\u2022 Occupancy with +ve %s %d%n",
                        compareStr(previous.currentConfirmedCovidPositive, current.currentConfirmedCovidPositive),
                        current.currentConfirmedCovidPositive
                ));
                message.append(String.format("\u2022 Admissions with +ve %s %d%n",
                        compareStr(previous.admissionsCovidPositive, current.admissionsCovidPositive),
                        current.admissionsCovidPositive
                ));
            } else if (haveToday) {
                message.append(String.format("\u2022 No comparisons, data for %s not currently available%n",
                        summaryDate.minusDays(1)));
                message.append(String.format("\u2022 Occupancy with +ve %d%n",
                        current.currentConfirmedCovidPositive
                ));
                message.append(String.format("\u2022 Admissions with +ve %d%n",
                        current.admissionsCovidPositive
                ));
            } else {
                message.append("\u2022 Data not currently available" + "\n");
            }
            if (haveToday && previousLevel != null && previousLevelDate.until(summaryDate, ChronoUnit.DAYS) > 7) {
                message.append(String.format("%nNOTE: Occupancy is now the lowest it has been for %d days since %s when it was %d%n",
                        previousLevelDate.until(summaryDate, ChronoUnit.DAYS),
                        previousLevelDate,
                        previousLevel.currentConfirmedCovidPositive
                ));
            }

            canDoStats = haveToday;
            if (tweeting) {
                lastTweet = sendTweet(
                        message,
                        lastTweet,
                        Paths.get("graphs/COVID-19_NOCA_ICUBIS_Historic_Time_Series.png"),
                        Paths.get("graphs/COVID-19_NOCA_ICUBIS_Historic_Time_Series_Last30.png")
                );
            }
            message.append('\n');
            System.out.println(message);
            message.setLength(0);
            if (canDoStats) {
                {
                    message.append(
                            "Ireland \uD83C\uDDEE\uD83C\uDDEA  Covid Hospital ICU statistics (P<0.05) " + summaryDate + "\n");
                    message.append("\n");
                    {
                        Long x = icuOccupancyHistory.getNext();
                        Double xm = icuOccupancyHistory.getMean();
                        Double xs = icuOccupancyHistory.getStdDev();
                        if (x != null && xm != null && xs != null) {
                            double t = (x - xm) / xs;
                            if (Math.abs(t) >= T_TEST_CI90[icuOccupancyHistory.size()]) {
                                message.append(String.format("\u2022 Occupancy %d: statistically ", x));
                                if (t > 0) {
                                    message.append("greater ");
                                } else {
                                    message.append("less ");
                                }
                                message.append(String.format("than prev %d days: %.2f±%.2f ",
                                        icuOccupancyHistory.size(), xm, xs));
                            } else {
                                message.append(String.format(
                                        "\u2022 Occupancy %d: not statistically different ", x));
                                message.append(String.format("than prev %d days: %.1f±%.1f ",
                                        icuOccupancyHistory.size(), xm, xs));
                            }
                        } else {
                            message.append("\u2022 Insufficient data to analyse Occupancy");
                        }
                        message.append("\n");
                    }
                    {
                        Long x = icuAdmissionHistory.getNext();
                        Double xm = icuAdmissionHistory.getMean();
                        Double xs = icuAdmissionHistory.getStdDev();
                        if (x != null && xm != null && xs != null) {
                            double t = (x - xm) / xs;
                            if (Math.abs(t) >= T_TEST_CI90[icuAdmissionHistory.size()]) {
                                message.append(String.format("\u2022 Admissions %d: statistically ", x));
                                if (t > 0) {
                                    message.append("greater ");
                                } else {
                                    message.append("less ");
                                }
                                message.append(String.format("than prev %d days: %.2f±%.2f ",
                                        icuAdmissionHistory.size(), xm, xs));
                            } else {
                                message.append(String.format(
                                        "\u2022 Admissions %d: not statistically different ", x));
                                message.append(String.format("than prev %d days: %.1f±%.1f ",
                                        icuAdmissionHistory.size(), xm, xs));
                            }
                        } else {
                            message.append("\u2022 Insufficient data to analyse Admissions");
                        }
                        message.append("\n");
                    }
                }

                if (tweeting) {
                    lastTweet = sendTweet(
                            message,
                            lastTweet
                    );
                }
                message.append('\n');
                System.out.println(message);
                message.setLength(0);
            }
            message.append("Ireland \uD83C\uDDEE\uD83C\uDDEA  % PCR Positive Analysis " + summaryDate + "\n");
            if (tweeting) {
                message.append("\n(Automated tweet any analysis will follow later)");
                lastTweet = sendTweet(
                        message,
                        lastTweet,
                        Paths.get("graphs/COVID-19_Labs_Vs_Last_Year.gif")
                );
            }
            message.append('\n');
            System.out.println(message);
            message.setLength(0);

            message.append("Ireland \uD83C\uDDEE\uD83C\uDDEA  Total # of positives Analysis " + summaryDate + "\n");
            if (tweeting) {
                message.append("\n(Automated tweet any analysis will follow later)");
                lastTweet = sendTweet(
                        message,
                        lastTweet,
                        Paths.get("graphs/COVID-19_Positives_Vs_Last_Year.gif")
                );
            }
            message.append('\n');
            System.out.println(message);
            message.setLength(0);

            message.append("Ireland \uD83C\uDDEE\uD83C\uDDEA  All data graph " + summaryDate + "\n");
            if (tweeting) {
                message.append("\n(Automated tweet any analysis will follow later)");
                lastTweet = sendTweet(
                        message,
                        lastTweet,
                        Paths.get("graphs/COVID-19_Labs_Hospitalized_ICU.gif")
                );
            }
            message.append('\n');
            System.out.println(message);
            message.setLength(0);

            message.append("Ireland \uD83C\uDDEE\uD83C\uDDEA  All data time-shift graph " + summaryDate + "\n");
            if (tweeting) {
                message.append("\n(Automated tweet any analysis will follow later)");
                lastTweet = sendTweet(
                        message,
                        lastTweet,
                        Paths.get("graphs/COVID-19_Labs_Hospitalized_ICU_Timeshifted.gif")
                );
            }
            message.append('\n');
            System.out.println(message);
            message.setLength(0);

            message.append("Ireland \uD83C\uDDEE\uD83C\uDDEA  Last 90 days all data time-shift graph " + summaryDate + "\n");
            if (tweeting) {
                message.append("\n(Automated tweet any analysis will follow later)");
                lastTweet = sendTweet(
                        message,
                        lastTweet,
                        Paths.get("graphs/COVID-19_Labs_Hospitalized_ICU_Timeshifted_Last90.gif")
                );
            }
            message.append('\n');
            System.out.println(message);
            message.setLength(0);

            message.append("All data sourced from https://covid-19.geohive.ie/search" + "\n");
            message.append(
                    "I am maintaining an archive of the geohive data tracked every hour on https://github"
                            + ".com/stephenc/2021-ireland-stats/tree/main/data"
                            + "\n");
            message.append('\n');
            message.append(
                    "My full set of graphs is available from https://github"
                            + ".com/stephenc/2021-ireland-stats/tree/main/graphs (updated daily at 16:15 UTC and "
                            + "manually if I see the data updated early)"
                            + "\n");
            if (tweeting) {
                lastTweet = sendTweet(
                        message,
                        lastTweet
                );
            }
            message.append('\n');
            System.out.println(message);

            message.setLength(0);
            String previousThread = null;
            try {
                 previousThread = previousThread(summaryDate);
            } catch (Exception e) {
                // ignore in case twty cli command is not installed
            }
            if (previousThread != null) {
                String dayOfWeek = summaryDate.minusDays(1).getDayOfWeek().toString();
                message.append(dayOfWeek.charAt(0));
                message.append(dayOfWeek.substring(1).toLowerCase(Locale.ENGLISH));
                message.append("'s thread: https://twitter.com/connolly_s/status/");
                message.append(previousThread);
                System.out.println(message);
                if (tweeting) {
                    sendTweet(
                            message,
                            lastTweet
                    );
                }
            }
        }
    }

    private static String previousThread(LocalDate summaryDate) throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        args.add("twty");
        args.add("-v");
        args.add("-s");
        args.add(String.format("from:connolly_s -is:reply Ireland \"PCR lab tests %s\"", summaryDate.minusDays(1)));
        Process process = new ProcessBuilder(args)
                .redirectErrorStream(true)
                .start();
        String threadId = null;
        int state = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                switch (state) {
                    case 0:
                        if (line.startsWith("connolly_s:")) {
                            state = 1;
                        }
                        break;
                    case 1:
                        int i = line.indexOf("PCR lab tests");
                        if (line.startsWith("  Ireland") && i > 0 && i < 20) {
                            state = 2;
                        } else {
                            state = 0;
                        }
                        break;
                    case 2:
                        if (line.matches("^\\s{2}\\d+")) {
                            threadId = line.substring(2);
                        }
                        state = 0;
                        break;
                    default:
                        state = 0;
                        break;
                }
            }
        }
        int retVal = process.waitFor();
        if (retVal != 0) {
            throw new RuntimeException("forked process exited with " + retVal);
        }
        return threadId;
    }

    private static String sendTweet(StringBuffer message, String inReplyTo, Path... medias)
            throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        args.add("twty");
        if (inReplyTo != null) {
            args.add("-i");
            args.add(inReplyTo);
        }
        for (Path media : medias) {
            args.add("-m");
            args.add(media.toString());
        }
        args.add(message.toString());
        Process process = new ProcessBuilder(args)
                .redirectErrorStream(true)
                .start();
        Pattern tweeted = Pattern.compile("^tweeted:\\s+(\\d+)\\s*$");
        String tweetId = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.err.println(line);
                Matcher m = tweeted.matcher(line);
                if (m.matches()) {
                    tweetId = m.group(1);
                }
            }
        }
        int retVal = process.waitFor();
        if (retVal != 0) {
            throw new RuntimeException("forked process exited with " + retVal);
        }
        return tweetId;
    }

    private static OffsetDateTime parseTimestamp(String v1) {
        return OffsetDateTime.parse(
                v1.replace('/', '-')
                        .replace(' ', 'T')
                        .replace("+00", "Z")
        );

    }


    static String compareStr(long v1, long v2) {
        if (v1 == v2) {
            return "\u2190 at";
        }
        if (v1 < v2) {
            return "\u2191" + (v2 - v1) + " to";
        }
        return "\u2193" + (v1 - v2) + " to";
    }

    static String compareStr(double v1, double v2) {
        if (Math.abs(v1 - v2) < 0.05) {
            return "\u2190 at";
        }
        if (v1 < v2) {
            return "\u2191" + String.format("%.1f", v2 - v1) + " to";
        }
        return "\u2193" + String.format("%.1f", v1 - v2) + " to";
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

}
