package TranHaiViet.rain_forecast_web.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity
@Table(name = "prediction_history")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    @ToString.Exclude
    @JsonIgnore
    private Location location;

    @Column(name = "prediction_timestamp", nullable = false)
    private LocalDateTime predictionTimestamp;

    @Column(name = "predicted_for_date", nullable = false)
    private LocalDate predictedForDate;

    @Column(name = "predicted_rainfall")
    private Double predictedRainfall;

    @Column(name = "actual_rainfall")
    private Double actualRainfall;

    @OneToOne(mappedBy = "predictionHistory", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private PredictionFeature predictionFeature;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        PredictionHistory that = (PredictionHistory) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
    @JsonProperty("locationName")
    public String getLocationName() {
        return location != null ? location.getName() : "";
    }
}