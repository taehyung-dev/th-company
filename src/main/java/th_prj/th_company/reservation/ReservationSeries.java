package th_prj.th_company.reservation;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 반복 예약 묶음. 날짜마다 Reservation 이 하나씩 있고, 각각 따로 고치거나 취소할 수 있다.
 */
@Entity
@Table(name = "reservation_series")
public class ReservationSeries {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RepeatRule rule;

	@Column(nullable = false)
	private LocalDate untilDate;

	protected ReservationSeries() {
	}

	public ReservationSeries(RepeatRule rule, LocalDate untilDate) {
		this.rule = rule;
		this.untilDate = untilDate;
	}

	public Long getId() { return id; }
	public RepeatRule getRule() { return rule; }
	public LocalDate getUntilDate() { return untilDate; }
}
