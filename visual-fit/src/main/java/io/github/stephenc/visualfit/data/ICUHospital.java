package io.github.stephenc.visualfit.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ICUHospital {
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
