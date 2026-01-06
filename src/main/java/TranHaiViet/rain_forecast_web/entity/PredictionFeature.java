package TranHaiViet.rain_forecast_web.entity;

import com.fasterxml.jackson.annotation.JsonIgnore; // 1. Import cái này
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

@Entity
@Table(name = "prediction_features")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "history_id", nullable = false, unique = true)
    @ToString.Exclude

    // 2. THÊM DÒNG NÀY QUAN TRỌNG NHẤT
    // Nó bảo Jackson: "Khi in Feature ra JSON, đừng in ngược lại History nữa"
    @JsonIgnore
    private PredictionHistory predictionHistory;

    @Column(name = "input_lst")
    private Double inputLst;

    @Column(name = "input_humidity")
    private Double inputHumidity;

    @Column(name = "input_temp")
    private Double inputTemp;

    @Column(name = "input_wind_speed")
    private Double inputWindSpeed;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        PredictionFeature that = (PredictionFeature) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}