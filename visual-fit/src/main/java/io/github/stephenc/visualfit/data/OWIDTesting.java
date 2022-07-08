package io.github.stephenc.visualfit.data;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Stephen Connolly
 */
public class OWIDTesting {
    @JsonProperty("Entity")
    public String entity;
    @JsonProperty("ISO code")
    public String iso;
    @JsonProperty("Date")
    public String timestamp;
    @JsonProperty("Source URL")
    public String sourceUrl;
    @JsonProperty("Source label")
    public String sourceLabel;
    @JsonProperty("Notes")
    public String notes;
    @JsonProperty("Cumulative total")
    public Double total;
    @JsonProperty("Daily change in cumulative total")
    public Double daily;
    @JsonProperty("Cumulative total per thousand")
    public Double totalPerThousand;
    @JsonProperty("Daily change in cumulative total per thousand")
    public Double dailyPerThousand;
    @JsonProperty("7-day smoothed daily change")
    public Double daily7DaySmoothed;
    @JsonProperty("7-day smoothed daily change per thousand")
    public Double dailyPerThousand7DaySmoothed;
    @JsonProperty("Short-term positive rate")
    public Double shortTermPositivityRate;
    @JsonProperty("Short-term tests per case")
    public Double shortTermTestsPerCase;
}
