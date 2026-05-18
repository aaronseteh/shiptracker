package com.shiptracker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VesselInfoDTO {

    @JsonProperty("year_built")
    private Integer yearBuilt;

    @JsonProperty("gross_tonnage")
    private Integer grossTonnage;

    @JsonProperty("deadweight")
    private Integer deadweight;

    @JsonProperty("draught")
    private Double draught;

    @JsonProperty("port_of_registry")
    private String portOfRegistry;

    @JsonProperty("operator")
    private String operator;

    @JsonProperty("vessel_type_cargo")
    private String vesselTypeDetailed;

    @JsonProperty("flag_country")
    private String flagCountry;

    @JsonProperty("length_overall")
    private Double lengthOverall;

    @JsonProperty("breadth_moulded")
    private Double breadthMoulded;
}
