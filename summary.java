///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 11+
//JAVAC_OPTIONS -Xlint:unchecked
//DEPS com.fasterxml.jackson.core:jackson-databind:2.12.4
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.12.2
//DEPS org.apache.commons:commons-text:1.9

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

public class summary {

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

        List<Stats> stats = csvMapper.readerFor(Stats.class)
                .with(schema)
                .<Stats>readValues(
                        Paths.get("data/COVID-19_HPSC_Detailed_Statistics_Profile.csv").toFile()).readAll();

        stats.sort(Comparator.comparing(r -> r.timestamp));

        LocalDate summaryDate;
        if (args.length == 0) {
            summaryDate = LocalDate.now();
        } else {
            if (args[0].startsWith("-")) {
                summaryDate = LocalDate.now().minusDays(Integer.parseInt(args[0].substring(1)));
            } else {
                summaryDate = LocalDate.parse(args[0]);
            }
        }
        {
            LabTests current =
                    labTests.stream().filter(r -> parseTimestamp(r.timestamp).toLocalDate().equals(summaryDate))
                            .findFirst().orElse(null);
            LabTests previous =
                    labTests.stream()
                            .filter(r -> parseTimestamp(r.timestamp).toLocalDate().equals(summaryDate.minusDays(1)))
                            .findFirst().orElse(null);
            LabTests ref = labTests.stream()
                    .filter(r -> parseTimestamp(r.timestamp).toLocalDate().equals(summaryDate.minusDays(2)))
                    .findFirst().orElse(null);

            long currentPositives =
                    current == null || previous == null ? 0 : current.totalPositives - previous.totalPositives;
            long previousPositives = previous == null || ref == null ? 0 : previous.totalPositives - ref.totalPositives;
            long currentTotalTests =
                    current == null || previous == null ? 0 : current.totalLabsTotal - previous.totalLabsTotal;
            long previousTotalTests =
                    previous == null || ref == null ? 0 : previous.totalLabsTotal - ref.totalLabsTotal;
            double currentPositivity = current == null ? 0 : currentPositives * 100.0 / currentTotalTests;
            double previousPositivity = previous == null ? 0 : previousPositives * 100.0 / previousTotalTests;

            System.out.println("Ireland \uD83C\uDDEE\uD83C\uDDEA  PCR lab tests " + summaryDate);
            System.out.println();
            boolean haveToday = current != null && parseTimestamp(current.timestamp).toLocalDate().equals(summaryDate);
            boolean haveYesterday = previous != null &&
                    parseTimestamp(previous.timestamp).toLocalDate().equals(summaryDate.minusDays(1));
            boolean haveRef = ref != null &&
                    parseTimestamp(ref.timestamp).toLocalDate().equals(summaryDate.minusDays(2));
            if (haveToday && haveYesterday && haveRef) {
                System.out.printf("\u2022 Positives %s %d%n",
                        compareStr(previousPositives, currentPositives),
                        currentPositives);
                System.out.printf("\u2022 Tests %s %d%n",
                        compareStr(previousTotalTests, currentTotalTests),
                        currentTotalTests);
                System.out.printf("\u2022 Positivity %s %.1f%%%n",
                        compareStr(previousPositivity, currentPositivity),
                        currentPositivity);
            } else if (haveToday && haveYesterday) {
                System.out.printf("\u2022 No comparisons, data for %s not currently available%n",
                        summaryDate.minusDays(1));
                System.out.printf("\u2022 Positives %d%n",
                        currentPositives);
                System.out.printf("\u2022 Tests %d%n",
                        currentTotalTests);
                System.out.printf("\u2022 Positivity %.1f%%%n",
                        currentPositivity);
            } else {
                System.out.println("\u2022 Data not currently available");
            }
            System.out.println();

        }

