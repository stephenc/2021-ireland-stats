package io.github.stephenc.visualfit.data;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Stephen Connolly
 */
public class OWIDData {
    @JsonProperty("iso_code")
    public String isoCode;
    @JsonProperty("continent")
    public String continent;
    @JsonProperty("location")
    public String location;
    @JsonProperty("date")
    public String timestamp;
    @JsonProperty("total_cases")
    public Double total;
    @JsonProperty("new_cases")
    public Double daily;
    @JsonProperty("new_cases_smoothed")
    public Double dailySmoothed;
    @JsonProperty("total_deaths")
    public Double totalDeaths;
    @JsonProperty("new_deaths")
    public Double newDeaths;
    @JsonProperty("new_deaths_smoothed")
    public Double newDeathsSmoothed;
    @JsonProperty("total_cases_per_million")
    public Double totalCasesPerMillion;
    @JsonProperty("new_cases_per_million")
    public Double newCasesPerMillion;
    @JsonProperty("new_cases_smoothed_per_million")
    public Double newCasesSmoothedPerMillion;
    @JsonProperty("total_deaths_per_million")
    public Double total_deaths_per_million;
    @JsonProperty("new_deaths_per_million")
    public Double new_deaths_per_million;
    @JsonProperty("new_deaths_smoothed_per_million")
    public Double new_deaths_smoothed_per_million;
    @JsonProperty("reproduction_rate")
    public Double reproduction_rate;
    @JsonProperty("icu_patients")
    public Double icu_patients;
    @JsonProperty("icu_patients_per_million")
    public Double icu_patients_per_million;
    @JsonProperty("hosp_patients")
    public Double hosp_patients;
    @JsonProperty("hosp_patients_per_million")
    public Double hosp_patients_per_million;
    @JsonProperty("weekly_icu_admissions")
    public Double weekly_icu_admissions;
    @JsonProperty("weekly_icu_admissions_per_million")
    public Double weekly_icu_admissions_per_million;
    @JsonProperty("weekly_hosp_admissions")
    public Double weekly_hosp_admissions;
    @JsonProperty("weekly_hosp_admissions_per_million")
    public Double weekly_hosp_admissions_per_million;
    @JsonProperty("total_tests")
    public Double total_tests;
    @JsonProperty("new_tests")
    public Double new_tests;
    @JsonProperty("total_tests_per_thousand")
    public Double total_tests_per_thousand;
    @JsonProperty("new_tests_per_thousand")
    public Double new_tests_per_thousand;
    @JsonProperty("new_tests_smoothed")
    public Double new_tests_smoothed;
    @JsonProperty("new_tests_smoothed_per_thousand")
    public Double new_tests_smoothed_per_thousand;
    @JsonProperty("positive_rate")
    public Double positive_rate;
    @JsonProperty("tests_per_case")
    public Double tests_per_case;
    @JsonProperty("tests_units")
    public String tests_units;
    @JsonProperty("total_vaccinations")
    public Double total_vaccinations;
    @JsonProperty("people_vaccinated")
    public Double people_vaccinated;
    @JsonProperty("people_fully_vaccinated")
    public Double people_fully_vaccinated;
    @JsonProperty("total_boosters")
    public Double total_boosters;
    @JsonProperty("new_vaccinations")
    public Double new_vaccinations;
    @JsonProperty("new_vaccinations_smoothed")
    public Double new_vaccinations_smoothed;
    @JsonProperty("total_vaccinations_per_hundred")
    public Double total_vaccinations_per_hundred;
    @JsonProperty("people_vaccinated_per_hundred")
    public Double people_vaccinated_per_hundred;
    @JsonProperty("people_fully_vaccinated_per_hundred")
    public Double people_fully_vaccinated_per_hundred;
    @JsonProperty("total_boosters_per_hundred")
    public Double total_boosters_per_hundred;
    @JsonProperty("new_vaccinations_smoothed_per_million")
    public Double new_vaccinations_smoothed_per_million;
    @JsonProperty("new_people_vaccinated_smoothed")
    public Double new_people_vaccinated_smoothed;
    @JsonProperty("new_people_vaccinated_smoothed_per_hundred")
    public Double new_people_vaccinated_smoothed_per_hundred;
    @JsonProperty("stringency_index")
    public Double stringency_index;
    @JsonProperty("population_density")
    public Double population_density;
    @JsonProperty("median_age")
    public Double median_age;
    @JsonProperty("aged_65_older")
    public Double aged_65_older;
    @JsonProperty("aged_70_older")
    public Double aged_70_older;
    @JsonProperty("gdp_per_capita")
    public Double gdp_per_capita;
    @JsonProperty("cardiovasc_death_rate")
    public Double cardiovasc_death_rate;
    @JsonProperty("diabetes_prevalence")
    public Double diabetes_prevalence;
    @JsonProperty("female_smokers")
    public Double female_smokers;
    @JsonProperty("male_smokers")
    public Double male_smokers;
    @JsonProperty("handwashing_facilities")
    public Double handwashing_facilities;
    @JsonProperty("hospital_beds_per_thousand")
    public Double hospital_beds_per_thousand;
    @JsonProperty("life_expectancy")
    public Double life_expectancy;
    @JsonProperty("human_development_index")
    public Double human_development_index;
    @JsonProperty("population")
    public Double population;
    @JsonProperty("excess_mortality_cumulative_absolute")
    public Double excess_mortality_cumulative_absolute;
    @JsonProperty("excess_mortality_cumulative")
    public Double excess_mortality_cumulative;
    @JsonProperty("excess_mortality")
    public Double excess_mortality;
    @JsonProperty("excess_mortality_cumulative_per_million")
    public Double excess_mortality_cumulative_per_million;
    @JsonProperty("extreme_poverty")
    public Double extreme_poverty;
}





