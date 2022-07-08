package io.github.stephenc.visualfit.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AcuteHospital {
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