        {
            AcuteHospital current = acuteHospitals.stream()
                    .filter(r -> parseTimestamp(r.timestamp).toLocalDate().equals(summaryDate))
                    .findFirst().orElse(null);
            AcuteHospital previous = acuteHospitals.stream()
                    .filter(r -> parseTimestamp(r.timestamp).toLocalDate().equals(summaryDate.minusDays(1)))
                    .findFirst().orElse(null);

            System.out.println("Ireland \uD83C\uDDEE\uD83C\uDDEA  Covid Hospital general " + summaryDate);
            System.out.println();
            boolean haveToday = current != null && parseTimestamp(current.timestamp).toLocalDate().equals(summaryDate);
            boolean haveYesterday = previous != null &&
                    parseTimestamp(previous.timestamp).toLocalDate().equals(summaryDate.minusDays(1));
            if (haveToday && haveYesterday) {
                System.out.printf("\u2022 Occupancy with +ve %s %d%n",
                        compareStr(previous.currentConfirmedCovidPositive, current.currentConfirmedCovidPositive),
                        current.currentConfirmedCovidPositive);
                if (previous.admissionsCovidPositive != null && current.admissionsCovidPositive != null) {
                    System.out.printf("\u2022 Admission with +ve %s %d%n",
                            compareStr(previous.admissionsCovidPositive, current.admissionsCovidPositive),
                            current.admissionsCovidPositive);
                    System.out.printf("\u2022 Post admission +ve %s %d%n",
                            compareStr(
                                    previous.newCovidCasesCovid - previous.admissionsCovidPositive,
                                    current.newCovidCasesCovid - current.admissionsCovidPositive),
                            current.newCovidCasesCovid - current.admissionsCovidPositive);
                } else if (current.admissionsCovidPositive != null) {
                    System.out.printf("\u2022 Admission with +ve %d%n",
                            current.admissionsCovidPositive);
                    System.out.printf("\u2022 Post admission +ve %d%n",
                            current.newCovidCasesCovid - current.admissionsCovidPositive);
                }
            } else if (haveToday) {
                System.out.printf("\u2022 No comparisons, data for %s not currently available%n",
                        summaryDate.minusDays(1));
                System.out.printf("\u2022 Occupancy with +ve %d%n",
                        current.currentConfirmedCovidPositive);
                System.out.printf("\u2022 Admission with +ve %d%n",
                        current.admissionsCovidPositive);
                System.out.printf("\u2022 Post admission +ve %d%n",
                        current.newCovidCasesCovid - current.admissionsCovidPositive);
            } else {
                System.out.println("\u2022 Data not currently available");
            }
            System.out.println();
        }

        {
            ICUHospital current = icuHospitals.stream()
                    .filter(r -> parseTimestamp(r.timestamp).toLocalDate().equals(summaryDate))
                    .findFirst().orElse(null);
            ICUHospital previous = icuHospitals.stream()
                    .filter(r -> parseTimestamp(r.timestamp).toLocalDate().equals(summaryDate.minusDays(1)))
                    .findFirst().orElse(null);

            System.out.println("Ireland \uD83C\uDDEE\uD83C\uDDEA  Covid Hospital ICU " + summaryDate);
            System.out.println();
            boolean haveToday = current != null && parseTimestamp(current.timestamp).toLocalDate().equals(summaryDate);
            boolean haveYesterday = previous != null && parseTimestamp(previous.timestamp).toLocalDate()
                    .equals(summaryDate.minusDays(1));
            if (haveToday && haveYesterday) {
                System.out.printf("\u2022 Occupancy with +ve %s %d%n",
                        compareStr(previous.currentConfirmedCovidPositive, current.currentConfirmedCovidPositive),
                        current.currentConfirmedCovidPositive
                );
                System.out.printf("\u2022 Admissions with +ve %s %d%n",
                        compareStr(previous.admissionsCovidPositive, current.admissionsCovidPositive),
                        current.admissionsCovidPositive
                );
            } else if (haveToday) {
                System.out.printf("\u2022 No comparisons, data for %s not currently available%n",
                        summaryDate.minusDays(1));
                System.out.printf("\u2022 Occupancy with +ve %d%n",
                        current.currentConfirmedCovidPositive
                );
                System.out.printf("\u2022 Admissions with +ve %d%n",
                        current.admissionsCovidPositive
                );
            } else {
                System.out.println("\u2022 Data not currently available");
            }
            System.out.println();
        }
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
    }

}
