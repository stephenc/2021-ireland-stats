package io.github.stephenc.visualfit.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LabTests {
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
