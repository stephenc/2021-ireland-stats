package io.github.stephenc.visualfit;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import io.github.stephenc.visualfit.data.AcuteHospital;
import io.github.stephenc.visualfit.data.Antigen;
import io.github.stephenc.visualfit.data.ICUHospital;
import io.github.stephenc.visualfit.data.LabTests;
import io.github.stephenc.visualfit.data.Stats;
import io.github.stephenc.visualfit.data.WeeklyVax;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import javax.swing.JFrame;

public class Main {

    public static void main(String... args) throws IOException {
        CsvMapper csvMapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        List<AcuteHospital> acuteHospitals = csvMapper.readerFor(AcuteHospital.class)
                .with(schema)
                .<AcuteHospital>readValues(
                        Paths.get("../data/COVID-19_SDU_Acute_Hospital_Time_Series_Summary.csv").toFile()).readAll();
        acuteHospitals.sort(Comparator.comparing(r -> r.timestamp));

        List<ICUHospital> icuHospitals = csvMapper.readerFor(ICUHospital.class)
                .with(schema)
                .<ICUHospital>readValues(
                        Paths.get("../data/COVID-19_NOCA_ICUBIS_Historic_Time_Series.csv").toFile()).readAll();

        icuHospitals.sort(Comparator.comparing(r -> r.timestamp));

        List<LabTests> labTests = csvMapper.readerFor(LabTests.class)
                .with(schema)
                .<LabTests>readValues(
                        Paths.get("../data/COVID-19_Laboratory_Testing_Time_Series.csv").toFile()).readAll();

        labTests.sort(Comparator.comparing(r -> r.timestamp));

        List<Stats> stats = csvMapper.readerFor(Stats.class)
                .with(schema)
                .<Stats>readValues(
                        Paths.get("../data/COVID-19_HPSC_Detailed_Statistics_Profile.csv").toFile()).readAll();

        stats.sort(Comparator.comparing(r -> r.timestamp));

        List<Antigen> antigens = csvMapper.readerFor(Antigen.class)
                .with(schema)
                .<Antigen>readValues(
                        Paths.get("../data/COVID-19_Antigen.csv").toFile()).readAll();

        antigens.sort(Comparator.comparing(r -> r.timestamp));

        List<WeeklyVax> weeklyVax = csvMapper.readerFor(WeeklyVax.class)
                .with(schema)
                .<WeeklyVax>readValues(
                        Paths.get("../data/COVID-19_HSE_Weekly_Vaccination_Figures.csv").toFile()).readAll();

        weeklyVax.sort(Comparator.comparing(r -> r.week));

    }
}
