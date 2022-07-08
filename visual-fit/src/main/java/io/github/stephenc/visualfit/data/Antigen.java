package io.github.stephenc.visualfit.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Antigen {
    @JsonProperty("DateOfData")
    public String timestamp;
    @JsonProperty("RegisteredPositiveAntigenFigure")
    public Long positives;
}
