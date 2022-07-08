package io.github.stephenc.visualfit.data;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Stephen Connolly
 */
public class Stats {
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
