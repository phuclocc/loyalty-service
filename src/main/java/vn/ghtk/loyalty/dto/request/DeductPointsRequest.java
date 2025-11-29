package vn.ghtk.loyalty.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeductPointsRequest {

    @NotNull(message = "Points is required")
    @Positive(message = "Points must be positive")
    private Integer points;
}

