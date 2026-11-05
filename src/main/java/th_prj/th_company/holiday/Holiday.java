package th_prj.th_company.holiday;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 공휴일(회사 지정 휴일 포함). 근태의 결근 판단, 휴가 일수 계산, 달력 표시, 반복 예약에 쓰인다.
 */
@Entity
@Table(name = "holiday")
public class Holiday {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private LocalDate holidayDate;

	@Column(nullable = false, length = 50)
	private String name;

	protected Holiday() {
	}

	public Holiday(LocalDate holidayDate, String name) {
		this.holidayDate = holidayDate;
		this.name = name;
	}

	public Long getId() { return id; }
	public LocalDate getHolidayDate() { return holidayDate; }
	public String getName() { return name; }
}
